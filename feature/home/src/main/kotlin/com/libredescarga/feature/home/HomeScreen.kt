package com.libredescarga.feature.home

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.libredescarga.core.designsystem.component.LibreLogo
import com.libredescarga.core.designsystem.component.rememberAppName
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.core.model.MediaEntry
import com.libredescarga.core.model.SearchProvider
import com.libredescarga.core.model.Site
import com.libredescarga.core.ui.userMessage
import com.libredescarga.feature.home.component.AddSiteDialog
import com.libredescarga.feature.home.component.CategoryRow
import com.libredescarga.feature.home.component.ClipboardCard
import com.libredescarga.feature.home.component.HomeSearchField
import com.libredescarga.feature.home.component.ProviderChips
import com.libredescarga.feature.home.component.SearchResultsHeader
import com.libredescarga.feature.home.component.SearchResultsState
import com.libredescarga.feature.home.component.SearchResultRow
import com.libredescarga.feature.home.component.SectionTitle
import com.libredescarga.feature.home.component.SitesGrid
import com.libredescarga.feature.home.component.SuggestionsCard
import com.libredescarga.feature.home.navigation.AnalysisSheetSlot

/** Pantalla de inicio: buscador, sitios favoritos, categorías y sugerencia del portapapeles. */
@Composable
internal fun HomeScreen(
    analysisSheet: AnalysisSheetSlot,
    onOpenSite: (String) -> Boolean,
    onOpenBrowser: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // Siempre usa la versión más reciente de la función que abre sitios.
    val currentOnOpenSite by rememberUpdatedState(onOpenSite)

    // Escucha el evento de abrir un sitio; si no se puede, avisa.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.OpenSite -> if (!currentOnOpenSite(event.url)) viewModel.onBrowserUnavailable()
            }
        }
    }

    // Avisos breves de abajo (descargas en cola, enlace no permitido...).
    val messageText = uiState.message?.let { messageText(it) }
    LaunchedEffect(uiState.message) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.onMessageShown()
        }
    }

    // Revisa el portapapeles al volver a la app, si el usuario lo permite en Ajustes.
    ClipboardEffect(enabled = uiState.clipboardDetectionEnabled, onText = viewModel::onClipboardText)

    // Dibuja la pantalla y le pasa cada acción al ViewModel.
    HomeContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onQueryChange = viewModel::onQueryChange,
        onSubmit = viewModel::onSubmit,
        onPaste = { text ->
            viewModel.onQueryChange(text)
            viewModel.onSubmit()
        },
        onSuggestionClick = viewModel::onSuggestionClick,
        onRemoveSuggestion = viewModel::onRemoveSuggestion,
        onClearHistory = viewModel::onClearHistory,
        onProviderSelected = viewModel::onProviderSelected,
        onCategoryClick = viewModel::onCategoryClick,
        onCloseResults = viewModel::onCloseResults,
        onRetrySearch = viewModel::onRetrySearch,
        onResultClick = viewModel::onResultClick,
        onQuickDownload = viewModel::onQuickDownload,
        onClipboardAnalyze = viewModel::onClipboardAnalyze,
        onClipboardDismiss = viewModel::onClipboardDismiss,
        onSiteClick = viewModel::onSiteClick,
        onAddSite = viewModel::onAddSite,
        onRemoveSite = viewModel::onRemoveSite,
        onOpenBrowser = onOpenBrowser,
    )

    // Cuando hay un enlace para analizar, muestra la hoja con las opciones de descarga.
    uiState.analysisUrl?.let { url ->
        analysisSheet(url, viewModel::onAnalysisDismissed, viewModel::onDownloadsQueued)
    }
}

