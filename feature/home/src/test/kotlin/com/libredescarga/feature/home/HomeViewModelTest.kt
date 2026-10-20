package com.libredescarga.feature.home

import app.cash.turbine.test
import com.libredescarga.core.domain.usecase.CreateQuickDownloadRequestUseCase
import com.libredescarga.core.domain.usecase.EnqueueDownloadsUseCase
import com.libredescarga.core.domain.usecase.ResolveSearchInputUseCase
import com.libredescarga.core.domain.usecase.SearchMediaUseCase
import com.libredescarga.core.model.MediaError
import com.libredescarga.core.model.SearchProvider
import com.libredescarga.core.model.UserSettings
import com.libredescarga.core.model.VideoQuality
import com.libredescarga.core.testing.FakeDownloadRepository
import com.libredescarga.core.testing.FakeIncomingLinkRepository
import com.libredescarga.core.testing.FakeMediaExtractorRepository
import com.libredescarga.core.testing.FakeSearchHistoryRepository
import com.libredescarga.core.testing.FakeSiteRepository
import com.libredescarga.core.testing.FakeUserPreferencesRepository
import com.libredescarga.core.testing.MainDispatcherRule
import com.libredescarga.core.testing.TestMedia
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val extractor = FakeMediaExtractorRepository()
    private val history = FakeSearchHistoryRepository()
    private val sites = FakeSiteRepository()
    private val preferences = FakeUserPreferencesRepository()
    private val incomingLinks = FakeIncomingLinkRepository()
    private val downloads = FakeDownloadRepository()
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        viewModel = HomeViewModel(
            resolveInput = ResolveSearchInputUseCase(),
            searchMedia = SearchMediaUseCase(extractor, history),
            history = history,
            siteRepository = sites,
            preferences = preferences,
            incomingLinks = incomingLinks,
            createQuickRequest = CreateQuickDownloadRequestUseCase(preferences),
            enqueueDownloads = EnqueueDownloadsUseCase(downloads),
        )
    }

    private fun kotlinx.coroutines.test.TestScope.collectState() =
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect {} }

    @Test
    fun `un enlace abre la hoja de análisis`() = runTest {
        collectState()
        viewModel.onQueryChange("http://example.com/watch/1")
        viewModel.onSubmit()
        assertEquals("https://example.com/watch/1", viewModel.uiState.value.analysisUrl)

        viewModel.onAnalysisDismissed()
        assertNull(viewModel.uiState.value.analysisUrl)
    }

    @Test
    fun `palabras clave buscan en el sitio elegido y quedan en el historial`() = runTest {
        collectState()
        viewModel.onProviderSelected(SearchProvider.SOUNDCLOUD)
        viewModel.onQueryChange("lofi")
        viewModel.onSubmit()

        val search = viewModel.uiState.value.search as SearchState.Results
        assertEquals(listOf(TestMedia.entry), search.items)
        assertEquals("lofi" to SearchProvider.SOUNDCLOUD, extractor.lastSearch)
        assertEquals(listOf("lofi"), history.suggestions("", 10).first())
    }

    @Test
    fun `un error de búsqueda se muestra y se puede reintentar`() = runTest {
        collectState()
        extractor.searchError = MediaError.Network()
        viewModel.onQueryChange("gatos")
        viewModel.onSubmit()
        assertTrue((viewModel.uiState.value.search as SearchState.Failed).error is MediaError.Network)

        extractor.searchError = null
        viewModel.onRetrySearch()
        assertTrue(viewModel.uiState.value.search is SearchState.Results)

        viewModel.onCloseResults()
        assertEquals(SearchState.Idle, viewModel.uiState.value.search)
    }

    @Test
    fun `los esquemas peligrosos muestran un aviso`() = runTest {
        collectState()
        viewModel.onQueryChange("javascript:alert(1)")
        viewModel.onSubmit()
        assertEquals(HomeMessage.ForbiddenLink, viewModel.uiState.value.message)
        assertNull(viewModel.uiState.value.analysisUrl)
    }

    @Test
    fun `el portapapeles ofrece cada enlace una sola vez`() = runTest {
        collectState()
        viewModel.onClipboardText("Mira: https://example.com/v/9")
        assertEquals("https://example.com/v/9", viewModel.uiState.value.clipboardUrl)

        viewModel.onClipboardDismiss()
        viewModel.onClipboardText("https://example.com/v/9")
        assertNull(viewModel.uiState.value.clipboardUrl)
    }

    @Test
    fun `analizar desde el portapapeles abre la hoja`() = runTest {
        collectState()
        viewModel.onClipboardText("https://example.com/v/10")
        viewModel.onClipboardAnalyze()
        assertEquals("https://example.com/v/10", viewModel.uiState.value.analysisUrl)
        assertNull(viewModel.uiState.value.clipboardUrl)
    }

    @Test
    fun `sin detección de portapapeles no se ofrece nada`() = runTest {
        preferences.emit(UserSettings(clipboardDetection = false))
        collectState()
        viewModel.onClipboardText("https://example.com/v/11")
        assertNull(viewModel.uiState.value.clipboardUrl)
    }

    @Test
    fun `un enlace compartido desde otra app abre el análisis`() = runTest {
        collectState()
        incomingLinks.submit("https://example.com/shared")
        advanceUntilIdle()
        assertEquals("https://example.com/shared", viewModel.uiState.value.analysisUrl)
        assertNull(incomingLinks.pendingLink.value)
    }

    @Test
    fun `descarga rápida encola con la calidad por defecto`() = runTest {
        preferences.emit(UserSettings(defaultVideoQuality = VideoQuality.P480))
        collectState()
        assertTrue(viewModel.uiState.value.quickDownloadAvailable)

        viewModel.onQuickDownload(TestMedia.entry)

        assertEquals(480, downloads.enqueued.single().video?.maxHeight)
        assertEquals(HomeMessage.Queued(1), viewModel.uiState.value.message)
    }

    @Test
    fun `descarga rápida sin calidad por defecto abre el análisis`() = runTest {
        collectState()
        viewModel.onQuickDownload(TestMedia.entry)
        assertEquals(TestMedia.entry.url, viewModel.uiState.value.analysisUrl)
        assertTrue(downloads.enqueued.isEmpty())
    }

    @Test
    fun `una categoría abre la búsqueda en el navegador con el sitio elegido`() = runTest {
        collectState()
        viewModel.onProviderSelected(SearchProvider.SOUNDCLOUD)

        viewModel.events.test {
            viewModel.onCategoryClick("lofi beats")
            assertEquals(HomeEvent.OpenSite("https://soundcloud.com/search?q=lofi+beats"), awaitItem())
        }
        assertTrue(viewModel.uiState.value.search is SearchState.Idle)
    }

    @Test
    fun `tocar un sitio pide abrirlo y se pueden agregar sitios propios`() = runTest {
        collectState()
        viewModel.onAddSite("Mi archivo", "archivo.example.org/videos")
        val site = viewModel.uiState.value.sites.single()
        assertEquals("Mi archivo", site.name)

        viewModel.events.test {
            viewModel.onSiteClick(site)
            assertEquals(HomeEvent.OpenSite(site.url), awaitItem())
        }

        viewModel.onRemoveSite(site)
        assertTrue(viewModel.uiState.value.sites.isEmpty())
    }
}
