package com.libredescarga.core.domain.usecase

import com.libredescarga.core.domain.repository.DownloadRepository
import com.libredescarga.core.model.AudioFormat
import com.libredescarga.core.model.DownloadRequest
import com.libredescarga.core.model.LibraryMedia
import com.libredescarga.core.model.LibrarySort
import com.libredescarga.core.model.MediaFolder
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.security.FileNameSanitizer
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

/** Búsqueda, orden y agrupación por carpetas de la biblioteca (lógica pura, sin Android). */
object LibraryQuery {

    fun filterAndSort(items: List<LibraryMedia>, query: String, sort: LibrarySort): List<LibraryMedia> {
        val text = query.trim()
        val filtered = if (text.isEmpty()) {
            items
        } else {
            items.filter { media ->
                listOfNotNull(media.title, media.displayName, media.artist, media.album)
                    .any { it.contains(text, ignoreCase = true) }
            }
        }
        val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
        return when (sort) {
            LibrarySort.DATE -> filtered.sortedByDescending { it.dateAdded }
            LibrarySort.NAME -> filtered.sortedWith { a, b -> collator.compare(a.title, b.title) }
            LibrarySort.SIZE -> filtered.sortedByDescending { it.sizeBytes }
            LibrarySort.DURATION -> filtered.sortedByDescending { it.durationMs }
        }
    }

    /** Carpetas con su contenido, ordenadas por nombre. */
    fun folders(items: List<LibraryMedia>): List<MediaFolder> =
        items.groupBy { it.folder.trimEnd('/') }
            .map { (path, media) ->
                MediaFolder(
                    path = path,
                    name = path.substringAfterLast('/').ifEmpty { path },
                    items = media.sortedByDescending { it.dateAdded },
                )
            }
            .sortedBy { it.name.lowercase() }
}

/** Reanudar desde donde se dejó, salvo que fuera al principio o casi al final. */
object ResumePositionPolicy {
    private const val MIN_RESUME_MS = 5_000L
    private const val END_MARGIN_MS = 10_000L

    fun startPosition(savedMs: Long?, durationMs: Long): Long {
        val saved = savedMs ?: return 0
        if (saved < MIN_RESUME_MS) return 0
        if (durationMs > 0 && saved > durationMs - END_MARGIN_MS) return 0
        return saved
    }

    /** Si al guardar ya se llegó al final, se olvida la posición. */
    fun shouldForget(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && positionMs > durationMs - END_MARGIN_MS
}

/**
 * Convierte un video ya guardado en MP3 (con FFmpeg). La conversión se hace en
 * la cola de descargas, así se ve su progreso y se puede cancelar.
 */
class ConvertToAudioUseCase @Inject constructor(
    private val downloads: DownloadRepository,
) {
    suspend operator fun invoke(media: LibraryMedia, format: AudioFormat = AudioFormat.MP3_192): Boolean {
        if (media.kind != MediaKind.VIDEO || !media.uri.startsWith("content://")) return false
        val request = DownloadRequest(
            url = media.uri,
            title = media.title,
            uploader = media.artist,
            thumbnailUrl = null,
            durationSeconds = media.durationMs / 1000,
            kind = MediaKind.AUDIO,
            video = null,
            audioFormat = format,
            subtitle = null,
            trim = null,
            fileName = FileNameSanitizer.sanitize(media.title),
            estimatedBytes = media.durationMs / 1000 * (format.bitrateKbps ?: 192) * 125,
        )
        return downloads.enqueue(listOf(request)).isNotEmpty()
    }
}
