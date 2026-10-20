package com.libredescarga.feature.downloads

import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import com.libredescarga.feature.downloads.component.ClearDownloadsDialog
import com.libredescarga.core.model.DownloadStatus
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.core.model.DownloadTask
import com.libredescarga.core.model.VaultItem
import com.libredescarga.core.ui.EmptyState
import com.libredescarga.core.ui.formatBytes
import com.libredescarga.feature.downloads.component.DownloadItem
import com.libredescarga.feature.downloads.component.DownloadSettingsSheet
import com.libredescarga.feature.downloads.component.RemoveDownloadDialog
import kotlinx.coroutines.launch

/** Pantalla "Descargas": conecta con su ViewModel y ejecuta las acciones (abrir, compartir, pausar, cancelar...). */
@Composable
internal fun DownloadsScreen(
    onOpenVideo: (uri: String, title: String, durationMs: Long) -> Unit,
    onOpenImages: (uris: List<String>, titles: List<String>, index: Int) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Textos de aviso que se usan más abajo.
    val noAppMessage = stringResource(R.string.downloads_no_app)
    val inVaultMessage = stringResource(R.string.downloads_in_vault)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshStorage() }

    // Los videos y las imágenes se abren con las pantallas de la propia app.
    val currentOnOpenVideo by rememberUpdatedState(onOpenVideo)
    val currentOnOpenImages by rememberUpdatedState(onOpenImages)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DownloadsEvent.OpenVideo -> currentOnOpenVideo(event.uri, event.title, event.durationMs)
                is DownloadsEvent.OpenImages -> currentOnOpenImages(event.uris, event.titles, event.index)
                DownloadsEvent.InVault -> snackbarHostState.showSnackbar(inVaultMessage)
            }
        }
    }

    // Ejecuta una acción y, si falla, avisa al usuario (por ejemplo, si no hay una app que abra el archivo).
    fun runOrWarn(action: () -> Boolean) {
        if (!action()) scope.launch { snackbarHostState.showSnackbar(noAppMessage) }
    }

    DownloadsContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onTabSelected = viewModel::onTabSelected,
        onPause = viewModel::onPause,
        onResume = viewModel::onResume,
        onCancel = viewModel::onCancel,
        onRetry = viewModel::onRetry,
        onPrioritize = viewModel::onPrioritize,
        onRemove = viewModel::onRemove,
        onClearAll = viewModel::onClearAll,
        // Lo que se descargó directo a la carpeta privada solo se abre desde ahí.
        onOpen = viewModel::onOpen,
        onShare = { task ->
            if (VaultItem.isVaultUri(task.outputUri)) {
                scope.launch { snackbarHostState.showSnackbar(inVaultMessage) }
            } else {
                runOrWarn { context.shareDownload(task) }
            }
        },
        onPauseAll = viewModel::onPauseAll,
        onResumeAll = viewModel::onResumeAll,
        onMaxConcurrentChange = viewModel::onMaxConcurrentChange,
        onFragmentsChange = viewModel::onFragmentsChange,
        onWifiOnlyChange = viewModel::onWifiOnlyChange,
        onSpeedLimitChange = viewModel::onSpeedLimitChange,
        onFolderChange = viewModel::onFolderChange,
    )
}

