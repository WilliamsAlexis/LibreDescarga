package com.libredescarga.feature.analysis

import app.cash.turbine.test
import com.libredescarga.core.domain.usecase.AnalyzeUrlUseCase
import com.libredescarga.core.domain.usecase.EnqueueDownloadsUseCase
import com.libredescarga.core.model.AnalysisResult
import com.libredescarga.core.model.AnalysisUpdate
import com.libredescarga.core.model.AudioFormat
import com.libredescarga.core.model.LinkPreview
import com.libredescarga.core.model.MediaError
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.model.UserSettings
import com.libredescarga.core.model.VideoQuality
import com.libredescarga.core.testing.FakeBlocklistRepository
import com.libredescarga.core.testing.FakeDownloadRepository
import com.libredescarga.core.testing.FakeMediaExtractorRepository
import com.libredescarga.core.testing.FakePageImageResolver
import com.libredescarga.core.testing.FakeStorageRepository
import com.libredescarga.core.testing.FakeUserPreferencesRepository
import com.libredescarga.core.testing.MainDispatcherRule
import com.libredescarga.core.testing.TestMedia
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AnalysisViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val extractor = FakeMediaExtractorRepository()
    private val downloads = FakeDownloadRepository()
    private val preferences = FakeUserPreferencesRepository()
    private val storage = FakeStorageRepository()
    private val pageImages = FakePageImageResolver()

    private fun createViewModel(url: String = TestMedia.entry.url) = AnalysisViewModel(
        url = url,
        analyzeUrl = AnalyzeUrlUseCase(FakeBlocklistRepository(setOf("bloqueado.com")), extractor),
        enqueueDownloads = EnqueueDownloadsUseCase(downloads),
        preferences = preferences,
        storage = storage,
        pageImages = pageImages,
    )

    @Test
    fun `un enlace sin video pero con imagen principal descarga la imagen`() = runTest {
        extractor.analysisError = MediaError.Unknown("No video formats found!")
        pageImages.image = "https://i.pinimg.com/736x/ab/cd/foto.jpg"

        val viewModel = createViewModel("https://www.pinterest.com/pin/123/")

        assertEquals(1, downloads.enqueued.size)
        val request = downloads.enqueued.single()
        assertEquals(MediaKind.IMAGE, request.kind)
        assertEquals("https://i.pinimg.com/736x/ab/cd/foto.jpg", request.url)
        assertEquals("https://www.pinterest.com/pin/123/", pageImages.lastPage)
        viewModel.events.test { assertEquals(AnalysisEvent.Queued(1), awaitItem()) }
    }

    @Test
    fun `sin imagen principal se muestra el error original`() {
        val error = MediaError.Unknown("No video formats found!")
        extractor.analysisError = error
        pageImages.image = null

        val viewModel = createViewModel("https://example.com/pagina")

        assertEquals(AnalysisUiState.Error(error), viewModel.uiState.value)
        assertTrue(downloads.enqueued.isEmpty())
    }

    @Test
    fun `un error de red no intenta buscar imagenes`() {
        extractor.analysisError = MediaError.Network()
        pageImages.image = "https://cdn.example.com/a.jpg"

        val viewModel = createViewModel()

        assertTrue((viewModel.uiState.value as AnalysisUiState.Error).error is MediaError.Network)
        assertNull(pageImages.lastPage)
        assertTrue(downloads.enqueued.isEmpty())
    }

    @Test
    fun `avisa si la descarga no cabe y guarda el tamaño estimado`() = runTest {
        storage.available = 50_000_000 // 50 MB libres; el 1080p estimado ocupa 80 MB
        val viewModel = createViewModel()
        assertTrue(viewModel.single().lowStorage)

        viewModel.onVideoSelected(TestMedia.video720) // 40 MB → 80 MB con la copia: tampoco cabe
        assertTrue(viewModel.single().lowStorage)

        storage.available = 10_000_000_000
        viewModel.retry()
        assertFalse(viewModel.single().lowStorage)

        viewModel.events.test {
            viewModel.onDownload()
            awaitItem()
        }
        assertEquals(TestMedia.video1080.estimatedBytes, downloads.enqueued.single().estimatedBytes)
    }

    private fun AnalysisViewModel.single() = uiState.value as AnalysisUiState.Single
    private fun AnalysisViewModel.playlist() = uiState.value as AnalysisUiState.Playlist

    @Test
    fun `muestra carga mientras analiza`() {
        extractor.analysisGate = CompletableDeferred()
        val viewModel = createViewModel()
        assertEquals(AnalysisUiState.Loading, viewModel.uiState.value)

        extractor.analysisGate!!.complete(Unit)
        assertTrue(viewModel.uiState.value is AnalysisUiState.Single)
    }

    @Test
    fun `por defecto elige 1080p MP4 y MP3 320`() {
        val state = createViewModel().single()
        assertEquals(MediaKind.VIDEO, state.tab)
        assertEquals(1080, state.selectedVideo?.height)
        assertEquals(AudioFormat.MP3_320, state.selectedAudio)
        assertNull(state.quickDefault)
    }

    @Test
    fun `respeta la calidad por defecto de ajustes y ofrece descarga rápida`() {
        preferences.emit(UserSettings(defaultVideoQuality = VideoQuality.P720))
        val state = createViewModel().single()
        assertEquals(720, state.selectedVideo?.height)
        assertEquals(QuickDefault.Video(VideoQuality.P720), state.quickDefault)
    }

    @Test
    fun `descargar video encola la calidad elegida`() = runTest {
        val viewModel = createViewModel()
        viewModel.onVideoSelected(TestMedia.video720)

        viewModel.events.test {
            viewModel.onDownload()
            assertEquals(AnalysisEvent.Queued(1), awaitItem())
        }

        val request = downloads.enqueued.single()
        assertEquals(MediaKind.VIDEO, request.kind)
        assertEquals("22", request.video?.formatId)
        assertTrue(request.video!!.formatHasAudio)
    }

    @Test
    fun `descargar audio usa el titulo saneado como nombre`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTabSelected(MediaKind.AUDIO)
        viewModel.onAudioSelected(AudioFormat.M4A)

        viewModel.events.test {
            viewModel.onDownload()
            awaitItem()
        }

        val request = downloads.enqueued.single()
        assertEquals(MediaKind.AUDIO, request.kind)
        assertEquals(AudioFormat.M4A, request.audioFormat)
        // El nombre propuesto es el título saneado (":" no está permitido en tarjetas SD).
        assertEquals("Concierto libre_ grabación pública", request.fileName)
        assertNull(request.subtitle)
    }

    @Test
    fun `los subtítulos elegidos viajan con la descarga`() = runTest {
        val viewModel = createViewModel()
        viewModel.onSubtitleSelected(TestMedia.info.subtitles.first())
        viewModel.onEmbedSubtitleChange(true)

        viewModel.events.test {
            viewModel.onDownload()
            awaitItem()
        }

        val subtitle = downloads.enqueued.single().subtitle!!
        assertEquals("es", subtitle.languageCode)
        assertTrue(subtitle.embed)
    }

    @Test
    fun `una lista permite elegir elementos y descargarlos por lotes`() = runTest {
        extractor.analysisResult = AnalysisResult.Playlist(TestMedia.playlist)
        val viewModel = createViewModel()
        assertTrue(viewModel.playlist().allSelected)

        viewModel.onEntryToggled("v2")
        viewModel.onPlaylistKindSelected(MediaKind.AUDIO)
        viewModel.onPlaylistAudioFormat(AudioFormat.OPUS)

        viewModel.events.test {
            viewModel.onDownloadPlaylist()
            assertEquals(AnalysisEvent.Queued(2), awaitItem())
        }

        assertEquals(listOf("Pista 1", "Pista 3"), downloads.enqueued.map { it.title })
        assertTrue(downloads.enqueued.all { it.audioFormat == AudioFormat.OPUS })
    }

    @Test
    fun `seleccionar ninguno desactiva la descarga`() {
        extractor.analysisResult = AnalysisResult.Playlist(TestMedia.playlist)
        val viewModel = createViewModel()
        viewModel.onSelectAll(false)
        assertFalse(viewModel.playlist().canDownload)
        viewModel.onSelectAll(true)
        assertTrue(viewModel.playlist().allSelected)
    }

    @Test
    fun `los errores se muestran y se puede reintentar`() {
        extractor.analysisError = MediaError.Network()
        val viewModel = createViewModel()
        assertTrue((viewModel.uiState.value as AnalysisUiState.Error).error is MediaError.Network)

        extractor.analysisError = null
        viewModel.retry()
        assertTrue(viewModel.uiState.value is AnalysisUiState.Single)
    }

    @Test
    fun `contenido con DRM muestra el error correspondiente`() {
        extractor.analysisError = MediaError.DrmProtected()
        val viewModel = createViewModel()
        assertTrue((viewModel.uiState.value as AnalysisUiState.Error).error is MediaError.DrmProtected)
    }

    @Test
    fun `los sitios bloqueados no se analizan`() {
        val viewModel = createViewModel("https://bloqueado.com/v/1")
        assertTrue((viewModel.uiState.value as AnalysisUiState.Error).error is MediaError.BlockedSite)
        assertNull(extractor.lastAnalyzedUrl)
    }

    @Test
    fun `una pagina de imagen sin video se descarga sin llamar al motor`() = runTest {
        pageImages.imageOnly = "https://i.pinimg.com/originals/aa/bb/foto.jpg"

        val viewModel = createViewModel("https://www.pinterest.com/pin/456/")

        val request = downloads.enqueued.single()
        assertEquals(MediaKind.IMAGE, request.kind)
        assertNull(extractor.lastAnalyzedUrl)
        viewModel.events.test { assertEquals(AnalysisEvent.Queued(1), awaitItem()) }
    }

    @Test
    fun `una pagina de Pinterest con video se analiza con el motor`() {
        pageImages.imageOnly = null // la página declara un video

        val viewModel = createViewModel("https://www.pinterest.com/pin/789/")

        assertEquals("https://www.pinterest.com/pin/789/", extractor.lastAnalyzedUrl)
        assertTrue(viewModel.uiState.value is AnalysisUiState.Single)
        assertTrue(downloads.enqueued.isEmpty())
    }

    @Test
    fun `muestra la vista previa mientras analiza y luego las opciones`() {
        pageImages.preview = LinkPreview("Título rápido", "Canal", "https://i.ytimg.com/vi/x/hq.jpg")
        extractor.analysisGate = CompletableDeferred()

        val viewModel = createViewModel("https://youtu.be/abcdefghijk")

        assertEquals(AnalysisUiState.Preview(pageImages.preview!!), viewModel.uiState.value)

        extractor.analysisGate!!.complete(Unit)
        assertTrue(viewModel.uiState.value is AnalysisUiState.Single)
    }

    private val fastInfo = TestMedia.info.copy(videoOptions = listOf(TestMedia.video720), subtitles = emptyList())

    private fun partialThenFull(gate: CompletableDeferred<Unit>) {
        extractor.progressiveGate = gate
        extractor.progressiveUpdates = listOf(
            AnalysisUpdate(AnalysisResult.Single(fastInfo), isComplete = false),
            AnalysisUpdate(AnalysisResult.Single(TestMedia.info), isComplete = true),
        )
    }

    @Test
    fun `muestra el resultado rapido y despues las calidades completas`() {
        val gate = CompletableDeferred<Unit>()
        partialThenFull(gate)

        val viewModel = createViewModel("https://youtu.be/abcdefghijk")

        val partial = viewModel.single()
        assertTrue(partial.loadingMoreQualities)
        // Mientras llega el completo se ofrecen 1080p, 720p, 360p, 240p y 144p; solo el 720p es real (viene del análisis rápido).
        assertEquals(listOf(1080, 720, 360, 240, 144), partial.info.videoOptions.map { it.height })
        assertEquals(listOf(true, false, true, true, true), partial.info.videoOptions.map { it.isPreset })
        assertEquals(1080, partial.selectedVideo?.height)

        gate.complete(Unit)

        val full = viewModel.single()
        assertFalse(full.loadingMoreQualities)
        assertEquals(listOf(1080, 720), full.info.videoOptions.map { it.height })
        // El usuario no había elegido nada: la calidad por defecto vuelve a calcularse con la lista completa.
        assertEquals(1080, full.selectedVideo?.height)
    }

    @Test
    fun `lo que el usuario eligio no se pisa cuando llega el analisis completo`() {
        val gate = CompletableDeferred<Unit>()
        partialThenFull(gate)
        val viewModel = createViewModel("https://youtu.be/abcdefghijk")

        viewModel.onVideoSelected(TestMedia.video720)
        viewModel.onTabSelected(MediaKind.AUDIO)
        viewModel.onAudioSelected(AudioFormat.M4A)
        gate.complete(Unit)

        val full = viewModel.single()
        assertEquals(720, full.selectedVideo?.height)
        assertEquals(MediaKind.AUDIO, full.tab)
        assertEquals(AudioFormat.M4A, full.selectedAudio)
        assertFalse(full.loadingMoreQualities)
    }

    @Test
    fun `si el analisis completo falla se conserva el resultado rapido`() {
        extractor.progressiveUpdates = listOf(AnalysisUpdate(AnalysisResult.Single(fastInfo), isComplete = false))

        val viewModel = createViewModel("https://youtu.be/abcdefghijk")

        val state = viewModel.single()
        assertEquals(listOf(1080, 720, 360, 240, 144), state.info.videoOptions.map { it.height })
        assertFalse(state.loadingMoreQualities)
    }

    @Test
    fun `descargar una calidad predefinida pide hasta esa altura y no un formato concreto`() = runTest {
        partialThenFull(CompletableDeferred())
        val viewModel = createViewModel("https://youtu.be/abcdefghijk")

        viewModel.onVideoSelected(viewModel.single().info.videoOptions.first { it.height == 1080 })
        viewModel.events.test {
            viewModel.onDownload()
            awaitItem()
        }

        val selection = downloads.enqueued.single().video!!
        assertNull(selection.formatId)
        assertEquals(1080, selection.maxHeight)
    }

    @Test
    fun `una calidad predefinida elegida pasa a la real al llegar el analisis completo`() {
        val gate = CompletableDeferred<Unit>()
        partialThenFull(gate)
        val viewModel = createViewModel("https://youtu.be/abcdefghijk")

        viewModel.onVideoSelected(viewModel.single().info.videoOptions.first { it.height == 1080 })
        assertTrue(viewModel.single().selectedVideo!!.isPreset)
        gate.complete(Unit)

        val selected = viewModel.single().selectedVideo!!
        assertFalse(selected.isPreset)
        assertEquals(TestMedia.video1080.formatId, selected.formatId)
    }

    @Test
    fun `una calidad predefinida que el video no tiene vuelve a la de por defecto`() {
        val gate = CompletableDeferred<Unit>()
        partialThenFull(gate)
        val viewModel = createViewModel("https://youtu.be/abcdefghijk")

        viewModel.onVideoSelected(viewModel.single().info.videoOptions.first { it.height == 144 }) // el completo no la trae
        gate.complete(Unit)

        assertEquals(1080, viewModel.single().selectedVideo?.height)
    }

    @Test
    fun `el progreso estimado llega al cien por ciento al terminar`() {
        val viewModel = createViewModel()

        assertEquals(1f, viewModel.progress.value, 0f)
    }
}
