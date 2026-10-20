package com.libredescarga.core.domain.usecase

import com.libredescarga.core.model.AudioFormat
import com.libredescarga.core.model.MediaError
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.model.SearchProvider
import com.libredescarga.core.model.UserSettings
import com.libredescarga.core.model.VideoQuality
import com.libredescarga.core.testing.FakeBlocklistRepository
import com.libredescarga.core.testing.FakeDownloadRepository
import com.libredescarga.core.testing.FakeMediaExtractorRepository
import com.libredescarga.core.testing.FakeSearchHistoryRepository
import com.libredescarga.core.testing.FakeUserPreferencesRepository
import com.libredescarga.core.testing.TestMedia
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MediaUseCasesTest {

    private val extractor = FakeMediaExtractorRepository()
    private val blocklist = FakeBlocklistRepository(setOf("bloqueado.com"))
    private val analyze = AnalyzeUrlUseCase(blocklist, extractor)

    @Test
    fun `resolver entrada distingue enlaces, palabras y esquemas prohibidos`() {
        val resolve = ResolveSearchInputUseCase()
        assertEquals(SearchInput.Empty, resolve("  "))
        assertEquals(SearchInput.Link("https://example.com/v"), resolve("http://example.com/v"))
        assertEquals(SearchInput.Keywords("música chill"), resolve(" música chill "))
        assertEquals(SearchInput.Forbidden, resolve("javascript:alert(1)"))
    }

    @Test
    fun `analizar usa la url normalizada a https`() = runTest {
        analyze("http://example.com/v/1")
        assertEquals("https://example.com/v/1", extractor.lastAnalyzedUrl)
    }

    @Test
    fun `analizar rechaza dominios de la lista negra sin llamar al motor`() = runTest {
        try {
            analyze("https://www.bloqueado.com/video")
            fail("Debía lanzar BlockedSite")
        } catch (error: MediaError.BlockedSite) {
            assertEquals("bloqueado.com", error.domain)
        }
        assertNull(extractor.lastAnalyzedUrl)
    }

    @Test
    fun `analizar rechaza esquemas peligrosos`() = runTest {
        try {
            analyze("file:///sdcard/a.mp4")
            fail("Debía lanzar ForbiddenScheme")
        } catch (_: MediaError.ForbiddenScheme) {
        }
    }

    @Test
    fun `buscar guarda la consulta en el historial`() = runTest {
        val history = FakeSearchHistoryRepository()
        val search = SearchMediaUseCase(extractor, history)

        val results = search("  gatos  ", SearchProvider.SOUNDCLOUD)

        assertEquals(listOf("gatos"), history.suggestions("", 10).first())
        assertEquals("gatos", extractor.lastSearch?.first)
        assertEquals(SearchProvider.SOUNDCLOUD, extractor.lastSearch?.second)
        assertEquals(extractor.searchResults, results)
    }

    @Test
    fun `descarga rápida usa la calidad de video por defecto`() {
        val request = CreateQuickDownloadRequestUseCase.create(
            TestMedia.entry,
            UserSettings(defaultVideoQuality = VideoQuality.P720),
        )!!
        assertEquals(MediaKind.VIDEO, request.kind)
        assertEquals(720, request.video?.maxHeight)
    }

    @Test
    fun `descarga rápida usa el audio si el video está en preguntar`() {
        val request = CreateQuickDownloadRequestUseCase.create(
            TestMedia.entry,
            UserSettings(defaultAudioFormat = AudioFormat.MP3_320),
        )!!
        assertEquals(MediaKind.AUDIO, request.kind)
        assertEquals(AudioFormat.MP3_320, request.audioFormat)
    }

    @Test
    fun `sin calidad por defecto no hay descarga rápida`() = runTest {
        val useCase = CreateQuickDownloadRequestUseCase(FakeUserPreferencesRepository())
        assertNull(useCase(TestMedia.entry))
    }

    @Test
    fun `encolar sanea los nombres de archivo`() = runTest {
        val downloads = FakeDownloadRepository()
        val enqueue = EnqueueDownloadsUseCase(downloads)
        val request = DownloadRequestFactory.video(TestMedia.entry, VideoQuality.P1080)
            .copy(fileName = "../../etc/passwd")

        val count = enqueue(listOf(request))

        assertEquals(1, count)
        val saved = downloads.enqueued.single().fileName
        assertTrue(saved, '/' !in saved && !saved.startsWith("."))
    }
}