/** Dibujo de la pantalla de descargas: pestañas, secciones, tarjetas de cada descarga y diálogos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadsContent(
    uiState: DownloadsUiState,
    snackbarHostState: SnackbarHostState,
    onTabSelected: (DownloadsTab) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
    onPrioritize: (String) -> Unit,
    onRemove: (id: String, deleteFile: Boolean) -> Unit,
    onClearAll: (tab: DownloadsTab, deleteFiles: Boolean) -> Unit,
    onOpen: (DownloadTask) -> Unit,
    onShare: (DownloadTask) -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onMaxConcurrentChange: (Int) -> Unit,
    onFragmentsChange: (Int) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onSpeedLimitChange: (Int) -> Unit,
    onFolderChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var clearTab by rememberSaveable { mutableStateOf<DownloadsTab?>(null) }
    var taskToRemove by remember { mutableStateOf<DownloadTask?>(null) }
    // Descargas agrupadas en tres listas: en curso, completadas y fallidas.
    val sections = uiState.sections
    // Descargas que corresponden a la pestaña elegida.
    val tasks = when (uiState.selectedTab) {
        DownloadsTab.ACTIVE -> sections.active
        DownloadsTab.COMPLETED -> sections.completed
        DownloadsTab.FAILED -> sections.failed
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.downloads_title)) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(LibreIcons.Tune, contentDescription = stringResource(R.string.downloads_settings))
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(LibreIcons.More, contentDescription = stringResource(R.string.downloads_more))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.downloads_pause_all)) },
                                leadingIcon = { Icon(LibreIcons.Pause, contentDescription = null) },
                                enabled = uiState.hasRunning,
                                onClick = {
                                    onPauseAll()
                                    showMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.downloads_resume_all)) },
                                leadingIcon = { Icon(LibreIcons.Play, contentDescription = null) },
                                enabled = uiState.hasPaused,
                                onClick = {
                                    onResumeAll()
                                    showMenu = false
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.downloads_clear_completed)) },
                                leadingIcon = { Icon(LibreIcons.Delete, contentDescription = null) },
                                enabled = sections.completed.isNotEmpty(),
                                onClick = {
                                    showMenu = false
                                    clearTab = DownloadsTab.COMPLETED
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.downloads_clear_failed)) },
                                leadingIcon = { Icon(LibreIcons.Delete, contentDescription = null) },
                                enabled = sections.failed.isNotEmpty(),
                                onClick = {
                                    showMenu = false
                                    clearTab = DownloadsTab.FAILED
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PrimaryTabRow(selectedTabIndex = uiState.selectedTab.ordinal) {
                DownloadsTab.entries.forEach { tab ->
                    val count = when (tab) {
                        DownloadsTab.ACTIVE -> sections.active.size
                        DownloadsTab.COMPLETED -> sections.completed.size
                        DownloadsTab.FAILED -> sections.failed.size
                    }
                    Tab(
                        selected = tab == uiState.selectedTab,
                        onClick = { onTabSelected(tab) },
                        text = { Text(stringResource(tab.labelRes, count)) },
                    )
                }
            }

            if (tasks.isEmpty() && !uiState.lowStorage && !uiState.folderUnavailable) {
                EmptyState(
                    icon = LibreIcons.Downloads,
                    title = stringResource(uiState.selectedTab.emptyTitleRes),
                    message = stringResource(uiState.selectedTab.emptyMessageRes),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (uiState.lowStorage) {
                        item(key = "low-storage") {
                            WarningCard(
                                stringResource(
                                    R.string.downloads_low_storage,
                                    formatBytes(uiState.availableBytes ?: 0),
                                ),
                            )
                        }
                    }
                    if (uiState.folderUnavailable) {
                        item(key = "folder-unavailable") {
                            WarningCard(stringResource(R.string.downloads_folder_unavailable))
                        }
                    }
                    // Botón para limpiar de una vez toda la pestaña (completadas o fallidas).
                    if (uiState.selectedTab != DownloadsTab.ACTIVE && tasks.isNotEmpty()) {
                        item(key = "clear-all") {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { clearTab = uiState.selectedTab }) {
                                    Icon(LibreIcons.Delete, contentDescription = null)
                                    Text(
                                        text = stringResource(R.string.downloads_clear_button, tasks.size),
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                    items(tasks, key = { it.id }) { task ->
                        DownloadItem(
                            task = task,
                            wifiOnly = uiState.settings.wifiOnly,
                            isFirstInQueue = task.id == uiState.firstQueuedId,
                            onPause = { onPause(task.id) },
                            onResume = { onResume(task.id) },
                            onCancel = { onCancel(task.id) },
                            onRetry = { onRetry(task.id) },
                            onPrioritize = { onPrioritize(task.id) },
                            onOpen = { onOpen(task) },
                            onShare = { onShare(task) },
                            onRemove = { taskToRemove = task },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    // Confirmación para limpiar todas las descargas completadas o fallidas, con la casilla de borrar archivos.
    clearTab?.let { tab ->
        val toClear = if (tab == DownloadsTab.FAILED) sections.failed else sections.completed
        if (toClear.isNotEmpty()) {
            ClearDownloadsDialog(
                tab = tab,
                count = toClear.size,
                deletableFiles = toClear.count {
                    it.status == DownloadStatus.COMPLETED && it.outputUri != null && !VaultItem.isVaultUri(it.outputUri)
                },
                onConfirm = { deleteFiles ->
                    onClearAll(tab, deleteFiles)
                    clearTab = null
                },
                onDismiss = { clearTab = null },
            )
        }
    }

    taskToRemove?.let { task ->
        RemoveDownloadDialog(
            task = task,
            onConfirm = { deleteFile ->
                onRemove(task.id, deleteFile)
                taskToRemove = null
            },
            onDismiss = { taskToRemove = null },
        )
    }

    if (showSettings) {
        DownloadSettingsSheet(
            settings = uiState.settings,
            folderName = uiState.folderName,
            onDismiss = { showSettings = false },
            onMaxConcurrentChange = onMaxConcurrentChange,
            onFragmentsChange = onFragmentsChange,
            onWifiOnlyChange = onWifiOnlyChange,
            onSpeedLimitChange = onSpeedLimitChange,
            onFolderChange = onFolderChange,
        )
    }
}

/** Tarjeta de aviso, por ejemplo cuando la carpeta de descargas no está disponible. */
@Composable
private fun WarningCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(LibreIcons.Error, contentDescription = null)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// Nombre de cada pestaña.
private val DownloadsTab.labelRes: Int
    get() = when (this) {
        DownloadsTab.ACTIVE -> R.string.downloads_tab_active
        DownloadsTab.COMPLETED -> R.string.downloads_tab_completed
        DownloadsTab.FAILED -> R.string.downloads_tab_failed
    }

// Título que se muestra cuando una pestaña no tiene descargas.
private val DownloadsTab.emptyTitleRes: Int
    get() = when (this) {
        DownloadsTab.ACTIVE -> R.string.downloads_empty_active_title
        DownloadsTab.COMPLETED -> R.string.downloads_empty_completed_title
        DownloadsTab.FAILED -> R.string.downloads_empty_failed_title
    }

// Explicación que se muestra cuando una pestaña no tiene descargas.
private val DownloadsTab.emptyMessageRes: Int
    get() = when (this) {
        DownloadsTab.ACTIVE -> R.string.downloads_empty_active_message
        DownloadsTab.COMPLETED -> R.string.downloads_empty_completed_message
        DownloadsTab.FAILED -> R.string.downloads_empty_failed_message
    }

// --- Compartir (abrir se hace con el reproductor y el visor de la propia app) ---------------

// Abre el menú de Android para compartir el archivo descargado.
private fun Context.shareDownload(task: DownloadTask): Boolean {
    val uri = task.outputUri?.let(Uri::parse) ?: return false
    val intent = Intent(Intent.ACTION_SEND)
        .setType(contentResolver.getType(uri) ?: "*/*")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return startSafely(Intent.createChooser(intent, task.request.title))
}

// Lanza la pantalla de otra app sin que la app se cierre si no existe.
private fun Context.startSafely(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
}
