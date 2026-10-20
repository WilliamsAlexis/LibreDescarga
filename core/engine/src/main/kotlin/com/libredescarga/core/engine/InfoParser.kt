package com.libredescarga.core.engine

import com.libredescarga.core.model.AnalysisResult
import com.libredescarga.core.model.AudioFormat
import com.libredescarga.core.model.AudioOption
import com.libredescarga.core.model.MediaEntry
import com.libredescarga.core.model.MediaError
import com.libredescarga.core.model.MediaInfo
import com.libredescarga.core.model.PlaylistInfo
import com.libredescarga.core.model.SubtitleTrack
import com.libredescarga.core.model.VideoContainer
import com.libredescarga.core.model.VideoOption
import kotlinx.serialization.json.JsonObject

/** Convierte el JSON de `yt-dlp --dump-single-json --flat-playlist` en modelos de la app. */
object InfoParser {

    private const val NONE = "none"
    private val LIVE_STATUSES = setOf("is_live", "is_upcoming", "post_live")

    fun parse(json: JsonObject, requestedUrl: String): AnalysisResult =
        if (json.string("_type") == "playlist") {
            AnalysisResult.Playlist(parsePlaylist(json, requestedUrl))
        } else {
            AnalysisResult.Single(parseInfo(json, requestedUrl))
        }

    /** Resultados de una búsqueda "ytsearchN:consulta" (una lista de reproducción plana). */
    fun parseEntries(json: JsonObject): List<MediaEntry> = json.objects("entries").mapNotNull(::parseEntry)

    private fun parsePlaylist(json: JsonObject, requestedUrl: String): PlaylistInfo {
        val entries = parseEntries(json)
        if (entries.isEmpty()) throw MediaError.Unavailable()
        return PlaylistInfo(
            id = json.string("id"),
            url = json.string("webpage_url") ?: requestedUrl,
            title = json.string("title") ?: json.string("id") ?: requestedUrl,
            uploader = json.uploaderName(),
            entries = entries,
        )
    }

    internal fun parseEntry(json: JsonObject): MediaEntry? {
        val url = json.string("webpage_url")
            ?: json.string("url")?.takeIf { it.startsWith("http") }
            ?: return null
        return MediaEntry(
            id = json.string("id") ?: url,
            url = url,
            title = json.string("title") ?: url,
            uploader = json.uploaderName(),
            durationSeconds = json.double("duration")?.toLong(),
            thumbnailUrl = json.thumbnailUrl(),
        )
    }

    private fun parseInfo(json: JsonObject, requestedUrl: String): MediaInfo {
        if (json.bool("is_live") == true || json.string("live_status") in LIVE_STATUSES) {
            throw MediaError.LiveStream()
        }

        // Algunos extractores no listan formatos: el propio objeto es el único formato.
        val allFormats = json.objects("formats").ifEmpty {
            if (json.string("url") != null) listOf(json) else emptyList()
        }
        val formats = allFormats.filterNot { it.bool("has_drm") == true || it.string("ext") == "mhtml" }
        if (allFormats.isNotEmpty() && formats.isEmpty()) throw MediaError.DrmProtected()
        if (formats.isEmpty()) throw MediaError.Unsupported()

        val duration = json.double("duration")?.toLong()
        val audioOnly = formats.filter { it.isAudioOnly() }
        val bestAudio = audioOnly.maxByOrNull { it.double("abr") ?: it.double("tbr") ?: 0.0 }

        return MediaInfo(
            id = json.string("id") ?: requestedUrl,
            url = json.string("webpage_url") ?: json.string("original_url") ?: requestedUrl,
            title = json.string("title") ?: json.string("id") ?: requestedUrl,
            uploader = json.uploaderName(),
            durationSeconds = duration,
            viewCount = json.long("view_count"),
            thumbnailUrl = json.thumbnailUrl(),
            extractor = json.string("extractor_key") ?: json.string("extractor") ?: "generic",
            videoOptions = buildVideoOptions(formats, bestAudio, duration),
            audioOptions = buildAudioOptions(formats, audioOnly, bestAudio, duration),
            subtitles = buildSubtitles(json),
        )
    }

