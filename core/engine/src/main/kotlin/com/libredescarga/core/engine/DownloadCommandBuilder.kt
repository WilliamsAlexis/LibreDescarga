package com.libredescarga.core.engine

import com.libredescarga.core.model.DownloadRequest
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.model.VideoContainer
import java.util.Locale

/** Ajustes del gestor que afectan a cómo se descarga. */
data class DownloadTuning(
    /** Fragmentos descargados en paralelo (descarga multihilo). */
    val concurrentFragments: Int = 4,
    /** Límite de velocidad en KB/s, o null sin límite. */
    val rateLimitKbps: Int? = null,
)

/** Traduce una [DownloadRequest] a argumentos de yt-dlp. Sin efectos secundarios: fácil de probar. */
object DownloadCommandBuilder {

    fun build(
        request: DownloadRequest,
        outputDir: String,
        tuning: DownloadTuning = DownloadTuning(),
    ): List<String> = buildList {
        add("--no-playlist")
        // Conversión de un archivo propio (copiado a la carpeta privada de la app).
        if (request.url.startsWith("file://")) add("--enable-file-urls")
        add("--no-mtime")
        add("--newline")
        add("--concurrent-fragments")
        add(tuning.concurrentFragments.coerceIn(1, 16).toString())
        tuning.rateLimitKbps?.takeIf { it > 0 }?.let {
            add("--limit-rate")
            add("${it}K")
        }

        when (request.kind) {
            MediaKind.VIDEO -> addVideoOptions(request)
            MediaKind.AUDIO -> addAudioOptions(request)
            // Las imágenes no pasan por yt-dlp: las baja ImageFetcher.
            MediaKind.IMAGE -> error("Las imágenes no se descargan con yt-dlp")
        }

        request.trim?.let { trim ->
            add("--download-sections")
            add("*${formatTime(trim.startSeconds)}-${trim.endSeconds?.let(::formatTime) ?: "inf"}")
            add("--force-keyframes-at-cuts")
        }

        add("--embed-metadata")
        add("-o")
        add("${outputDir.trimEnd('/')}/${escapeTemplate(request.fileName)}.%(ext)s")
    }

    private fun MutableList<String>.addVideoOptions(request: DownloadRequest) {
        val selection = requireNotNull(request.video) { "Falta la selección de video" }
        val container = selection.container.extension
        val audioExt = if (selection.container == VideoContainer.WEBM) "webm" else "m4a"
        val height = selection.maxHeight
        val formatId = selection.formatId

        val format = when {
            formatId != null && selection.formatHasAudio -> formatId
            formatId != null -> "$formatId+ba[ext=$audioExt]/$formatId+ba/$formatId"
            height != null ->
                "bv*[height<=$height][ext=$container]+ba[ext=$audioExt]/" +
                    "bv*[height<=$height]+ba/b[height<=$height]/b"
            else -> "bv*[ext=$container]+ba[ext=$audioExt]/bv*+ba/b"
        }
        add("-f")
        add(format)
        add("--merge-output-format")
        add(container)

        request.subtitle?.let { subtitle ->
            add(if (subtitle.isAutomatic) "--write-auto-subs" else "--write-subs")
            add("--sub-langs")
            add(subtitle.languageCode)
            if (subtitle.embed) {
                add("--embed-subs")
            } else {
                add("--convert-subs")
                add("srt")
            }
        }
    }

    private fun MutableList<String>.addAudioOptions(request: DownloadRequest) {
        val format = requireNotNull(request.audioFormat) { "Falta el formato de audio" }
        val extension = requireNotNull(format.extension) { "Formato de audio no válido: $format" }
        // Se prefiere el audio m4a: YouTube a veces rechaza (error 403) el audio webm/opus.
        add("-f")
        add("ba[ext=m4a]/ba/b")
        add("--extract-audio")
        add("--audio-format")
        add(extension)
        add("--audio-quality")
        add(format.bitrateKbps?.let { "${it}K" } ?: "0")
        // Carátula incrustada (MP3 y M4A) para los reproductores de música.
        if (extension == "mp3" || extension == "m4a") {
            add("--embed-thumbnail")
            add("--convert-thumbnails")
            add("jpg")
        }
    }

    /** yt-dlp interpreta "%" en la plantilla de salida: hay que duplicarlo. */
    fun escapeTemplate(name: String): String = name.replace("%", "%%")

    fun formatTime(totalSeconds: Long): String {
        val seconds = totalSeconds.coerceAtLeast(0)
        return String.format(
            Locale.ROOT,
            "%02d:%02d:%02d",
            seconds / 3600,
            (seconds % 3600) / 60,
            seconds % 60,
        )
    }
}
