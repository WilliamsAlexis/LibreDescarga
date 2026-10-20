package com.libredescarga.feature.browser

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.domain.repository.RemoteFileSizeProbe
import com.libredescarga.core.domain.browser.DetectedMedia
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.libredescarga.core.domain.browser.MediaSniffer
import com.libredescarga.core.domain.browser.NavigationDecision
import com.libredescarga.core.domain.browser.NavigationPolicy
import com.libredescarga.core.domain.browser.Omnibox
import com.libredescarga.core.domain.browser.PageProbePolicy
import com.libredescarga.core.domain.repository.BlocklistRepository
import com.libredescarga.core.domain.repository.BookmarkRepository
import com.libredescarga.core.domain.repository.BrowserHistoryRepository
import com.libredescarga.core.domain.usecase.AnalyzeUrlUseCase
import com.libredescarga.core.domain.usecase.EnqueueDownloadsUseCase
import com.libredescarga.core.domain.usecase.ImageDownloads
import com.libredescarga.core.model.Bookmark
import com.libredescarga.core.model.HistoryEntry
import com.libredescarga.core.model.MediaError
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** ¿Hay un video o audio descargable en la página? */
enum class ProbeState { IDLE, CHECKING, SUPPORTED, UNSUPPORTED }

/** Lo que se sabe de la página que muestra cada pestaña. */
data class PageState(
    val progress: Int = 0,
    val loading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val error: Boolean = false,
    /** Contenido con protección DRM (Widevine): nunca se intenta descargar. */
    val drm: Boolean = false,
    val probe: ProbeState = ProbeState.IDLE,
    /** Videos, audios e imágenes detectados en la página (por las etiquetas y por las peticiones que hizo). */
    val media: List<DetectedMedia> = emptyList(),
)

sealed interface BrowserMessage {
    data object BlockedScheme : BrowserMessage
    data class BlockedDomain(val domain: String) : BrowserMessage
    data class Queued(val count: Int) : BrowserMessage
    data class BookmarkToggled(val added: Boolean) : BrowserMessage
    data object HistoryCleared : BrowserMessage
    data object ImageFailed : BrowserMessage
    data object NoImages : BrowserMessage
}

data class BrowserUiState(
    val tabs: List<BrowserTab> = emptyList(),
    val selected: BrowserTab? = null,
    val page: PageState = PageState(),
    val bookmarks: List<Bookmark> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val analysisUrl: String? = null,
    /** Imagen que el usuario mantuvo presionada y que falta confirmar para descargar. */
    val imageUrl: String? = null,
    /** Imágenes encontradas en la página, para elegir cuáles guardar; null si la hoja está cerrada. */
    val pageImages: List<String>? = null,
    /** Hoja con los archivos detectados en la página (el botón flotante la abre). */
    val mediaSheetOpen: Boolean = false,
    /** Tamaño en bytes de los archivos detectados, cuando el servidor lo informa. */
    val sizes: Map<String, Long> = emptyMap(),
    /** Archivos que ya se agregaron a la cola desde la hoja, para marcarlos. */
    val queuedUrls: Set<String> = emptySet(),
    val message: BrowserMessage? = null,
) {
    val isBookmarked: Boolean get() = selected?.url?.let { url -> url.isNotEmpty() && bookmarks.any { it.url == url } } == true

    /** La propia página, si el motor de descargas reconoce un video o audio en ella (y no tiene DRM). */
    val pageCandidate: String?
        get() = selected?.url?.takeIf { it.isNotEmpty() && page.probe == ProbeState.SUPPORTED && !page.drm }

    /**
     * Lo que se ofrece además de la propia página:
     * - si la dirección es directamente una imagen, esa imagen;
     * - si el motor NO reconoce el video o audio de la página, los que la página está reproduciendo.
     * Nunca se ofrecen las sugerencias ni las demás imágenes de la página (eso se hace manteniendo presionada una imagen).
     */
    val media: List<DetectedMedia>
        get() {
            val url = selected?.url.orEmpty()
            val image = if (ImageDownloads.isImageUrl(url)) listOf(DetectedMedia(url, MediaKind.IMAGE)) else emptyList()
            val playing = if (pageCandidate != null || page.drm) {
                emptyList()
            } else {
                page.media.filter { it.kind != MediaKind.IMAGE }
            }
            return image + playing
        }

    /** Número que muestra el botón flotante de descarga. */
    val downloadCount: Int get() = (if (pageCandidate != null) 1 else 0) + media.size

    /** El botón flotante aparece solo cuando se detectó algo descargable. */
    val showDownloadFab: Boolean get() = downloadCount > 0
}

