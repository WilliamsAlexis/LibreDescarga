package com.libredescarga.core.domain.usecase

import com.libredescarga.core.model.AudioFormat
import com.libredescarga.core.model.LibrarySort
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.testing.FakeDownloadRepository
import com.libredescarga.core.testing.TestLibrary
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryUseCasesTest {

    private val items = TestLibrary.videos + TestLibrary.songs

    @Test
    fun `ordena por fecha, nombre, tamaño y duración`() {
        assertEquals(
            listOf("Zeta", "Árbol", "Bosque"),
            LibraryQuery.filterAndSort(TestLibrary.videos, "", LibrarySort.DATE).map { it.title },
        )
        assertEquals(
            listOf("Árbol", "Bosque", "Zeta"),
            LibraryQuery.filterAndSort(TestLibrary.videos, "", LibrarySort.NAME).map { it.title },
        )
        assertEquals(
            listOf("Bosque", "Árbol", "Zeta"),
            LibraryQuery.filterAndSort(TestLibrary.videos, "", LibrarySort.SIZE).map { it.title },
        )
        assertEquals(
            listOf("Zeta", "Bosque", "Árbol"),
            LibraryQuery.filterAndSort(TestLibrary.videos, "", LibrarySort.DURATION).map { it.title },
        )
    }

    @Test
    fun `busca en título, artista y álbum sin distinguir mayúsculas`() {
        assertEquals(listOf("Canción libre"), LibraryQuery.filterAndSort(items, "LIBRE", LibrarySort.NAME).map { it.title })
        assertEquals(listOf("Canción libre"), LibraryQuery.filterAndSort(items, "orquesta", LibrarySort.NAME).map { it.title })
    }

    @Test
    fun `agrupa por carpetas`() {
        val folders = LibraryQuery.folders(items)
        assertEquals(listOf("Camera", "LibreDescarga", "LibreDescarga"), folders.map { it.name })
        assertEquals("DCIM/Camera", folders.first().path)
        assertEquals(2, folders.first { it.path == "Movies/LibreDescarga" }.items.size)
    }

    @Test
    fun `reanuda solo si tiene sentido`() {
        assertEquals(0L, ResumePositionPolicy.startPosition(null, 60_000))
        assertEquals(0L, ResumePositionPolicy.startPosition(3_000, 60_000))
        assertEquals(30_000L, ResumePositionPolicy.startPosition(30_000, 60_000))
        assertEquals(0L, ResumePositionPolicy.startPosition(55_000, 60_000))
        assertTrue(ResumePositionPolicy.shouldForget(59_000, 60_000))
        assertFalse(ResumePositionPolicy.shouldForget(10_000, 60_000))
    }

    @Test
    fun `convertir a MP3 crea una tarea de audio con el video como origen`() = runTest {
        val downloads = FakeDownloadRepository()
        val video = TestLibrary.videos.first()

        assertTrue(ConvertToAudioUseCase(downloads)(video))

        val request = downloads.enqueued.single()
        assertEquals(video.uri, request.url)
        assertEquals(MediaKind.AUDIO, request.kind)
        assertEquals(AudioFormat.MP3_192, request.audioFormat)
    }

    @Test
    fun `no se convierte un audio`() = runTest {
        val downloads = FakeDownloadRepository()
        assertFalse(ConvertToAudioUseCase(downloads)(TestLibrary.songs.first()))
        assertTrue(downloads.enqueued.isEmpty())
    }
}
