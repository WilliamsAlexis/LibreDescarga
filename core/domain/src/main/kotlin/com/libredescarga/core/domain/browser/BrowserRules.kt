package com.libredescarga.core.domain.browser

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.model.WebSearch
import com.libredescarga.core.security.DomainMatcher
import com.libredescarga.core.security.UrlValidator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Qué hacer cuando el navegador intenta cargar una dirección. */
sealed interface NavigationDecision {
    /** Cargar [url] (puede ser la versión https de una dirección http). */
    data class Allow(val url: String, val upgraded: Boolean) : NavigationDecision

    /** Esquema prohibido: file://, content://, javascript:, intent:, etc. */
    data object BlockScheme : NavigationDecision

    /** Dominio de la lista negra (retirado por derechos de autor). */
    data class BlockDomain(val domain: String) : NavigationDecision
}

object NavigationPolicy {

    /** Solo http y https. Las direcciones http se cargan siempre como https. */
    fun decide(url: String, blockedDomains: Set<String>): NavigationDecision {
        val valid = when (val result = UrlValidator.validate(url)) {
            is UrlValidator.Result.Valid -> result
            else -> return NavigationDecision.BlockScheme
        }
        DomainMatcher.matches(valid.host, blockedDomains)?.let { return NavigationDecision.BlockDomain(it) }
        return NavigationDecision.Allow(url = valid.url, upgraded = !url.trim().startsWith("https://", ignoreCase = true))
    }
}

/** Lo que se escribe en la barra de direcciones: un enlace o una búsqueda. */
object Omnibox {

    /** Devuelve la dirección a cargar, o null si el texto está vacío o es un esquema prohibido. */
    fun resolve(input: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return null
        return when (val result = UrlValidator.validate(text)) {
            is UrlValidator.Result.Valid -> result.url
            UrlValidator.Result.ForbiddenScheme -> null
            UrlValidator.Result.NotAUrl -> WebSearch.url(text)
        }
    }
}

/** Un archivo descargable que se detectó en la página: su dirección y si es video, audio o imagen. */
data class DetectedMedia(val url: String, val kind: MediaKind)

/** Detecta videos, audios e imágenes que carga una página, para ofrecer descargarlos. */
object MediaSniffer {

