package com.libredescarga.feature.library.duplicates

import android.app.Activity
import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.core.model.DuplicateGroup
import com.libredescarga.core.model.DuplicateScanState
import com.libredescarga.core.model.LibraryMedia
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.ui.EmptyState
import com.libredescarga.core.ui.LocalMediaThumbnail
import com.libredescarga.core.ui.formatBytes
import com.libredescarga.feature.library.R
import java.text.DateFormat
import java.util.Date

/**
 * Pantalla "Archivos duplicados": agrupa los videos, la música y las imágenes idénticos.
 * El usuario decide qué copias eliminar; la app nunca borra nada por su cuenta.
 */
@Composable
internal fun DuplicatesScreen(
    onBack: () -> Unit,
    viewModel: DuplicatesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        viewModel.onConsentResult(granted = it.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(uiState.pendingConsent) {
        (uiState.pendingConsent as? IntentSender)?.let {
            consentLauncher.launch(IntentSenderRequest.Builder(it).build())
        }
    }

    val message = uiState.message?.let { stringResource(it.labelRes) }
    LaunchedEffect(uiState.message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.onMessageShown()
        }
    }

    DuplicatesContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRescan = viewModel::scan,
        onFilterSelected = viewModel::onFilterSelected,
        onToggle = viewModel::onToggle,
        onDeleteSelected = viewModel::onDeleteSelected,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DuplicatesContent(
    uiState: DuplicatesUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRescan: () -> Unit,
    onFilterSelected: (MediaKind?) -> Unit,
    onToggle: (String) -> Unit,
    onDeleteSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.duplicates_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(LibreIcons.Back, contentDescription = stringResource(R.string.library_back))
                    }
                },
                actions = {
                    IconButton(onClick = onRescan, enabled = uiState.scan is DuplicateScanState.Done) {
                        Icon(LibreIcons.Retry, contentDescription = stringResource(R.string.duplicates_rescan))
                    }
                },
            )
        },
        bottomBar = {
            if (uiState.selected.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = { confirmDelete = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Icon(LibreIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            text = stringResource(
                                R.string.duplicates_delete_selected,
                                uiState.selected.size,
                                formatBytes(uiState.selectedBytes),
                            ),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (val scan = uiState.scan) {
                is DuplicateScanState.Scanning -> Scanning(scan)
                is DuplicateScanState.Done -> {
                    if (scan.groups.isEmpty()) {
                        EmptyState(
                            icon = LibreIcons.Granted,
                            title = stringResource(R.string.duplicates_empty_title),
                            message = stringResource(R.string.duplicates_empty_message),
                        )
                    } else {
                        Results(uiState, scan.groups, onFilterSelected, onToggle)
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.duplicates_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.duplicates_confirm_message,
                        uiState.selected.size,
                        formatBytes(uiState.selectedBytes),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDeleteSelected()
                }) { Text(stringResource(R.string.library_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.library_cancel)) }
            },
        )
    }
}

@Composable
private fun Scanning(scan: DuplicateScanState.Scanning) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.duplicates_scanning),
            style = MaterialTheme.typography.titleMedium,
        )
        if (scan.total > 0) {
            LinearProgressIndicator(
                progress = { scan.done / scan.total.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            )
            Text(
                text = stringResource(R.string.duplicates_scanning_progress, scan.done, scan.total),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
        }
    }
}

@Composable
private fun Results(
    uiState: DuplicatesUiState,
    allGroups: List<DuplicateGroup>,
    onFilterSelected: (MediaKind?) -> Unit,
    onToggle: (String) -> Unit,
) {
    val groups = uiState.groups
    val reclaimable = groups.sumOf { it.reclaimableBytes }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.duplicates_summary, groups.size, formatBytes(reclaimable)),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.duplicates_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = uiState.filter == null,
                        onClick = { onFilterSelected(null) },
                        label = { Text(stringResource(R.string.duplicates_filter_all)) },
                    )
                    MediaKind.entries.filter { kind -> allGroups.any { it.kind == kind } }.forEach { kind ->
                        FilterChip(
                            selected = uiState.filter == kind,
                            onClick = { onFilterSelected(kind) },
                            label = { Text(stringResource(kind.pluralRes)) },
                        )
                    }
                }
            }
        }
        items(groups, key = { group -> group.items.first().uri }) { group ->
            GroupCard(group = group, selected = uiState.selected, onToggle = onToggle)
        }
    }
}

@Composable
private fun GroupCard(group: DuplicateGroup, selected: Set<String>, onToggle: (String) -> Unit) {
    // Siempre queda una copia sin marcar: la última no se puede marcar.
    val remaining = group.items.count { it.uri !in selected }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(
                    R.string.duplicates_group_title,
                    stringResource(group.kind.singularRes),
                    formatBytes(group.sizeBytes),
                    group.items.size,
                ),
                style = MaterialTheme.typography.titleSmall,
            )
            group.items.forEach { item ->
                val isSelected = item.uri in selected
                CopyRow(
                    item = item,
                    selected = isSelected,
                    enabled = isSelected || remaining > 1,
                    onToggle = { onToggle(item.uri) },
                )
            }
        }
    }
}

@Composable
private fun CopyRow(item: LibraryMedia, selected: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle() }, enabled = enabled)
        LocalMediaThumbnail(uri = item.uri, kind = item.kind, modifier = Modifier.size(56.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.folder.ifBlank { "/" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(item.dateAdded)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val DuplicatesMessage.labelRes: Int
    get() = when (this) {
        DuplicatesMessage.Deleted -> R.string.duplicates_deleted
        DuplicatesMessage.DeleteFailed -> R.string.duplicates_delete_failed
        DuplicatesMessage.ScanFailed -> R.string.duplicates_scan_failed
    }

private val MediaKind.singularRes: Int
    get() = when (this) {
        MediaKind.VIDEO -> R.string.duplicates_kind_video
        MediaKind.AUDIO -> R.string.duplicates_kind_audio
        MediaKind.IMAGE -> R.string.duplicates_kind_image
    }

private val MediaKind.pluralRes: Int
    get() = when (this) {
        MediaKind.VIDEO -> R.string.duplicates_filter_videos
        MediaKind.AUDIO -> R.string.duplicates_filter_music
        MediaKind.IMAGE -> R.string.duplicates_filter_images
    }