    private fun buildVideoOptions(
        formats: List<JsonObject>,
        bestAudio: JsonObject?,
        duration: Long?,
    ): List<VideoOption> {
        val bestAudioBytes = bestAudio?.estimatedBytes(duration)
        val candidates = formats.filter { it.hasVideo() }

        val withHeight = candidates.filter { (it.long("height") ?: 0) > 0 }
        val options = withHeight.mapNotNull { format ->
            val formatId = format.string("format_id") ?: return@mapNotNull null
            val hasAudio = format.hasAudio()
            val ownBytes = format.estimatedBytes(duration)
            VideoOption(
                formatId = formatId,
                height = format.long("height")!!.toInt(),
                fps = format.double("fps")?.let { kotlin.math.round(it).toInt() },
                isHdr = format.string("dynamic_range")?.let { it != "SDR" } ?: false,
                container = if (format.string("ext") == "webm") VideoContainer.WEBM else VideoContainer.MP4,
                hasAudio = hasAudio,
                estimatedBytes = when {
                    ownBytes == null -> null
                    hasAudio || bestAudioBytes == null -> ownBytes
                    else -> ownBytes + bestAudioBytes
                },
            ).let { it to format.score() }
        }
            // Una opción por altura + fps (30/60) + HDR + contenedor: la de mayor calidad.
            .groupBy { (option, _) ->
                listOf(option.height, if ((option.fps ?: 30) >= 48) 60 else 30, option.isHdr, option.container)
            }
            .map { (_, group) -> group.maxBy { it.second }.first }
            .sortedWith(
                compareByDescending<VideoOption> { it.height }
                    .thenByDescending { it.fps ?: 0 }
                    .thenBy { it.isHdr }
                    .thenBy { it.container.ordinal },
            )

        if (options.isNotEmpty()) return options

        // Sin información de resolución (enlaces directos, etc.): "Calidad original".
        val best = candidates.maxByOrNull { it.score() } ?: return emptyList()
        val formatId = best.string("format_id") ?: return emptyList()
        return listOf(
            VideoOption(
                formatId = formatId,
                height = 0,
                fps = null,
                isHdr = false,
                container = if (best.string("ext") == "webm") VideoContainer.WEBM else VideoContainer.MP4,
                hasAudio = best.hasAudio(),
                estimatedBytes = best.estimatedBytes(duration),
            ),
        )
    }

    private fun buildAudioOptions(
        formats: List<JsonObject>,
        audioOnly: List<JsonObject>,
        bestAudio: JsonObject?,
        duration: Long?,
    ): List<AudioOption> {
        if (formats.none { it.hasAudio() }) return emptyList()
        val bestAudioBytes = bestAudio?.estimatedBytes(duration)

        fun mp3(format: AudioFormat) = AudioOption(
            format = format,
            estimatedBytes = duration?.let { it * format.bitrateKbps!! * 125 },
        )

        val m4aBytes = audioOnly
            .filter { it.string("ext") == "m4a" || it.string("acodec")?.startsWith("mp4a") == true }
            .maxByOrNull { it.score() }
            ?.estimatedBytes(duration) ?: bestAudioBytes
        val opusBytes = audioOnly
            .filter { it.string("acodec")?.contains("opus") == true }
            .maxByOrNull { it.score() }
            ?.estimatedBytes(duration) ?: bestAudioBytes

        return listOf(
            mp3(AudioFormat.MP3_320),
            mp3(AudioFormat.MP3_192),
            mp3(AudioFormat.MP3_128),
            AudioOption(AudioFormat.M4A, m4aBytes),
            AudioOption(AudioFormat.OPUS, opusBytes),
        )
    }

    private fun buildSubtitles(json: JsonObject): List<SubtitleTrack> {
        val manual = json.obj("subtitles").orEmpty()
            .filterKeys { it != "live_chat" }
            .map { (code, tracks) ->
                SubtitleTrack(
                    languageCode = code,
                    name = (tracks as? kotlinx.serialization.json.JsonArray)
                        ?.firstOrNull()?.let { (it as? JsonObject)?.string("name") },
                    isAutomatic = false,
                )
            }

        // Los subtítulos automáticos traen decenas de traducciones: solo el idioma original.
        val language = json.string("language")
        val manualCodes = manual.map { it.languageCode }.toSet()
        val automatic = json.obj("automatic_captions").orEmpty().keys
            .filter { code -> code.endsWith("-orig") || (language != null && code == language) }
            .filter { it.removeSuffix("-orig") !in manualCodes }
            .map { SubtitleTrack(languageCode = it, name = null, isAutomatic = true) }

        return (manual.sortedBy { it.languageCode } + automatic)
    }

    // --- utilidades de formato -------------------------------------------------------

    private fun JsonObject.hasVideo(): Boolean = string("vcodec") != NONE

    private fun JsonObject.hasAudio(): Boolean = string("acodec") != NONE

    private fun JsonObject.isAudioOnly(): Boolean =
        string("vcodec") == NONE && string("acodec").let { it != null && it != NONE }

    private fun JsonObject.estimatedBytes(duration: Long?): Long? =
        long("filesize")
            ?: long("filesize_approx")
            ?: double("tbr")?.let { tbr -> duration?.let { (tbr * 125 * it).toLong() } }

    /** Mayor puntuación = mejor calidad (tamaño conocido y mayor tasa de bits). */
    private fun JsonObject.score(): Double =
        (double("tbr") ?: double("vbr") ?: double("abr") ?: 0.0) +
            (if (long("filesize") != null) 0.001 else 0.0)

    private fun JsonObject.uploaderName(): String? =
        string("uploader") ?: string("channel") ?: string("artist") ?: string("creator")

    private fun JsonObject.thumbnailUrl(): String? =
        string("thumbnail") ?: objects("thumbnails").lastOrNull { it.string("url") != null }?.string("url")
}