sealed interface BrowserEvent {
    /** Carga [url] en la pestaña [tabId]. */
    data class Load(val tabId: String, val url: String) : BrowserEvent

    data object Close : BrowserEvent
}

@HiltViewModel(assistedFactory = BrowserViewModel.Factory::class)
class BrowserViewModel @AssistedInject constructor(
    @Assisted initialUrl: String?,
    private val store: BrowserTabsStore,
    private val analyzeUrl: AnalyzeUrlUseCase,
    private val enqueueDownloads: EnqueueDownloadsUseCase,
    private val sizeProbe: RemoteFileSizeProbe,
    private val historyRepository: BrowserHistoryRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val blocklist: BlocklistRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(initialUrl: String?): BrowserViewModel
    }

    private val pages = MutableStateFlow<Map<String, PageState>>(emptyMap())
    private val local = MutableStateFlow(LocalState())
    private val probeJobs = mutableMapOf<String, Job>()

    // Consulta de tamaños en curso y direcciones ya consultadas (cada una se consulta una sola vez).
    private var sizeJob: Job? = null
    private val probed = mutableSetOf<String>()

    @Volatile
    private var blockedDomains: Set<String> = emptySet()

    private val _events = Channel<BrowserEvent>(Channel.BUFFERED)
    val events: Flow<BrowserEvent> = _events.receiveAsFlow()

    private data class LocalState(
        val analysisUrl: String? = null,
        val imageUrl: String? = null,
        val pageImages: List<String>? = null,
        val mediaSheetOpen: Boolean = false,
        val sizes: Map<String, Long> = emptyMap(),
        val queued: Set<String> = emptySet(),
        val message: BrowserMessage? = null,
    )

    val uiState: StateFlow<BrowserUiState> = combine(
        store.state,
        pages,
        bookmarkRepository.bookmarks,
        historyRepository.history,
        local,
    ) { tabs, pages, bookmarks, history, local ->
        BrowserUiState(
            tabs = tabs.tabs,
            selected = tabs.selected,
            page = tabs.selectedId?.let { pages[it] } ?: PageState(),
            bookmarks = bookmarks,
            history = history,
            analysisUrl = local.analysisUrl,
            imageUrl = local.imageUrl,
            pageImages = local.pageImages,
            mediaSheetOpen = local.mediaSheetOpen,
            sizes = local.sizes,
            queuedUrls = local.queued,
            message = local.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowserUiState())

    init {
        store.ensureTab()
        viewModelScope.launch {
            // La lista negra se carga primero, porque cada navegación la consulta sin esperar.
            blockedDomains = blocklist.all()
            initialUrl?.let(::openInitial)
        }
    }

    /** Enlace con el que se abrió el navegador: usa la pestaña en blanco si hay una, o abre otra. */
    private fun openInitial(text: String) {
        val url = Omnibox.resolve(text) ?: return showMessage(BrowserMessage.BlockedScheme)
        when (val decision = NavigationPolicy.decide(url, blockedDomains)) {
            is NavigationDecision.Allow -> {
                val current = store.state.value.selected
                if (current != null && current.url.isEmpty() && !current.incognito) {
                    store.update(current.id) { it.copy(url = decision.url) }
                } else {
                    store.open(decision.url)
                }
            }
            else -> onNavigationBlocked(decision)
        }
    }

    // --- Pestañas ------------------------------------------------------------------------------

    fun onNewTab(incognito: Boolean = false) {
        store.open("", incognito)
    }

    fun onSelectTab(id: String) = store.select(id)

    fun onCloseTab(id: String) {
        probeJobs.remove(id)?.cancel()
        pages.update { it - id }
        store.close(id)
    }

    /** Al salir del navegador: se cierran las pestañas de incógnito. */
    fun onLeave() {
        probeJobs.values.forEach(Job::cancel)
        probeJobs.clear()
        store.closeIncognito()
    }

    val hasIncognitoTabs: Boolean get() = store.hasIncognito

    // --- Barra de direcciones --------------------------------------------------------------------

    /** Un enlace o una búsqueda escrita en la barra. */
    fun onSubmitAddress(text: String) = openAddress(text)

    fun onOpenUrl(url: String) = openAddress(url)

    private fun openAddress(text: String) {
        val url = Omnibox.resolve(text) ?: run {
            showMessage(BrowserMessage.BlockedScheme)
            return
        }
        when (val decision = NavigationPolicy.decide(url, blockedDomains)) {
            is NavigationDecision.Allow -> {
                val tab = store.state.value.selected ?: return
                val hadPage = tab.url.isNotEmpty()
                store.update(tab.id) { it.copy(url = decision.url) }
                // Una pestaña en blanco crea su WebView ya con la dirección; la que tiene página la recibe por evento.
                if (hadPage) viewModelScope.launch { _events.send(BrowserEvent.Load(tab.id, decision.url)) }
            }
            else -> onNavigationBlocked(decision)
        }
    }

    /** Lo consulta el WebView en cada navegación, de forma inmediata. */
    fun decide(url: String): NavigationDecision = NavigationPolicy.decide(url, blockedDomains)

    fun onNavigationBlocked(decision: NavigationDecision) {
        when (decision) {
            NavigationDecision.BlockScheme -> showMessage(BrowserMessage.BlockedScheme)
            is NavigationDecision.BlockDomain -> showMessage(BrowserMessage.BlockedDomain(decision.domain))
            is NavigationDecision.Allow -> Unit
        }
    }

    // --- Eventos del WebView -----------------------------------------------------------------------

    fun onPageStarted(tabId: String, url: String) {
        probeJobs.remove(tabId)?.cancel()
        store.update(tabId) { it.copy(url = url) }
        updatePage(tabId) { PageState(loading = true, canGoBack = it.canGoBack, canGoForward = it.canGoForward) }
    }

    fun onUrlChanged(tabId: String, url: String) {
        if (url.isBlank() || url == "about:blank") return
        val previous = store.state.value.tabs.firstOrNull { it.id == tabId }?.url.orEmpty()
        store.update(tabId) { it.copy(url = url) }
        // Páginas como YouTube cambian de video sin recargarse: se vuelve a revisar qué video o audio hay ahora.
        val finishedLoading = pages.value[tabId]?.loading == false
        if (previous.isNotEmpty() && previous != url && finishedLoading) {
            probeJobs.remove(tabId)?.cancel()
            updatePage(tabId) { it.copy(media = emptyList(), probe = ProbeState.IDLE, drm = false) }
            startProbe(tabId, url)
        }
    }

    fun onProgress(tabId: String, progress: Int) = updatePage(tabId) { it.copy(progress = progress) }

    fun onTitle(tabId: String, title: String) = store.update(tabId) { it.copy(title = title) }

    fun onNavigationState(tabId: String, canGoBack: Boolean, canGoForward: Boolean) =
        updatePage(tabId) { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }

    fun onPageFinished(tabId: String, url: String, title: String?) {
        val tab = store.state.value.tabs.firstOrNull { it.id == tabId } ?: return
        updatePage(tabId) { it.copy(loading = false, progress = 100) }
        if (!title.isNullOrBlank()) store.update(tabId) { it.copy(title = title) }
        // La navegación de incógnito no deja rastro en el historial.
        if (!tab.incognito && url.startsWith("https://")) {
            viewModelScope.launch { historyRepository.record(url, title.orEmpty()) }
        }
        startProbe(tabId, url)
    }

    fun onPageError(tabId: String, error: Boolean) = updatePage(tabId) { it.copy(error = error, loading = false) }

    /** Una petición de la página apunta a un video o audio (se reconoce por su extensión). */
    fun onMediaSniffed(tabId: String, url: String) {
        updatePage(tabId) { it.copy(media = MediaSniffer.add(it.media, url)) }
        scheduleSizeProbe()
    }

    /** Resultado del script que lista las etiquetas video y audio de la página. */
    fun onDomResult(tabId: String, raw: String?) {
        // Solo video y audio: las imágenes de la página no se ofrecen en automático.
        val found = MediaSniffer.parseDomResult(raw).filter { it.kind != MediaKind.IMAGE }
        if (found.isEmpty()) return
        updatePage(tabId) { page ->
            page.copy(media = found.fold(page.media) { acc, item -> MediaSniffer.add(acc, item.url, item.kind) })
        }
        scheduleSizeProbe()
    }

    fun onDrmDetected(tabId: String) = updatePage(tabId) { it.copy(drm = true) }

    /** El WebView recibió un archivo para descargar (enlace de descarga directa). */
    fun onDownloadRequested(url: String) {
        val allowed = (decide(url) as? NavigationDecision.Allow)?.url ?: return
        // Un archivo de imagen no se analiza con yt-dlp: se ofrece descargarlo directamente.
        if (ImageDownloads.isImageUrl(allowed)) {
            local.update { it.copy(imageUrl = allowed) }
        } else {
            local.update { it.copy(analysisUrl = allowed) }
        }
    }

    /** El usuario mantuvo presionada una imagen: se pide confirmar antes de descargarla. */
    fun onImageLongPress(url: String) {
        val allowed = (decide(url) as? NavigationDecision.Allow)?.url ?: return
        local.update { it.copy(imageUrl = allowed) }
    }

    /** El usuario confirmó: la imagen se agrega a la cola de descargas. */
    fun onImageConfirmed() {
        val url = local.value.imageUrl ?: return
        local.update { it.copy(imageUrl = null) }
        val request = ImageDownloads.request(url, uiState.value.selected?.title) ?: return
        viewModelScope.launch {
            try {
                showMessage(BrowserMessage.Queued(enqueueDownloads(listOf(request))))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                showMessage(BrowserMessage.ImageFailed)
            }
        }
    }

    fun onImageDismissed() = local.update { it.copy(imageUrl = null) }

    /** Resultado de leer las imágenes de la página: si hay, se abre la hoja para elegirlas; si no, se avisa. */
    fun onPageImagesFound(raw: String?) {
        val images = MediaSniffer.parseImagesResult(raw).filter { decide(it) is NavigationDecision.Allow }
        if (images.isEmpty()) showMessage(BrowserMessage.NoImages) else local.update { it.copy(pageImages = images) }
    }

    fun onPageImagesDismissed() = local.update { it.copy(pageImages = null) }

    /** El usuario eligió varias imágenes: todas se agregan a la cola de descargas. */
    fun onPageImagesConfirmed(urls: List<String>) {
        local.update { it.copy(pageImages = null) }
        val title = uiState.value.selected?.title
        val requests = urls.mapNotNull { ImageDownloads.request(it, title) }
        if (requests.isEmpty()) return
        viewModelScope.launch {
            try {
                showMessage(BrowserMessage.Queued(enqueueDownloads(requests)))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                showMessage(BrowserMessage.ImageFailed)
            }
        }
    }

    private fun startProbe(tabId: String, url: String) {
        if (!PageProbePolicy.shouldProbe(url)) {
            updatePage(tabId) { it.copy(probe = ProbeState.IDLE) }
            return
        }
        updatePage(tabId) { it.copy(probe = ProbeState.CHECKING) }
        probeJobs[tabId] = viewModelScope.launch {
            delay(PROBE_DELAY_MS) // Si la página sigue cambiando, no se analiza cada paso.
            val result = try {
                analyzeUrl(url)
                ProbeState.SUPPORTED
            } catch (error: CancellationException) {
                throw error
            } catch (_: MediaError.DrmProtected) {
                updatePage(tabId) { it.copy(drm = true) }
                ProbeState.UNSUPPORTED
            } catch (_: Exception) {
                ProbeState.UNSUPPORTED
            }
            updatePage(tabId) { it.copy(probe = result) }
        }
    }

    // --- Descarga ------------------------------------------------------------------------------------

    /** Abre la lista de archivos detectados y consulta su tamaño sin esperar. */
    fun onDownloadFabClick() {
        local.update { it.copy(mediaSheetOpen = true) }
        sizeJob?.cancel()
        sizeJob = viewModelScope.launch { probeSizes() }
    }

    fun onMediaSheetDismissed() = local.update { it.copy(mediaSheetOpen = false) }

    /** Descargar el video o audio de la propia página: el motor la analiza y deja elegir calidad o formato. */
    fun onDownloadPage() {
        val url = uiState.value.pageCandidate ?: return
        local.update { it.copy(mediaSheetOpen = false, analysisUrl = url) }
    }

    /**
     * Descargar un archivo de la lista. Un video o audio se analiza para elegir su calidad o formato;
     * una imagen se agrega directo a la cola y la hoja sigue abierta para bajar más.
     */
    fun onMediaDownload(media: DetectedMedia) {
        if (media.kind != MediaKind.IMAGE) {
            local.update { it.copy(mediaSheetOpen = false, analysisUrl = media.url) }
            return
        }
        val request = ImageDownloads.request(media.url, uiState.value.selected?.title)
        if (request == null) {
            showMessage(BrowserMessage.ImageFailed)
            return
        }
        viewModelScope.launch {
            try {
                val count = enqueueDownloads(listOf(request))
                local.update { it.copy(queued = it.queued + media.url) }
                showMessage(BrowserMessage.Queued(count))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                showMessage(BrowserMessage.ImageFailed)
            }
        }
    }

    // Espera un momento a que la página termine de cargar antes de consultar los tamaños.
    private fun scheduleSizeProbe() {
        sizeJob?.cancel()
        sizeJob = viewModelScope.launch {
            delay(SIZE_PROBE_DELAY_MS)
            probeSizes()
        }
    }

    // Pregunta el tamaño de los archivos detectados, de a pocos a la vez. Sirve para mostrarlo y para ocultar iconos.
    private suspend fun probeSizes() {
        val urls = pages.value[store.state.value.selectedId]?.media.orEmpty().map { it.url }.filter { probed.add(it) }
        urls.chunked(SIZE_PROBE_PARALLEL).forEach { chunk ->
            val results = coroutineScope {
                chunk.map { url -> async { url to runCatching { sizeProbe.sizeOf(url) }.getOrNull() } }.awaitAll()
            }
            val found = results.mapNotNull { (url, size) -> size?.let { url to it } }
            if (found.isNotEmpty()) local.update { it.copy(sizes = it.sizes + found) }
        }
    }

    fun onAnalysisDismissed() = local.update { it.copy(analysisUrl = null) }

    fun onDownloadsQueued(count: Int) = showMessage(BrowserMessage.Queued(count))

    // --- Marcadores e historial -------------------------------------------------------------------------

    fun onToggleBookmark() {
        val tab = store.state.value.selected ?: return
        if (tab.url.isEmpty()) return
        val added = !uiState.value.isBookmarked
        viewModelScope.launch {
            bookmarkRepository.toggle(tab.url, tab.title)
            showMessage(BrowserMessage.BookmarkToggled(added))
        }
    }

    fun onRemoveBookmark(url: String) {
        viewModelScope.launch { bookmarkRepository.remove(url) }
    }

    fun onRemoveHistory(url: String) {
        viewModelScope.launch { historyRepository.remove(url) }
    }

    fun onClearHistory() {
        viewModelScope.launch {
            historyRepository.clear()
            showMessage(BrowserMessage.HistoryCleared)
        }
    }

    fun onClose() {
        viewModelScope.launch { _events.send(BrowserEvent.Close) }
    }

    fun onMessageShown() = local.update { it.copy(message = null) }

    private fun showMessage(message: BrowserMessage) = local.update { it.copy(message = message) }

    private fun updatePage(tabId: String, transform: (PageState) -> PageState) =
        pages.update { it + (tabId to transform(it[tabId] ?: PageState())) }

    private companion object {
        const val PROBE_DELAY_MS = 800L
        const val SIZE_PROBE_DELAY_MS = 1_000L
        const val SIZE_PROBE_PARALLEL = 4
    }
}
