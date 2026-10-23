package com.libredescarga.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.libredescarga.core.domain.repository.IncomingLinkRepository
import com.libredescarga.core.domain.repository.SearchHistoryRepository
import com.libredescarga.core.domain.repository.SiteRepository
import com.libredescarga.core.domain.repository.UserPreferencesRepository
import com.libredescarga.core.domain.usecase.CreateQuickDownloadRequestUseCase
import com.libredescarga.core.domain.usecase.EnqueueDownloadsUseCase
import com.libredescarga.core.domain.usecase.ImageDownloads
import com.libredescarga.core.domain.usecase.ResolveSearchInputUseCase
import com.libredescarga.core.domain.usecase.SearchInput
import com.libredescarga.core.domain.usecase.SearchMediaUseCase
import com.libredescarga.core.model.AnalisisPerf
import com.libredescarga.core.model.AudioFormat
import com.libredescarga.core.model.MediaEntry
import com.libredescarga.core.model.MediaError
import com.libredescarga.core.model.SearchProvider
import com.libredescarga.core.model.Site
import com.libredescarga.core.model.VideoQuality
import com.libredescarga.core.model.webSearchUrl
import com.libredescarga.core.security.UrlValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Lógica de la pantalla de inicio: búsqueda de videos, sugerencias del historial, enlaces del portapapeles,
 * sitios favoritos y descargas rápidas.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    // Decide si lo escrito es un enlace, palabras para buscar o algo no permitido.
    private val resolveInput: ResolveSearchInputUseCase,
    // Busca videos por palabras en el sitio elegido.
    private val searchMedia: SearchMediaUseCase,
    // Historial de búsquedas.
    private val history: SearchHistoryRepository,
    // Sitios de la pantalla de inicio.
    private val siteRepository: SiteRepository,
    // Preferencias del usuario.
    private val preferences: UserPreferencesRepository,
    // Enlaces recibidos desde otras apps con "Compartir".
    private val incomingLinks: IncomingLinkRepository,
    // Arma una descarga directa con la calidad por defecto.
    private val createQuickRequest: CreateQuickDownloadRequestUseCase,
    // Agrega descargas a la cola.
    private val enqueueDownloads: EnqueueDownloadsUseCase,
) : ViewModel() {

    // Parte del estado que solo vive en esta pantalla (texto escrito, búsqueda, avisos).
    private val local = MutableStateFlow(HomeUiState())
    // Búsqueda en curso; se cancela si el usuario hace otra.
    private var searchJob: Job? = null

    /** Enlaces del portapapeles ya ofrecidos (no se vuelven a sugerir en esta sesión). */
    private val offeredClipboardUrls = mutableSetOf<String>()

    // Eventos de una sola vez, como abrir un sitio en el navegador.
    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    // Sugerencias del historial según lo que se escribe; espera 150 ms para no buscar en cada letra.
    private val suggestions = local
        .map { it.query.trim() }
        .distinctUntilChanged()
        .debounce(SUGGESTION_DEBOUNCE_MS)
        .flatMapLatest { history.suggestions(it, MAX_SUGGESTIONS) }
        // Sin esperar al primer "debounce": la pantalla se muestra de inmediato.
        .onStart { emit(emptyList()) }

    // Estado completo de la pantalla: mezcla lo local con las sugerencias, los sitios y las preferencias.
    val uiState: StateFlow<HomeUiState> = combine(
        local,
        suggestions,
        siteRepository.sites,
        preferences.settings,
    ) { state, suggestions, sites, settings ->
        state.copy(
            suggestions = suggestions.filterNot { it == state.query.trim() },
            sites = sites.filter(Site::enabled),
            clipboardDetectionEnabled = settings.clipboardDetection,
            quickDownloadAvailable = settings.defaultVideoQuality != VideoQuality.ASK ||
                settings.defaultAudioFormat != AudioFormat.ASK,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        // "Compartir con LibreDescarga": abre directamente la hoja de análisis.
        viewModelScope.launch {
            incomingLinks.pendingLink.filterNotNull().collect { link ->
                openAnalysis(link)
                incomingLinks.consume()
            }
        }
    }

    // --- Búsqueda universal ------------------------------------------------------------

    // El usuario escribe en el buscador.
    fun onQueryChange(query: String) = local.update { it.copy(query = query) }

    // El usuario pulsa Buscar.
    fun onSubmit() = submit(local.value.query)

    // Elige una sugerencia del historial y la busca.
    fun onSuggestionClick(suggestion: String) {
        local.update { it.copy(query = suggestion) }
        submit(suggestion)
    }

    // Borra una sugerencia del historial.
    fun onRemoveSuggestion(suggestion: String) {
        viewModelScope.launch { history.remove(suggestion) }
    }

    // Borra todo el historial de búsquedas.
    fun onClearHistory() {
        viewModelScope.launch { history.clear() }
    }

    // Cambia el sitio de búsqueda y repite la búsqueda actual en él.
    fun onProviderSelected(provider: SearchProvider) {
        local.update { it.copy(provider = provider) }
        val search = local.value.search
        if (search !is SearchState.Idle) runSearch(search.queryOrNull() ?: return)
    }

    /** Las categorías abren una búsqueda en el sitio elegido, dentro del navegador integrado. */
    fun onCategoryClick(query: String) {
        val url = local.value.provider.webSearchUrl(query)
        viewModelScope.launch { _events.send(HomeEvent.OpenSite(url)) }
    }

    // Cierra los resultados y vuelve al inicio.
    fun onCloseResults() {
        searchJob?.cancel()
        local.update { it.copy(search = SearchState.Idle, query = "") }
    }

    // Repite la última búsqueda.
    fun onRetrySearch() {
        local.value.search.queryOrNull()?.let(::runSearch)
    }

    // Según lo escrito: abre el análisis si es un enlace, busca si son palabras y avisa si el enlace no se permite.
    private fun submit(raw: String) {
        when (val input = resolveInput(raw)) {
            SearchInput.Empty -> Unit
            SearchInput.Forbidden -> showMessage(HomeMessage.ForbiddenLink)
            is SearchInput.Link -> openAnalysis(input.url)
            is SearchInput.Keywords -> runSearch(input.text)
        }
    }

    // Busca y guarda el resultado; cancela cualquier búsqueda anterior.
    private fun runSearch(query: String) {
        searchJob?.cancel()
        local.update { it.copy(search = SearchState.Loading(query)) }
        searchJob = viewModelScope.launch {
            val result = try {
                SearchState.Results(query, searchMedia(query, local.value.provider))
            } catch (error: CancellationException) {
                throw error
            } catch (error: MediaError) {
                SearchState.Failed(query, error)
            } catch (error: Exception) {
                SearchState.Failed(query, MediaError.Unknown(error.message))
            }
            local.update { it.copy(search = result) }
        }
    }

    // --- Resultados y análisis ---------------------------------------------------------

    // Toca un resultado: abre el análisis de ese video.
    fun onResultClick(entry: MediaEntry) = openAnalysis(entry.url)

    // Descarga directa con la calidad por defecto; si no hay una definida, abre el análisis para elegir.
    fun onQuickDownload(entry: MediaEntry) {
        viewModelScope.launch {
            val request = createQuickRequest(entry) ?: run {
                openAnalysis(entry.url)
                return@launch
            }
            try {
                showMessage(HomeMessage.Queued(enqueueDownloads(listOf(request))))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showMessage(HomeMessage.Error(error as? MediaError ?: MediaError.Unknown(error.message)))
            }
        }
    }

    // Se cerró la hoja de análisis.
    fun onAnalysisDismissed() = local.update { it.copy(analysisUrl = null) }

    // Avisa cuántas descargas se agregaron a la cola.
    fun onDownloadsQueued(count: Int) = showMessage(HomeMessage.Queued(count))

    // Abre la hoja que analiza el enlace. Si el enlace es directamente una imagen, se descarga sin analizar.
    private fun openAnalysis(url: String) {
        AnalisisPerf.mark("entrada: Inicio abre el análisis (pegado, portapapeles, compartido o resultado)", AnalisisPerf.hostOf(url))
        val imageRequest = if (ImageDownloads.isImageUrl(url)) ImageDownloads.request(url) else null
        if (imageRequest == null) {
            local.update { it.copy(analysisUrl = url) }
            return
        }
        viewModelScope.launch {
            try {
                showMessage(HomeMessage.Queued(enqueueDownloads(listOf(imageRequest))))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showMessage(HomeMessage.Error(error as? MediaError ?: MediaError.Unknown(error.message)))
            }
        }
    }

    // --- Portapapeles ------------------------------------------------------------------

    /** Texto leído del portapapeles al volver a la app. Solo se ofrece una vez por enlace. */
    fun onClipboardText(text: String) {
        val url = UrlValidator.extractFirstUrl(text)
            ?.let { (UrlValidator.validate(it) as? UrlValidator.Result.Valid)?.url }
            ?: return
        viewModelScope.launch {
            if (!preferences.settings.first().clipboardDetection) return@launch
            if (!offeredClipboardUrls.add(url)) return@launch
            local.update { it.copy(clipboardUrl = url) }
        }
    }

    // Analiza el enlace que se encontró en el portapapeles.
    fun onClipboardAnalyze() {
        val url = local.value.clipboardUrl ?: return
        local.update { it.copy(clipboardUrl = null) }
        openAnalysis(url)
    }

    // Descarta la sugerencia del portapapeles.
    fun onClipboardDismiss() = local.update { it.copy(clipboardUrl = null) }

    // --- Sitios ------------------------------------------------------------------------

    // Abre el sitio en el navegador integrado.
    fun onSiteClick(site: Site) {
        viewModelScope.launch { _events.send(HomeEvent.OpenSite(site.url)) }
    }

    // Agrega un sitio propio; avisa si la dirección no es válida.
    fun onAddSite(name: String, url: String) {
        viewModelScope.launch {
            try {
                siteRepository.addCustomSite(name, url)
            } catch (_: MediaError) {
                showMessage(HomeMessage.InvalidSite)
            }
        }
    }

    // Quita un sitio de la pantalla de inicio.
    fun onRemoveSite(site: Site) {
        viewModelScope.launch { siteRepository.removeSite(site.id) }
    }

    // Avisa que no se pudo abrir el navegador.
    fun onBrowserUnavailable() = showMessage(HomeMessage.NoBrowser)

    // --- Mensajes ----------------------------------------------------------------------

    // El aviso ya se mostró; se quita para no repetirlo.
    fun onMessageShown() = local.update { it.copy(message = null) }

    // Pide mostrar un aviso breve.
    private fun showMessage(message: HomeMessage) = local.update { it.copy(message = message) }

    // Texto de la búsqueda actual; null si no hay ninguna.
    private fun SearchState.queryOrNull(): String? = when (this) {
        SearchState.Idle -> null
        is SearchState.Loading -> query
        is SearchState.Results -> query
        is SearchState.Failed -> query
    }

    private companion object {
        // Espera antes de pedir sugerencias, en milisegundos.
        const val SUGGESTION_DEBOUNCE_MS = 150L
        // Máximo de sugerencias que se muestran.
        const val MAX_SUGGESTIONS = 6
    }
}
