package com.libredescarga.feature.analysis

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.core.model.MediaError
import com.libredescarga.core.ui.userMessage
import com.libredescarga.feature.analysis.component.PlaylistContent
import com.libredescarga.feature.analysis.component.SingleMediaContent
import kotlinx.coroutines.launch

/**
 * Hoja inferior que analiza un enlace y deja elegir calidad, formato,
 * subtítulos, nombre y recorte antes de descargar. La usan Inicio y el navegador.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaAnalysisSheet(
    url: String,
    onDismiss: () -> Unit,
    onQueued: (count: Int) -> Unit,
) {
    val viewModel = hiltViewModel<AnalysisViewModel, AnalysisViewModel.Factory>(key = url) { factory ->
        factory.create(url)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnQueued by rememberUpdatedState(onQueued)
    var submitError by remember { mutableStateOf<MediaError?>(null) }

    fun close(afterClose: () -> Unit = {}) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            afterClose()
            currentOnDismiss()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AnalysisEvent.Queued -> close { currentOnQueued(event.count) }
                is AnalysisEvent.Failed -> submitError = event.error
            }
        }
    }

    val requireStorage = rememberLegacyStorageGate()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        when (val state = uiState) {
            AnalysisUiState.Loading -> LoadingContent(url)

            is AnalysisUiState.Error -> ErrorContent(
                message = state.error.userMessage(),
                canRetry = state.error.isRetryable(),
                onRetry = viewModel::retry,
                onClose = { close() },
            )

            is AnalysisUiState.Single -> SingleMediaContent(
                state = state,
                submitError = submitError?.userMessage(),
                onTabSelected = viewModel::onTabSelected,
                onVideoSelected = viewModel::onVideoSelected,
                onAudioSelected = viewModel::onAudioSelected,
                onSubtitleSelected = viewModel::onSubtitleSelected,
                onEmbedSubtitleChange = viewModel::onEmbedSubtitleChange,
                onFileNameChange = viewModel::onFileNameChange,
                onTrimStartChange = viewModel::onTrimStartChange,
                onTrimEndChange = viewModel::onTrimEndChange,
                onDownload = { requireStorage(viewModel::onDownload) },
                onQuickDownload = { requireStorage(viewModel::onQuickDownload) },
            )

            is AnalysisUiState.Playlist -> PlaylistContent(
                state = state,
                submitError = submitError?.userMessage(),
                onEntryToggled = viewModel::onEntryToggled,
                onSelectAll = viewModel::onSelectAll,
                onKindSelected = viewModel::onPlaylistKindSelected,
                onVideoQualitySelected = viewModel::onPlaylistVideoQuality,
                onAudioFormatSelected = viewModel::onPlaylistAudioFormat,
                onDownload = { requireStorage(viewModel::onDownloadPlaylist) },
            )
        }
    }
}

private fun MediaError.isRetryable(): Boolean =
    this is MediaError.Network || this is MediaError.Unknown || this is MediaError.Unavailable || this is MediaError.BotCheck

@Composable
private fun LoadingContent(url: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 240.dp)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.analysis_loading),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = url,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ErrorContent(
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = LibreIcons.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onClose) { Text(stringResource(R.string.analysis_close)) }
            if (canRetry) {
                Button(onClick = onRetry) { Text(stringResource(R.string.analysis_retry)) }
            }
        }
    }
}

/**
 * En Android 7–9 guardar en Movies/ y Music/ requiere WRITE_EXTERNAL_STORAGE.
 * Desde Android 10 se usa MediaStore y no hace falta ningún permiso.
 */
@Composable
private fun rememberLegacyStorageGate(): (() -> Unit) -> Unit {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return { action -> action() }

    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pending?.invoke()
        pending = null
    }
    return { action ->
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            action()
        } else {
            pending = action
            launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
}