    private val VIDEO_EXTENSIONS = setOf("m3u8", "mpd", "mp4", "m4v", "webm", "mkv", "mov")
    private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "ogg", "opus", "flac", "wav")
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    private const val MAX_MEDIA_PER_PAGE = 10
    private const val MAX_IMAGES_PER_PAGE = 40
    private const val MAX_DOM_RESULTS = 80
    private const val MAX_IMAGES = 100
    private val json = Json { ignoreUnknownKeys = true }

    /** Qué tipo de archivo es la dirección según su extensión, o null si no parece un medio descargable. */
    fun kindOfUrl(url: String): MediaKind? {
        if (!url.startsWith("https://", ignoreCase = true) && !url.startsWith("http://", ignoreCase = true)) return null
        val path = url.substringBefore('#').substringBefore('?').lowercase()
        return when (path.substringAfterLast('.', missingDelimiterValue = "")) {
            in VIDEO_EXTENSIONS -> MediaKind.VIDEO
            in AUDIO_EXTENSIONS -> MediaKind.AUDIO
            in IMAGE_EXTENSIONS -> MediaKind.IMAGE
            else -> null
        }
    }

    /** Los fragmentos sueltos (.ts, .m4s) no cuentan: basta con la lista de reproducción. Solo video y audio. */
    fun isMediaUrl(url: String): Boolean = kindOfUrl(url).let { it == MediaKind.VIDEO || it == MediaKind.AUDIO }

    /**
     * Añade [url] a [current] sin repetir y sin pasar del máximo por página (10 videos o audios, 40 imágenes).
     * Si no se indica [kind], se deduce por la extensión; sin extensión conocida no se añade.
     */
    fun add(current: List<DetectedMedia>, url: String, kind: MediaKind? = null): List<DetectedMedia> {
        val valid = (UrlValidator.validate(url) as? UrlValidator.Result.Valid)?.url ?: return current
        val resolved = kind ?: kindOfUrl(valid) ?: return current
        if (current.any { it.url == valid }) return current
        val isImage = resolved == MediaKind.IMAGE
        val limit = if (isImage) MAX_IMAGES_PER_PAGE else MAX_MEDIA_PER_PAGE
        if (current.count { (it.kind == MediaKind.IMAGE) == isImage } >= limit) return current
        return current + DetectedMedia(valid, resolved)
    }

    /**
     * Lee el resultado de [DOM_SCRIPT]: una lista de {t: tipo, u: dirección}. Android la devuelve como un texto
     * JSON dentro de otro (el resultado va entre comillas). También acepta una lista simple de direcciones.
     */
    fun parseDomResult(raw: String?): List<DetectedMedia> {
        if (raw.isNullOrBlank() || raw == "null") return emptyList()
        return try {
            val inner = (json.parseToJsonElement(raw) as? JsonPrimitive)?.contentOrNull ?: return emptyList()
            (json.parseToJsonElement(inner) as? JsonArray).orEmpty()
                .mapNotNull { element -> detectedFrom(element) }
                .distinctBy { it.url }
                .take(MAX_DOM_RESULTS)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun detectedFrom(element: JsonElement): DetectedMedia? {
        val url: String
        val declared: MediaKind?
        when (element) {
            is JsonObject -> {
                url = (element["u"] as? JsonPrimitive)?.contentOrNull ?: return null
                declared = when ((element["t"] as? JsonPrimitive)?.contentOrNull) {
                    "v" -> MediaKind.VIDEO
                    "a" -> MediaKind.AUDIO
                    "i" -> MediaKind.IMAGE
                    else -> null
                }
            }
            is JsonPrimitive -> {
                url = element.contentOrNull ?: return null
                declared = null
            }
            else -> return null
        }
        val valid = (UrlValidator.validate(url) as? UrlValidator.Result.Valid)?.url ?: return null
        val kind = declared ?: kindOfUrl(valid) ?: return null
        return DetectedMedia(valid, kind)
    }

    /**
     * Lee el resultado de [IMAGES_SCRIPT]: direcciones de imágenes sin repetir, solo http(s), hasta [MAX_IMAGES].
     * Los iconos diminutos ya vienen filtrados por el propio script.
     */
    fun parseImagesResult(raw: String?): List<String> {
        if (raw.isNullOrBlank() || raw == "null") return emptyList()
        return try {
            val inner = (json.parseToJsonElement(raw) as? JsonPrimitive)?.contentOrNull ?: return emptyList()
            (json.parseToJsonElement(inner) as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                .mapNotNull { (UrlValidator.validate(it) as? UrlValidator.Result.Valid)?.url }
                .distinct()
                .take(MAX_IMAGES)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Lista las imágenes de la página que ya se cargaron (omite iconos de menos de 120 px).
     * Incluye las que aún no cargan, porque no se sabe su tamaño. Sin addJavascriptInterface: solo devuelve un resultado.
     */
    const val IMAGES_SCRIPT =
        "(function(){try{var s=[];document.querySelectorAll('img').forEach(function(e){" +
            "var u=e.currentSrc||e.src||e.getAttribute('data-src')||e.getAttribute('data-original');" +
            "var w=e.naturalWidth||0,h=e.naturalHeight||0;" +
            "if(u&&((w===0&&h===0)||(w>=120&&h>=120))){s.push(u);}});return JSON.stringify(s);}catch(e){return '[]';}})()"

    /**
     * Se ejecuta en la página ya cargada y lista SOLO el video o audio que se está reproduciendo
     * (o el reproductor grande y visible que ya empezó a reproducirse), no los demás de la página
     * (sugerencias, vistas previas). Cada elemento es {t: "v" | "a", u: dirección}.
     * Sin addJavascriptInterface: el script solo devuelve un resultado.
     */
    const val DOM_SCRIPT =
        "(function(){try{var s=[],seen={};" +
            "function add(t,u){if(u&&!seen[u]){seen[u]=1;s.push({t:t,u:u});}}" +
            "document.querySelectorAll('video,audio').forEach(function(e){" +
            "var playing=!e.paused&&!e.ended&&e.readyState>=2;" +
            "var r=e.getBoundingClientRect();" +
            "var visible=r.width>=200&&r.height>=100&&r.bottom>0&&r.top<window.innerHeight;" +
            "if(!(playing||(e.currentTime>0&&visible))){return;}" +
            "var t=e.tagName==='AUDIO'?'a':'v';add(t,e.currentSrc||e.src);" +
            "e.querySelectorAll('source').forEach(function(x){add(t,x.src);});});" +
            "return JSON.stringify(s);}catch(e){return '[]';}})()"
}

/** Evita analizar páginas de inicio o de resultados donde no hay un video concreto. */
object PageProbePolicy {
    fun shouldProbe(url: String): Boolean {
        val valid = UrlValidator.validate(url) as? UrlValidator.Result.Valid ?: return false
        val rest = valid.url.removePrefix("https://").substringAfter('/', missingDelimiterValue = "")
        val path = rest.substringBefore('?').substringBefore('#').trim('/')
        val query = rest.substringAfter('?', missingDelimiterValue = "")
        // La portada ("/") y las búsquedas no son un video; "watch?v=..." sí.
        if (path.isEmpty() && query.isEmpty()) return false
        if (path.startsWith("search") || path.startsWith("results") || path.endsWith("/search")) return false
        return true
    }
}
