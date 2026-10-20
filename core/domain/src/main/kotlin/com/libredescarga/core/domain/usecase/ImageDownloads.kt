package com.libredescarga.core.domain.usecase

import com.libredescarga.core.model.DownloadRequest
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.security.FileNameSanitizer
import com.libredescarga.core.security.UrlValidator

/** Reglas para las descargas de imágenes (fotos, dibujos, GIF...) desde cualquier página. */
object ImageDownloads {

    /** Extensiones que se reconocen como imagen en una dirección. */
    val EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif", "heic", "heif")

    /** Indica si la dirección apunta directamente a un archivo de imagen (por su extensión). */
    fun isImageUrl(url: String): Boolean {
        val valid = UrlValidator.validate(url) as? UrlValidator.Result.Valid ?: return false
        val path = valid.url.substringBefore('#').substringBefore('?').lowercase()
        return path.substringAfterLast('.', missingDelimiterValue = "") in EXTENSIONS
    }

    /**
     * Crea la solicitud para descargar una imagen. Devuelve null si la dirección no es válida.
     * [pageTitle] se usa como nombre si la dirección no tiene un nombre de archivo útil.
     */
    fun request(url: String, pageTitle: String? = null): DownloadRequest? {
        val valid = UrlValidator.validate(url) as? UrlValidator.Result.Valid ?: return null
        val fromUrl = valid.url.substringBefore('#').substringBefore('?')
            .substringAfterLast('/')
            .substringBeforeLast('.', missingDelimiterValue = "")
            .replace('%', ' ')
            .trim()
        val title = fromUrl.ifBlank { pageTitle?.trim().orEmpty() }.ifBlank { DEFAULT_NAME }
        return DownloadRequest(
            url = valid.url,
            title = title,
            uploader = valid.host,
            // La propia imagen sirve de miniatura en la lista de descargas.
            thumbnailUrl = valid.url,
            durationSeconds = null,
            kind = MediaKind.IMAGE,
            video = null,
            audioFormat = null,
            subtitle = null,
            trim = null,
            fileName = FileNameSanitizer.sanitize(title, fallback = DEFAULT_NAME),
        )
    }

    // Pinterest guarda cada foto en varios tamaños (/236x/, /564x/, /736x/...) y siempre existe /originals/.
    private val PIN_SIZE = Regex("^(https://i\\.pinimg\\.com/)(\\d+x\\d*|originals)/")

    /**
     * Direcciones a intentar para bajar una imagen, de la mejor a la menos buena.
     * En Pinterest primero se prueba la versión original (más grande) y luego la que se recibió.
     */
    fun candidates(url: String): List<String> {
        val match = PIN_SIZE.find(url) ?: return listOf(url)
        val original = url.replaceFirst(PIN_SIZE, match.groupValues[1] + "originals/")
        return if (original == url) listOf(url) else listOf(original, url)
    }

    private val META_TAG = Regex("<meta\\s[^>]*>", RegexOption.IGNORE_CASE)
    private val META_NAME = Regex("(?:property|name)\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    private val META_CONTENT = Regex("content\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
    private val IMAGE_META_NAMES = listOf("og:image:secure_url", "og:image", "twitter:image", "twitter:image:src")

    /**
     * Busca la imagen principal que declara una página web (la que usan las redes sociales como vista previa).
     * Sirve para páginas que no son un video, como un pin de Pinterest. Devuelve null si no hay.
     */
    fun mainImageFromHtml(html: String, pageUrl: String): String? {
        val found = mutableMapOf<String, String>()
        for (tag in META_TAG.findAll(html)) {
            val name = META_NAME.find(tag.value)?.groupValues?.get(1)?.lowercase() ?: continue
            val content = META_CONTENT.find(tag.value)?.groupValues?.get(1)?.trim().orEmpty()
            if (name in IMAGE_META_NAMES && content.isNotEmpty()) found.putIfAbsent(name, content)
        }
        val raw = IMAGE_META_NAMES.firstNotNullOfOrNull { found[it] } ?: return null
        val unescaped = raw.replace("&amp;", "&")
        val host = UrlValidator.hostOf(pageUrl) ?: return null
        val absolute = when {
            unescaped.startsWith("//") -> "https:$unescaped"
            unescaped.startsWith("/") -> "https://$host$unescaped"
            else -> unescaped
        }
        return (UrlValidator.validate(absolute) as? UrlValidator.Result.Valid)?.url
    }

    private const val DEFAULT_NAME = "imagen"
}
