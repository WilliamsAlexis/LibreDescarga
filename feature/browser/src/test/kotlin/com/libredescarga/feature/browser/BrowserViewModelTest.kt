package com.libredescarga.feature.browser

import app.cash.turbine.test
import com.libredescarga.core.domain.browser.DetectedMedia
import com.libredescarga.core.domain.browser.NavigationDecision
import com.libredescarga.core.domain.usecase.AnalyzeUrlUseCase
import com.libredescarga.core.domain.usecase.EnqueueDownloadsUseCase
import com.libredescarga.core.model.MediaError
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.testing.FakeDownloadRepository
import com.libredescarga.core.testing.FakeRemoteFileSizeProbe
import com.libredescarga.core.testing.FakeBlocklistRepository
import com.libredescarga.core.testing.FakeBookmarkRepository
import com.libredescarga.core.testing.FakeBrowserHistoryRepository
import com.libredescarga.core.testing.FakeMediaExtractorRepository
import com.libredescarga.core.testing.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BrowserViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val store = BrowserTabsStore()
    private val extractor = FakeMediaExtractorRepository()
    private val history = FakeBrowserHistoryRepository()
    private val bookmarks = FakeBookmarkRepository()
    private val blocklist = FakeBlocklistRepository(setOf("bloqueado.com"))
    private val downloads = FakeDownloadRepository()
    private val sizeProbe = FakeRemoteFileSizeProbe()

    private fun createViewModel(initialUrl: String? = null) = BrowserViewModel(
        initialUrl = initialUrl,
        store = store,
        analyzeUrl = AnalyzeUrlUseCase(blocklist, extractor),
        enqueueDownloads = EnqueueDownloadsUseCase(downloads),
        sizeProbe = sizeProbe,
        historyRepository = history,
        bookmarkRepository = bookmarks,
        blocklist = blocklist,
    )

    // Así llega el resultado del script de la página: un texto JSON dentro de otro.
    private fun domResult(vararg items: Pair<String, String>): String {
        val inner = items.joinToString(",", "[", "]") { (tipo, url) -> "{\"t\":\"$tipo\",\"u\":\"$url\"}" }
        return "\"" + inner.replace("\"", "\\\"") + "\""
    }

    private fun TestScope.collect(viewModel: BrowserViewModel) =
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect {} }

    private val tabId get() = store.state.value.selectedId!!

    @Test
    fun `mantener presionada una imagen pide confirmar y al aceptar la agrega a las descargas`() = runTest {
        val viewModel = createViewModel()
        collect(viewModel)

        viewModel.onImageLongPress("http://fotos.example.com/gato.jpg?x=1")
        assertEquals("https://fotos.example.com/gato.jpg?x=1", viewModel.uiState.value.imageUrl)
        assertTrue(downloads.enqueued.isEmpty())

        viewModel.onImageConfirmed()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.imageUrl)
        assertEquals(1, downloads.enqueued.size)
        val request = downloads.enqueued.single()
        assertEquals(MediaKind.IMAGE, request.kind)
        assertEquals("https://fotos.example.com/gato.jpg?x=1", request.url)
        assertEquals("gato", request.fileName)
        assertEquals(BrowserMessage.Queued(1), viewModel.uiState.value.message)
    }

    @Test
    fun `cancelar la confirmacion no descarga la imagen`() = runTest {
        val viewModel = createViewModel()
        collect(viewModel)

        viewModel.onImageLongPress("https://fotos.example.com/gato.png")
        viewModel.onImageDismissed()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.imageUrl)
        assertTrue(downloads.enqueued.isEmpty())
    }

    @Test
    fun `una imagen de un sitio bloqueado o con esquema peligroso se ignora`() = runTest {
        val viewModel = createViewModel()
        collect(viewModel)

        viewModel.onImageLongPress("https://bloqueado.com/foto.jpg")
        assertNull(viewModel.uiState.value.imageUrl)
        viewModel.onImageLongPress("data:image/png;base64,AAAA")
        assertNull(viewModel.uiState.value.imageUrl)
        viewModel.onImageLongPress("file:///sdcard/foto.jpg")
        assertNull(viewModel.uiState.value.imageUrl)
    }

    // Así llega el resultado de evaluateJavascript: un texto JSON dentro de otro.
    private fun scriptResult(vararg urls: String): String {
        val inner = urls.joinToString(",", "[", "]") { "\"$it\"" }
        return "\"" + inner.replace("\"", "\\\"") + "\""
    }

    @Test
    fun `las imagenes de la pagina se listan y las elegidas se agregan a las descargas`() = runTest {
        val viewModel = createViewModel()
        collect(viewModel)

        viewModel.onPageImagesFound(
            scriptResult(
                "https://fotos.example.com/a.jpg",
                "http://fotos.example.com/b",
                "https://fotos.example.com/a.jpg",
                "data:image/png;base64,AAAA",
                "https://bloqueado.com/c.png",
            ),
        )
        assertEquals(
            listOf("https://fotos.example.com/a.jpg", "https://fotos.example.com/b"),
            viewModel.uiState.value.pageImages,
        )

        viewModel.onPageImagesConfirmed(listOf("https://fotos.example.com/b"))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pageImages)
        assertEquals(1, downloads.enqueued.size)
        assertEquals(MediaKind.IMAGE, downloads.enqueued.single().kind)
        assertEquals(BrowserMessage.Queued(1), viewModel.uiState.value.message)
    }

    @Test
    fun `una pagina sin imagenes avisa y no abre la hoja`() = runTest {
        val viewModel = createViewModel()
        collect(viewModel)

        viewModel.onPageImagesFound("\"[]\"")
        assertNull(viewModel.uiState.value.pageImages)
        assertEquals(BrowserMessage.NoImages, viewModel.uiState.value.message)

        viewModel.onPageImagesFound(null)
        assertNull(viewModel.uiState.value.pageImages)
    }

    @Test
    fun `sin enlace inicial abre una pestaña en blanco`() = runTest {
        val viewModel = createViewModel()
        collect(viewModel)
        assertEquals(1, viewModel.uiState.value.tabs.size)
        assertEquals("", viewModel.uiState.value.selected?.url)
    }

    @Test
    fun `el enlace inicial reutiliza la pestaña en blanco y pasa a https`() = runTest {
        val viewModel = createViewModel("http://example.com/watch?v=1")
        collect(viewModel)
        assertEquals(1, viewModel.uiState.value.tabs.size)
        assertEquals("https://example.com/watch?v=1", viewModel.uiState.value.selected?.url)
    }

    @Test
    fun `un enlace inicial de un sitio bloqueado no se abre`() = runTest {
        val viewModel = createViewModel("https://bloqueado.com/v")
        collect(viewModel)
        assertEquals("", viewModel.uiState.value.selected?.url)
        assertEquals(BrowserMessage.BlockedDomain("bloqueado.com"), viewModel.uiState.value.message)
    }

    @Test
    fun `escribir palabras busca y escribir un esquema peligroso se bloquea`() = runTest {
        val viewModel = createViewModel()
        collect(viewModel)

        viewModel.onSubmitAddress("gatos graciosos")
        assertEquals("https://duckduckgo.com/?q=gatos+graciosos", viewModel.uiState.value.selected?.url)

        viewModel.onSubmitAddress("javascript:alert(1)")
        assertEquals(BrowserMessage.BlockedScheme, viewModel.uiState.value.message)
        assertEquals("https://duckduckgo.com/?q=gatos+graciosos", viewModel.uiState.value.selected?.url)
    }

    @Test
    fun `navegar en una pestaña con página pide cargarla`() = runTest {
        val viewModel = createViewModel("https://example.com/a")
        collect(viewModel)

        viewModel.events.test {
            viewModel.onSubmitAddress("https://example.com/b")
            assertEquals(BrowserEvent.Load(tabId, "https://example.com/b"), awaitItem())
        }
    }

    @Test
    fun `decide cada navegación sin esperar`() = runTest {
        val viewModel = createViewModel()
        collect(viewModel)
        assertTrue(viewModel.decide("https://example.com") is NavigationDecision.Allow)
        assertEquals(NavigationDecision.BlockScheme, viewModel.decide("file:///sdcard/a"))
        assertEquals(NavigationDecision.BlockDomain("bloqueado.com"), viewModel.decide("https://www.bloqueado.com/x"))
    }

    @Test
    fun `una página compatible muestra el botón de descarga`() = runTest {
        val viewModel = createViewModel("https://example.com/watch?v=1")
        collect(viewModel)

        viewModel.onPageStarted(tabId, "https://example.com/watch?v=1")
        viewModel.onPageFinished(tabId, "https://example.com/watch?v=1", "Mi video")
        assertEquals(ProbeState.CHECKING, viewModel.uiState.value.page.probe)
        assertFalse(viewModel.uiState.value.showDownloadFab)

        advanceUntilIdle()

        assertEquals(ProbeState.SUPPORTED, viewModel.uiState.value.page.probe)
        assertTrue(viewModel.uiState.value.showDownloadFab)
        assertEquals("https://example.com/watch?v=1", viewModel.uiState.value.pageCandidate)
        assertEquals(1, viewModel.uiState.value.downloadCount)
        assertEquals("https://example.com/watch?v=1", extractor.lastAnalyzedUrl)

        viewModel.onDownloadFabClick()
        assertTrue(viewModel.uiState.value.mediaSheetOpen)
        viewModel.onDownloadPage()
        assertFalse(viewModel.uiState.value.mediaSheetOpen)
        assertEquals("https://example.com/watch?v=1", viewModel.uiState.value.analysisUrl)
    }

    @Test
    fun `si el motor no la admite se usan los archivos detectados`() = runTest {
        extractor.analysisError = MediaError.Unsupported()
        val viewModel = createViewModel("https://example.com/reproductor")
        collect(viewModel)

        viewModel.onPageStarted(tabId, "https://example.com/reproductor")
        viewModel.onMediaSniffed(tabId, "https://cdn.example.com/master.m3u8")
        viewModel.onDomResult(
            tabId,
            domResult("v" to "https://cdn.example.com/v.mp4", "i" to "https://cdn.example.com/foto?id=1"),
        )
        viewModel.onPageFinished(tabId, "https://example.com/reproductor", "Reproductor")
        advanceUntilIdle()

        assertEquals(ProbeState.UNSUPPORTED, viewModel.uiState.value.page.probe)
        assertTrue(viewModel.uiState.value.showDownloadFab)
        assertNull(viewModel.uiState.value.pageCandidate)
        assertEquals(
            listOf(
                DetectedMedia("https://cdn.example.com/master.m3u8", MediaKind.VIDEO),
                DetectedMedia("https://cdn.example.com/v.mp4", MediaKind.VIDEO),
            ),
            viewModel.uiState.value.media,
        )
        assertEquals(2, viewModel.uiState.value.downloadCount) // La imagen de la página no se ofrece.

        // El botón abre la lista; un video se analiza para elegir su calidad.
        viewModel.onDownloadFabClick()
        assertTrue(viewModel.uiState.value.mediaSheetOpen)
        viewModel.onMediaDownload(DetectedMedia("https://cdn.example.com/v.mp4", MediaKind.VIDEO))
        assertFalse(viewModel.uiState.value.mediaSheetOpen)
        assertEquals("https://cdn.example.com/v.mp4", viewModel.uiState.value.analysisUrl)
    }

    @Test
    fun `el contenido con DRM oculta el botón y nunca se ofrece`() = runTest {
        extractor.analysisError = MediaError.DrmProtected()
        val viewModel = createViewModel("https://example.com/pelicula")
        collect(viewModel)

        viewModel.onPageStarted(tabId, "https://example.com/pelicula")
        viewModel.onMediaSniffed(tabId, "https://cdn.example.com/master.m3u8")
        viewModel.onPageFinished(tabId, "https://example.com/pelicula", "Película")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.page.drm)
        assertFalse(viewModel.uiState.value.showDownloadFab)
    }

    @Test
    fun `la aviso de DRM del navegador tambien oculta el botón`() = runTest {
        val viewModel = createViewModel("https://example.com/watch?v=2")
        collect(viewModel)
        viewModel.onPageStarted(tabId, "https://example.com/watch?v=2")
        viewModel.onMediaSniffed(tabId, "https://cdn.example.com/a.mp4")
        assertTrue(viewModel.uiState.value.showDownloadFab)

        viewModel.onDrmDetected(tabId)
        assertFalse(viewModel.uiState.value.showDownloadFab)
    }

    @Test
    fun `las imagenes de la pagina no se ofrecen automaticamente`() = runTest {
        val viewModel = createViewModel("https://example.com/galeria")
        collect(viewModel)

        viewModel.onMediaSniffed(tabId, "https://cdn.example.com/a.jpg")
        viewModel.onDomResult(tabId, domResult("i" to "https://cdn.example.com/c?id=3"))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.media.isEmpty())
        assertFalse(viewModel.uiState.value.showDownloadFab)
    }

    @Test
    fun `si la direccion es directamente una imagen se ofrece solo esa imagen`() = runTest {
        val viewModel = createViewModel()
        collect(viewModel)

        viewModel.onPageStarted(tabId, "https://cdn.example.com/foto.jpg")
        viewModel.onMediaSniffed(tabId, "https://cdn.example.com/otra.jpg")

        assertTrue(viewModel.uiState.value.showDownloadFab)
        assertEquals(
            listOf(DetectedMedia("https://cdn.example.com/foto.jpg", MediaKind.IMAGE)),
            viewModel.uiState.value.media,
        )
        assertEquals(1, viewModel.uiState.value.downloadCount)
    }

    @Test
    fun `si el motor reconoce el video de la pagina no se ofrecen las sugerencias ni otros archivos`() = runTest {
        val viewModel = createViewModel("https://example.com/watch?v=1")
        collect(viewModel)
        viewModel.onPageStarted(tabId, "https://example.com/watch?v=1")
        viewModel.onMediaSniffed(tabId, "https://cdn.example.com/sugerencia.mp4")
        viewModel.onDomResult(tabId, domResult("v" to "https://cdn.example.com/otra.mp4"))
        viewModel.onPageFinished(tabId, "https://example.com/watch?v=1", "Mi video")
        advanceUntilIdle()

        assertEquals("https://example.com/watch?v=1", viewModel.uiState.value.pageCandidate)
        assertTrue(viewModel.uiState.value.media.isEmpty())
        assertEquals(1, viewModel.uiState.value.downloadCount)
    }

    @Test
    fun `al cambiar de video sin recargar la pagina se vuelve a revisar`() = runTest {
        val viewModel = createViewModel("https://example.com/watch?v=1")
        collect(viewModel)
        viewModel.onPageStarted(tabId, "https://example.com/watch?v=1")
        viewModel.onPageFinished(tabId, "https://example.com/watch?v=1", "Video 1")
        advanceUntilIdle()
        assertEquals("https://example.com/watch?v=1", viewModel.uiState.value.pageCandidate)

        // Como en YouTube: la dirección cambia pero la página no se recarga.
        viewModel.onUrlChanged(tabId, "https://example.com/watch?v=2")
        assertEquals(ProbeState.CHECKING, viewModel.uiState.value.page.probe)
        advanceUntilIdle()

        assertEquals("https://example.com/watch?v=2", extractor.lastAnalyzedUrl)
        assertEquals("https://example.com/watch?v=2", viewModel.uiState.value.pageCandidate)
    }

    @Test
    fun `se consulta el tamaño de los videos detectados`() = runTest {
        sizeProbe.sizes = mapOf("https://cdn.example.com/v.mp4" to 5_000_000L)
        extractor.analysisError = MediaError.Unsupported()
        val viewModel = createViewModel("https://example.com/reproductor")
        collect(viewModel)
        viewModel.onPageStarted(tabId, "https://example.com/reproductor")
        viewModel.onMediaSniffed(tabId, "https://cdn.example.com/v.mp4")

        advanceUntilIdle()

        assertEquals(5_000_000L, viewModel.uiState.value.sizes["https://cdn.example.com/v.mp4"])
    }

    @Test
    fun `descargar una imagen de la lista la agrega a la cola y la marca`() = runTest {
        val viewModel = createViewModel()
        collect(viewModel)
        val foto = DetectedMedia("https://cdn.example.com/foto.jpg", MediaKind.IMAGE)
        viewModel.onPageStarted(tabId, foto.url)
        viewModel.onDownloadFabClick()

        viewModel.onMediaDownload(foto)
        advanceUntilIdle()

        assertEquals(MediaKind.IMAGE, downloads.enqueued.single().kind)
        assertEquals(setOf(foto.url), viewModel.uiState.value.queuedUrls)
        assertEquals(BrowserMessage.Queued(1), viewModel.uiState.value.message)
        // La lista sigue abierta para poder bajar más.
        assertTrue(viewModel.uiState.value.mediaSheetOpen)
    }

    @Test
    fun `con DRM se ocultan videos y audios pero la imagen que se ve sigue disponible`() = runTest {
        val viewModel = createViewModel()
        collect(viewModel)
        viewModel.onPageStarted(tabId, "https://cdn.example.com/poster.jpg")
        viewModel.onMediaSniffed(tabId, "https://cdn.example.com/master.m3u8")
        assertEquals(2, viewModel.uiState.value.downloadCount)

        viewModel.onDrmDetected(tabId)

        assertEquals(listOf("https://cdn.example.com/poster.jpg"), viewModel.uiState.value.media.map { it.url })
        assertTrue(viewModel.uiState.value.showDownloadFab)
    }

    @Test
    fun `las portadas no se analizan`() = runTest {
        val viewModel = createViewModel("https://example.com/")
        collect(viewModel)
        viewModel.onPageStarted(tabId, "https://example.com/")
        viewModel.onPageFinished(tabId, "https://example.com/", "Inicio")
        advanceUntilIdle()
        assertEquals(ProbeState.IDLE, viewModel.uiState.value.page.probe)
        assertNull(extractor.lastAnalyzedUrl)
    }

    @Test
    fun `al empezar otra página se olvidan los archivos de la anterior`() = runTest {
        val viewModel = createViewModel("https://example.com/watch?v=1")
        collect(viewModel)
        viewModel.onPageStarted(tabId, "https://example.com/watch?v=1")
        viewModel.onMediaSniffed(tabId, "https://cdn.example.com/a.mp4")
        assertEquals(1, viewModel.uiState.value.page.media.size)

        viewModel.onPageStarted(tabId, "https://example.com/otra")
        assertTrue(viewModel.uiState.value.page.media.isEmpty())
    }

    @Test
    fun `el historial se guarda salvo en incógnito`() = runTest {
        val viewModel = createViewModel("https://example.com/a")
        collect(viewModel)
        viewModel.onPageFinished(tabId, "https://example.com/a", "Página A")
        assertEquals(listOf("https://example.com/a"), history.current.map { it.url })

        viewModel.onNewTab(incognito = true)
        viewModel.onPageStarted(tabId, "https://example.com/secreto")
        viewModel.onPageFinished(tabId, "https://example.com/secreto", "Secreto")
        assertEquals(listOf("https://example.com/a"), history.current.map { it.url })
    }

    @Test
    fun `marcadores y borrar el historial`() = runTest {
        val viewModel = createViewModel("https://example.com/a")
        collect(viewModel)
        viewModel.onTitle(tabId, "Página A")

        viewModel.onToggleBookmark()
        assertTrue(viewModel.uiState.value.isBookmarked)
        assertEquals(BrowserMessage.BookmarkToggled(added = true), viewModel.uiState.value.message)

        viewModel.onToggleBookmark()
        assertFalse(viewModel.uiState.value.isBookmarked)

        viewModel.onPageFinished(tabId, "https://example.com/a", "Página A")
        viewModel.onClearHistory()
        assertTrue(viewModel.uiState.value.history.isEmpty())
        assertEquals(BrowserMessage.HistoryCleared, viewModel.uiState.value.message)
    }

    @Test
    fun `una descarga directa del navegador abre el análisis`() = runTest {
        val viewModel = createViewModel()
        collect(viewModel)
        viewModel.onDownloadRequested("http://cdn.example.com/archivo.mp4")
        assertEquals("https://cdn.example.com/archivo.mp4", viewModel.uiState.value.analysisUrl)

        viewModel.onAnalysisDismissed()
        viewModel.onDownloadRequested("file:///sdcard/a.mp4")
        assertNull(viewModel.uiState.value.analysisUrl)
    }

    @Test
    fun `cerrar pestañas y salir de incógnito`() = runTest {
        val viewModel = createViewModel("https://example.com/a")
        collect(viewModel)
        viewModel.onNewTab(incognito = true)
        assertEquals(2, viewModel.uiState.value.tabs.size)
        assertTrue(viewModel.hasIncognitoTabs)

        viewModel.onLeave()
        assertEquals(1, viewModel.uiState.value.tabs.size)
        assertFalse(viewModel.hasIncognitoTabs)

        viewModel.onCloseTab(viewModel.uiState.value.tabs.first().id)
        assertEquals(1, viewModel.uiState.value.tabs.size) // Queda una pestaña en blanco.
        assertEquals("", viewModel.uiState.value.selected?.url)
    }
}
