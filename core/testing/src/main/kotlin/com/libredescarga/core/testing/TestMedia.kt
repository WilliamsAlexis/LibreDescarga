package com.libredescarga.core.testing

import com.libredescarga.core.model.AudioFormat
import com.libredescarga.core.model.AudioOption
import com.libredescarga.core.model.MediaEntry
import com.libredescarga.core.model.MediaInfo
import com.libredescarga.core.model.PlaylistInfo
import com.libredescarga.core.model.SubtitleTrack
import com.libredescarga.core.model.VideoContainer
import com.libredescarga.core.model.VideoOption

/** Datos de ejemplo para pruebas. */
object TestMedia {

    val entry = MediaEntry(
        id = "abc123",
        url = "https://example.com/watch/abc123",
        title = "Concierto libre: grabación pública",
        uploader = "Archivo Abierto",
        durationSeconds = 245,
        thumbnailUrl = "https://example.com/thumb.jpg",
    )

    val video1080 = VideoOption(
        formatId = "137",
        height = 1080,
        fps = 30,
        isHdr = false,
        container = VideoContainer.MP4,
        hasAudio = false,
        estimatedBytes = 80_000_000,
    )

    val video720 = VideoOption(
        formatId = "22",
        height = 720,
        fps = 30,
        isHdr = false,
        container = VideoContainer.MP4,
        hasAudio = true,
        estimatedBytes = 40_000_000,
    )

    val info = MediaInfo(
        id = entry.id,
        url = entry.url,
        title = entry.title,
        uploader = entry.uploader,
        durationSeconds = entry.durationSeconds,
        viewCount = 1_234,
        thumbnailUrl = entry.thumbnailUrl,
        extractor = "generic",
        videoOptions = listOf(video1080, video720),
        audioOptions = listOf(
            AudioOption(AudioFormat.MP3_320, 9_800_000),
            AudioOption(AudioFormat.MP3_192, 5_900_000),
            AudioOption(AudioFormat.MP3_128, 3_900_000),
            AudioOption(AudioFormat.M4A, 4_000_000),
            AudioOption(AudioFormat.OPUS, 3_500_000),
        ),
        subtitles = listOf(SubtitleTrack("es", "Español", isAutomatic = false)),
    )

    val playlist = PlaylistInfo(
        id = "PL1",
        url = "https://example.com/playlist/PL1",
        title = "Lista de prueba",
        uploader = "Archivo Abierto",
        entries = (1..3).map { index ->
            entry.copy(
                id = "v$index",
                url = "https://example.com/watch/v$index",
                title = "Pista $index",
            )
        },
    )
}
