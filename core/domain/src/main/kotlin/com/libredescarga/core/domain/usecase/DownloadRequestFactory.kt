package com.libredescarga.core.domain.usecase

import com.libredescarga.core.model.AudioFormat
import com.libredescarga.core.model.DownloadRequest
import com.libredescarga.core.model.MediaEntry
import com.libredescarga.core.model.MediaInfo
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.model.VideoContainer
import com.libredescarga.core.model.VideoQuality
import com.libredescarga.core.model.VideoSelection
import com.libredescarga.core.security.FileNameSanitizer

/** Crea solicitudes de descarga "por calidad" (descarga rápida y descargas por lotes). */
object DownloadRequestFactory {

    fun video(entry: MediaEntry, quality: VideoQuality): DownloadRequest = base(entry).copy(
        kind = MediaKind.VIDEO,
        video = VideoSelection(
            formatId = null,
            maxHeight = quality.maxHeight,
            container = VideoContainer.MP4,
            formatHasAudio = false,
        ),
    )

    fun audio(entry: MediaEntry, format: AudioFormat): DownloadRequest {
        require(format != AudioFormat.ASK) { "Se necesita un formato de audio concreto" }
        return base(entry).copy(kind = MediaKind.AUDIO, audioFormat = format)
    }

    fun MediaInfo.toEntry(): MediaEntry = MediaEntry(
        id = id,
        url = url,
        title = title,
        uploader = uploader,
        durationSeconds = durationSeconds,
        thumbnailUrl = thumbnailUrl,
    )

    private fun base(entry: MediaEntry) = DownloadRequest(
        url = entry.url,
        title = entry.title,
        uploader = entry.uploader,
        thumbnailUrl = entry.thumbnailUrl,
        durationSeconds = entry.durationSeconds,
        kind = MediaKind.VIDEO,
        video = null,
        audioFormat = null,
        subtitle = null,
        fileName = FileNameSanitizer.sanitize(entry.title),
    )
}
