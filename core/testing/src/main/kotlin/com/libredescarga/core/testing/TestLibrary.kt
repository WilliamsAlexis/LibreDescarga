package com.libredescarga.core.testing

import com.libredescarga.core.model.LibraryMedia
import com.libredescarga.core.model.MediaKind

/** Biblioteca de ejemplo para pruebas. */
object TestLibrary {

    private fun video(id: Int, title: String, folder: String, size: Long, duration: Long, date: Long) = LibraryMedia(
        uri = "content://media/external/video/media/$id",
        kind = MediaKind.VIDEO,
        title = title,
        displayName = "$title.mp4",
        folder = folder,
        sizeBytes = size,
        durationMs = duration,
        dateAdded = date,
        mimeType = "video/mp4",
        width = 1280,
        height = 720,
    )

    val videos = listOf(
        video(1, "Árbol", "Movies/LibreDescarga", size = 200, duration = 10_000, date = 2),
        video(2, "Bosque", "Movies/LibreDescarga", size = 900, duration = 50_000, date = 1),
        video(3, "Zeta", "DCIM/Camera", size = 100, duration = 90_000, date = 3),
    )

    val songs = listOf(
        LibraryMedia(
            uri = "content://media/external/audio/media/10",
            kind = MediaKind.AUDIO,
            title = "Canción libre",
            displayName = "Canción libre.mp3",
            folder = "Music/LibreDescarga",
            sizeBytes = 5_000,
            durationMs = 180_000,
            dateAdded = 5,
            mimeType = "audio/mpeg",
            artist = "Orquesta Abierta",
            album = "Dominio público",
        ),
        LibraryMedia(
            uri = "content://media/external/audio/media/11",
            kind = MediaKind.AUDIO,
            title = "Nocturno",
            displayName = "Nocturno.m4a",
            folder = "Music/LibreDescarga",
            sizeBytes = 4_000,
            durationMs = 200_000,
            dateAdded = 4,
            mimeType = "audio/mp4",
            artist = "Piano Solo",
            album = null,
        ),
    )
}
