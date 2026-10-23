package com.libredescarga.feature.browser

import com.libredescarga.feature.browser.component.addressBarContainerColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.MaterialTheme
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.core.domain.browser.MediaSniffer
import com.libredescarga.feature.browser.component.ImagesSheet
import com.libredescarga.core.domain.browser.NavigationDecision
import com.libredescarga.feature.browser.component.AddressBar
import com.libredescarga.feature.browser.component.BottomControls
import com.libredescarga.feature.browser.component.MediaListSheet
import com.libredescarga.feature.browser.component.DownloadFab
import com.libredescarga.feature.browser.component.DrmBanner
import com.libredescarga.feature.browser.component.LibrarySheet
import com.libredescarga.feature.browser.component.PageError
import com.libredescarga.feature.browser.component.StartPage
import com.libredescarga.feature.browser.component.TabsSheet
import com.libredescarga.feature.browser.component.WebViewCallbacks
import com.libredescarga.feature.browser.component.WebViewPool
import com.libredescarga.feature.browser.component.deleteIncognitoProfile
import com.libredescarga.feature.browser.component.supportsIncognitoProfile

/** Hoja de análisis para descargar (la aporta :app, así los módulos no dependen entre sí). */
typealias BrowserAnalysisSlot = @Composable (
    url: String,
    onDismiss: () -> Unit,
    onQueued: (count: Int) -> Unit,
) -> Unit

