package com.libredescarga.feature.downloads.component

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.libredescarga.core.config.AppConfig
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.core.model.UserSettings
import com.libredescarga.feature.downloads.DownloadSettingsUi
import com.libredescarga.feature.downloads.R
import kotlin.math.roundToInt

/** Ajustes del gestor de descargas, accesibles desde la propia pantalla de Descargas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadSettingsSheet(
    settings: DownloadSettingsUi,
    folderName: String?,
    onDismiss: () -> Unit,
    onMaxConcurrentChange: (Int) -> Unit,
    onFragmentsChange: (Int) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onSpeedLimitChange: (Int) -> Unit,
    onFolderChange: (String?) -> Unit,
) {
    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        // Se conserva el permiso para escribir ahí también tras reiniciar el teléfono.
        context.contentResolver.takePersistableUriPermission(uri, flags)
        settings.treeUri?.let { releasePermission(context, it, flags) }
        onFolderChange(uri.toString())
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.downloads_settings), style = MaterialTheme.typography.titleLarge)

            SliderSetting(
                title = stringResource(R.string.downloads_settings_concurrent),
                description = stringResource(R.string.downloads_settings_concurrent_description),
                value = settings.maxConcurrent,
                range = UserSettings.MIN_CONCURRENT_DOWNLOADS..UserSettings.MAX_CONCURRENT_DOWNLOADS,
                onValueChange = onMaxConcurrentChange,
            )

            SliderSetting(
                title = stringResource(R.string.downloads_settings_fragments),
                description = stringResource(R.string.downloads_settings_fragments_description),
                value = settings.concurrentFragments,
                range = UserSettings.MIN_FRAGMENTS..UserSettings.MAX_FRAGMENTS,
                onValueChange = onFragmentsChange,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = settings.wifiOnly, role = Role.Switch, onValueChange = onWifiOnlyChange)
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(LibreIcons.Wifi, contentDescription = null)
                Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    Text(stringResource(R.string.downloads_settings_wifi), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.downloads_settings_wifi_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.wifiOnly, onCheckedChange = null)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.downloads_settings_speed), style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UserSettings.SPEED_LIMIT_OPTIONS_KBPS.forEach { kbps ->
                        FilterChip(
                            selected = settings.speedLimitKbps == kbps,
                            onClick = { onSpeedLimitChange(kbps) },
                            label = { Text(speedLabel(kbps)) },
                        )
                    }
                }
            }

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(LibreIcons.Folder, contentDescription = null)
                    Column {
                        Text(stringResource(R.string.downloads_settings_folder), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = when {
                                settings.treeUri == null -> stringResource(
                                    R.string.downloads_settings_folder_system,
                                    AppConfig.DOWNLOAD_FOLDER_NAME,
                                )
                                folderName != null -> folderName
                                else -> stringResource(R.string.downloads_settings_folder_unavailable)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (settings.treeUri != null && folderName == null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { folderPicker.launch(null) }) {
                        Text(stringResource(R.string.downloads_settings_folder_choose))
                    }
                    if (settings.treeUri != null) {
                        TextButton(onClick = {
                            releasePermission(
                                context,
                                settings.treeUri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                            )
                            onFolderChange(null)
                        }) {
                            Text(stringResource(R.string.downloads_settings_folder_reset))
                        }
                    }
                }
                Text(
                    stringResource(R.string.downloads_settings_folder_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SliderSetting(
    title: String,
    description: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
) {
    var current by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(current.roundToInt().toString(), style = MaterialTheme.typography.titleMedium)
        }
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = current,
            onValueChange = { current = it },
            onValueChangeFinished = { onValueChange(current.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
        )
    }
}

@Composable
private fun speedLabel(kbps: Int): String = when {
    kbps <= 0 -> stringResource(R.string.downloads_settings_speed_unlimited)
    kbps < 1_000 -> stringResource(R.string.downloads_settings_speed_kb, kbps)
    else -> stringResource(R.string.downloads_settings_speed_mb, kbps / 1_000)
}

private fun releasePermission(context: Context, treeUri: String, flags: Int) {
    runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(treeUri), flags) }
}