/** Dibujo de la pantalla de inicio. */
@Composable
internal fun HomeContent(
    uiState: HomeUiState,
    snackbarHostState: SnackbarHostState,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onPaste: (String) -> Unit,
    onSuggestionClick: (String) -> Unit,
    onRemoveSuggestion: (String) -> Unit,
    onClearHistory: () -> Unit,
    onProviderSelected: (SearchProvider) -> Unit,
    onCategoryClick: (String) -> Unit,
    onCloseResults: () -> Unit,
    onRetrySearch: () -> Unit,
    onResultClick: (MediaEntry) -> Unit,
    onQuickDownload: (MediaEntry) -> Unit,
    onClipboardAnalyze: () -> Unit,
    onClipboardDismiss: () -> Unit,
    onSiteClick: (Site) -> Unit,
    onAddSite: (name: String, url: String) -> Unit,
    onRemoveSite: (Site) -> Unit,
    onOpenBrowser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Indica si el cursor está en el buscador (solo entonces se muestran las sugerencias).
    var searchFocused by remember { mutableStateOf(false) }
    // Indica si el diálogo para agregar un sitio está abierto.
    var showAddSite by rememberSaveable { mutableStateOf(false) }
    val search = uiState.search

    // Con resultados abiertos, Atrás los cierra en vez de salir de la pantalla.
    BackHandler(enabled = search !is SearchState.Idle, onBack = onCloseResults)

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "header") { HomeHeader(onOpenBrowser) }

                item(key = "search") {
                    HomeSearchField(
                        query = uiState.query,
                        onQueryChange = onQueryChange,
                        onSubmit = onSubmit,
                        onPaste = onPaste,
                        onFocusChange = { searchFocused = it },
                    )
                }

                if (searchFocused && uiState.suggestions.isNotEmpty()) {
                    item(key = "suggestions") {
                        SuggestionsCard(
                            suggestions = uiState.suggestions,
                            onClick = onSuggestionClick,
                            onRemove = onRemoveSuggestion,
                            onClearHistory = onClearHistory,
                        )
                    }
                }

                item(key = "providers") {
                    ProviderChips(selected = uiState.provider, onSelected = onProviderSelected)
                }

                uiState.clipboardUrl?.let { url ->
                    item(key = "clipboard") {
                        ClipboardCard(url = url, onAnalyze = onClipboardAnalyze, onDismiss = onClipboardDismiss)
                    }
                }

                // Sin búsqueda: categorías y sitios. Con búsqueda: resultados.
                if (search is SearchState.Idle) {
                    item(key = "categories-title") { SectionTitle(stringResource(R.string.home_categories)) }
                    item(key = "categories") {
                        CategoryRow(onCategoryClick = onCategoryClick)
                    }
                    item(key = "sites-title") { SectionTitle(stringResource(R.string.home_sites)) }
                    item(key = "sites") {
                        SitesGrid(
                            sites = uiState.sites,
                            onSiteClick = onSiteClick,
                            onRemoveSite = onRemoveSite,
                            onAddSite = { showAddSite = true },
                        )
                    }
                } else {
                    item(key = "results-header") {
                        SearchResultsHeader(search = search, provider = uiState.provider, onClose = onCloseResults)
                    }
                    if (search is SearchState.Results && search.items.isNotEmpty()) {
                        items(search.items, key = { "r-${it.id}-${it.url}" }) { entry ->
                            SearchResultRow(
                                entry = entry,
                                showQuickDownload = uiState.quickDownloadAvailable,
                                onClick = { onResultClick(entry) },
                                onQuickDownload = { onQuickDownload(entry) },
                            )
                        }
                    } else {
                        item(key = "results-state") {
                            SearchResultsState(search = search, onRetry = onRetrySearch)
                        }
                    }
                }
            }
        }
    }

    if (showAddSite) {
        AddSiteDialog(
            onConfirm = { name, url ->
                onAddSite(name, url)
                showAddSite = false
            },
            onDismiss = { showAddSite = false },
        )
    }
}

/** Cabecera con el logotipo, el nombre de la app y el botón del navegador. */
@Composable
private fun HomeHeader(onOpenBrowser: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LibreLogo(size = 36.dp)
        Text(text = rememberAppName(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onOpenBrowser) {
            Icon(LibreIcons.Internet, contentDescription = stringResource(R.string.home_open_browser))
        }
    }
}

/** Texto del aviso que corresponde a cada resultado. */
@Composable
private fun messageText(message: HomeMessage): String = when (message) {
    is HomeMessage.Queued -> pluralStringResource(R.plurals.home_downloads_queued, message.count, message.count)
    HomeMessage.ForbiddenLink -> stringResource(R.string.home_forbidden_link)
    HomeMessage.InvalidSite -> stringResource(R.string.home_invalid_site)
    HomeMessage.NoBrowser -> stringResource(R.string.home_no_browser)
    is HomeMessage.Error -> message.error.userMessage()
}

/**
 * Lee el portapapeles cuando la ventana tiene el foco (Android 10+ no lo permite
 * antes). Solo se lee si el usuario no desactivó la detección en Ajustes.
 */
@Composable
private fun ClipboardEffect(enabled: Boolean, onText: (String) -> Unit) {
    if (!enabled) return
    val context = LocalContext.current
    val focused = LocalWindowInfo.current.isWindowFocused
    val currentOnText by rememberUpdatedState(onText)
    LaunchedEffect(focused) {
        if (focused) context.readClipboardText()?.let(currentOnText)
    }
}

/** Lee el texto copiado (si lo hay) del portapapeles; null si está vacío o no es texto. */
internal fun Context.readClipboardText(): String? {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return null
    val description = clipboard.primaryClipDescription ?: return null
    val isText = description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
        description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML) ||
        description.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST)
    if (!isText) return null
    return clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
}