/** Navegador integrado: abre páginas, detecta videos y muestra el botón para descargarlos. */
@Composable
internal fun BrowserScreen(
    initialUrl: String?,
    analysisSheet: BrowserAnalysisSlot,
    onClose: () -> Unit,
    viewModel: BrowserViewModel = hiltViewModel<BrowserViewModel, BrowserViewModel.Factory>(
        key = "browser-${initialUrl.orEmpty()}",
    ) { it.create(initialUrl) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnClose by rememberUpdatedState(onClose)

    // Video del sitio a pantalla completa (WebChromeClient.onShowCustomView).
    var customView by remember { mutableStateOf<Pair<View, () -> Unit>?>(null) }

    // Funciones que el navegador web llama cuando algo ocurre (página cargada, video detectado...).
    val callbacks = remember(viewModel) {
        object : WebViewCallbacks {
            override fun decide(url: String) = viewModel.decide(url)
            override fun onBlocked(decision: NavigationDecision) = viewModel.onNavigationBlocked(decision)
            override fun onPageStarted(tabId: String, url: String) = viewModel.onPageStarted(tabId, url)
            override fun onPageFinished(tabId: String, url: String, title: String?) = viewModel.onPageFinished(tabId, url, title)
            override fun onUrlChanged(tabId: String, url: String) = viewModel.onUrlChanged(tabId, url)
            override fun onProgress(tabId: String, progress: Int) = viewModel.onProgress(tabId, progress)
            override fun onTitle(tabId: String, title: String) = viewModel.onTitle(tabId, title)
            override fun onNavigationState(tabId: String, canGoBack: Boolean, canGoForward: Boolean) =
                viewModel.onNavigationState(tabId, canGoBack, canGoForward)
            override fun onError(tabId: String, error: Boolean) = viewModel.onPageError(tabId, error)
            override fun onMediaSniffed(tabId: String, url: String) = viewModel.onMediaSniffed(tabId, url)
            override fun onDomResult(tabId: String, raw: String?) = viewModel.onDomResult(tabId, raw)
            override fun onDrm(tabId: String) = viewModel.onDrmDetected(tabId)
            override fun onDownload(url: String) = viewModel.onDownloadRequested(url)
            override fun onImageLongPress(tabId: String, url: String) = viewModel.onImageLongPress(url)
            override fun onShowCustomView(view: View, onHide: () -> Unit) {
                customView = view to onHide
            }
            override fun onHideCustomView() {
                customView = null
            }
        }
    }
    // Conjunto de pestañas abiertas; cada pestaña es un navegador web independiente.
    val pool = remember(callbacks) { WebViewPool(context, callbacks) }

    // Al salir: se destruyen los WebView y se borra todo lo de incógnito.
    DisposableEffect(pool) {
        onDispose {
            pool.destroyAll()
            viewModel.onLeave()
            deleteIncognitoProfile()
        }
    }

    // Se cierran los navegadores de las pestañas que ya no existen.
    val tabIds = uiState.tabs.map { it.id }.toSet()
    LaunchedEffect(tabIds) { pool.retainOnly(tabIds) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BrowserEvent.Load -> pool.peek(event.tabId)?.loadUrl(event.url)
                BrowserEvent.Close -> currentOnClose()
            }
        }
    }

    val selected = uiState.selected
    // Navegador de la pestaña que se está viendo.
    val activeWebView = selected?.let { pool.peek(it.id) }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { activeWebView?.onPause() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { activeWebView?.onResume() }

    // Barras del sistema ocultas mientras hay un video del sitio a pantalla completa.
    val activity = context.findActivity()
    DisposableEffect(customView != null) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (customView != null) controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // Los iconos de la barra de estado y de navegación se ven claros u oscuros según el color de las barras del navegador.
    val topBarColor = addressBarContainerColor(selected?.incognito == true)
    val bottomBarColor = MaterialTheme.colorScheme.surfaceContainer
    val lightStatusIcons = topBarColor.luminance() > 0.5f
    val lightNavigationIcons = bottomBarColor.luminance() > 0.5f
    DisposableEffect(activity, lightStatusIcons, lightNavigationIcons) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousStatus = controller?.isAppearanceLightStatusBars
        val previousNavigation = controller?.isAppearanceLightNavigationBars
        if (controller != null) {
            controller.isAppearanceLightStatusBars = lightStatusIcons
            controller.isAppearanceLightNavigationBars = lightNavigationIcons
        }
        onDispose {
            if (controller != null) {
                previousStatus?.let { controller.isAppearanceLightStatusBars = it }
                previousNavigation?.let { controller.isAppearanceLightNavigationBars = it }
            }
        }
    }

    BackHandler(enabled = customView != null) { customView?.second?.invoke() }
    BackHandler(enabled = customView == null && uiState.page.canGoBack && selected?.url?.isNotEmpty() == true) {
        activeWebView?.goBack()
    }

    // Avisos breves de abajo (sitio bloqueado, descargas en cola, marcador agregado...).
    val message = uiState.message?.let { browserMessage(it) }
    LaunchedEffect(uiState.message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.onMessageShown()
        }
    }

    var showTabs by rememberSaveable { mutableStateOf(false) }
    var showLibrary by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AddressBar(
                tab = selected,
                page = uiState.page,
                isBookmarked = uiState.isBookmarked,
                tabCount = uiState.tabs.size,
                onClose = viewModel::onClose,
                onSubmit = viewModel::onSubmitAddress,
                onToggleBookmark = viewModel::onToggleBookmark,
                onOpenTabs = { showTabs = true },
                onMenu = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(LibreIcons.More, contentDescription = stringResource(R.string.browser_menu))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browser_new_tab)) },
                                leadingIcon = { Icon(LibreIcons.Add, contentDescription = null) },
                                onClick = { menuOpen = false; viewModel.onNewTab() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browser_new_incognito_tab)) },
                                leadingIcon = { Icon(LibreIcons.Lock, contentDescription = null) },
                                onClick = { menuOpen = false; viewModel.onNewTab(incognito = true) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browser_menu_images)) },
                                leadingIcon = { Icon(LibreIcons.Library, contentDescription = null) },
                                enabled = selected?.url?.isNotEmpty() == true,
                                onClick = {
                                    menuOpen = false
                                    // Se lee la página en este momento, así incluye lo que se cargó al desplazarse.
                                    activeWebView?.evaluateJavascript(MediaSniffer.IMAGES_SCRIPT, viewModel::onPageImagesFound)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browser_bookmarks_history)) },
                                leadingIcon = { Icon(LibreIcons.History, contentDescription = null) },
                                onClick = { menuOpen = false; showLibrary = true },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            BottomControls(
                page = uiState.page,
                canNavigate = selected?.url?.isNotEmpty() == true,
                onBack = { activeWebView?.goBack() },
                onForward = { activeWebView?.goForward() },
                onReloadOrStop = { activeWebView?.let { if (uiState.page.loading) it.stopLoading() else it.reload() } },
                onShare = { selected?.let { context.shareLink(it.url, it.title) } },
                onHome = { viewModel.onNewTab() },
            )
        },
        floatingActionButton = {
            AnimatedVisibility(visible = uiState.showDownloadFab, enter = scaleIn(), exit = scaleOut()) {
                DownloadFab(
                    count = uiState.downloadCount,
                    onClick = {
                        // Antes de mostrar la lista se vuelve a revisar la página, para incluir lo último que cargó.
                        val tabId = selected?.id
                        if (activeWebView != null && tabId != null) {
                            activeWebView.evaluateJavascript(MediaSniffer.DOM_SCRIPT) { raw ->
                                viewModel.onDomResult(tabId, raw)
                                viewModel.onDownloadFabClick()
                            }
                        } else {
                            viewModel.onDownloadFabClick()
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (uiState.page.drm) DrmBanner()
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                if (selected == null || selected.url.isEmpty()) {
                    StartPage(
                        incognito = selected?.incognito == true,
                        bookmarks = uiState.bookmarks,
                        incognitoIsolated = supportsIncognitoProfile(),
                        onOpen = viewModel::onOpenUrl,
                    )
                } else {
                    androidx.compose.runtime.key(selected.id) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { pool.getOrCreate(selected) },
                            onRelease = { (it.parent as? ViewGroup)?.removeView(it) },
                        )
                    }
                    if (uiState.page.error) PageError(onRetry = { activeWebView?.reload(); viewModel.onPageError(selected.id, false) })
                }
            }
        }
    }

    customView?.let { (video, _) ->
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { video.apply { (parent as? ViewGroup)?.removeView(this) } },
            )
        }
    }

    if (showTabs) {
        TabsSheet(
            tabs = uiState.tabs,
            selectedId = selected?.id,
            onSelect = viewModel::onSelectTab,
            onClose = viewModel::onCloseTab,
            onNewTab = { viewModel.onNewTab(); showTabs = false },
            onNewIncognitoTab = { viewModel.onNewTab(incognito = true); showTabs = false },
            onDismiss = { showTabs = false },
        )
    }
    if (showLibrary) {
        LibrarySheet(
            bookmarks = uiState.bookmarks,
            history = uiState.history,
            onOpen = { viewModel.onOpenUrl(it); showLibrary = false },
            onRemoveBookmark = viewModel::onRemoveBookmark,
            onRemoveHistory = viewModel::onRemoveHistory,
            onClearHistory = viewModel::onClearHistory,
            onDismiss = { showLibrary = false },
        )
    }
    // Lista de lo que se puede descargar de esta página, cada elemento con su botón.
    if (uiState.mediaSheetOpen) {
        MediaListSheet(
            pageUrl = uiState.pageCandidate,
            media = uiState.media,
            sizes = uiState.sizes,
            queued = uiState.queuedUrls,
            onDownloadPage = viewModel::onDownloadPage,
            onDownload = viewModel::onMediaDownload,
            onDismiss = viewModel::onMediaSheetDismissed,
        )
    }
    uiState.analysisUrl?.let { url ->
        analysisSheet(url, viewModel::onAnalysisDismissed, viewModel::onDownloadsQueued)
    }
    // Elegir varias imágenes de la página.
    uiState.pageImages?.let { images ->
        ImagesSheet(images, onDownload = viewModel::onPageImagesConfirmed, onDismiss = viewModel::onPageImagesDismissed)
    }
    // Confirmación para descargar la imagen que el usuario mantuvo presionada.
    uiState.imageUrl?.let { url ->
        AlertDialog(
            onDismissRequest = viewModel::onImageDismissed,
            title = { Text(stringResource(R.string.browser_image_title)) },
            text = {
                Text(stringResource(R.string.browser_image_message, android.net.Uri.parse(url).host.orEmpty()))
            },
            confirmButton = {
                TextButton(onClick = viewModel::onImageConfirmed) { Text(stringResource(R.string.browser_image_download)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onImageDismissed) { Text(stringResource(R.string.browser_image_cancel)) }
            },
        )
    }
}

// Texto del aviso que corresponde a cada resultado.
@Composable
private fun browserMessage(message: BrowserMessage): String = when (message) {
    BrowserMessage.BlockedScheme -> stringResource(R.string.browser_message_blocked_scheme)
    is BrowserMessage.BlockedDomain -> stringResource(R.string.browser_message_blocked_domain, message.domain)
    is BrowserMessage.Queued -> pluralStringResource(R.plurals.browser_message_queued, message.count, message.count)
    is BrowserMessage.BookmarkToggled ->
        stringResource(if (message.added) R.string.browser_message_bookmark_added else R.string.browser_message_bookmark_removed)
    BrowserMessage.HistoryCleared -> stringResource(R.string.browser_message_history_cleared)
    BrowserMessage.ImageFailed -> stringResource(R.string.browser_message_image_failed)
    BrowserMessage.NoImages -> stringResource(R.string.browser_message_no_images)
}

// Abre el menú de Android para compartir el enlace de la página.
private fun Context.shareLink(url: String, title: String) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, url)
        .putExtra(Intent.EXTRA_SUBJECT, title)
    try {
        startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // Sin apps para compartir: no hay nada más que hacer.
    }
}

private tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
