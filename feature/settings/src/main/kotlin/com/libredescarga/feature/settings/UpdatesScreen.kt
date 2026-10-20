package com.libredescarga.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.libredescarga.core.config.AppConfig
import com.libredescarga.core.model.AppUpdate
import com.libredescarga.core.model.UpdateFailure
import com.libredescarga.feature.settings.component.SettingsScaffold
import kotlin.math.roundToInt

/** Pantalla "Actualizaciones": busca versiones nuevas de la app, del motor de descarga y de la lista de sitios bloqueados. */
@Composable
internal fun UpdatesScreen(onBack: () -> Unit, viewModel: UpdatesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScaffold(title = stringResource(R.string.settings_updates), onBack = onBack) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                stringResource(R.string.settings_updates_privacy, AppConfig.GITHUB_REPO_URL.removePrefix("https://")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppCard(state, onCheck = viewModel::onCheckApp, onDownload = viewModel::onDownloadUpdate, onInstall = viewModel::onInstallUpdate)
            EngineCard(state, onUpdate = viewModel::onUpdateEngine)
            BlocklistCard(state, onUpdate = viewModel::onUpdateBlocklist)
        }
    }
}

/** Tarjeta de la app: buscar, descargar e instalar la versión nueva. Muestra el progreso y los errores. */
@Composable
private fun AppCard(state: UpdatesUiState, onCheck: () -> Unit, onDownload: () -> Unit, onInstall: () -> Unit) {
    UpdateCard(
        title = stringResource(R.string.settings_updates_app),
        subtitle = stringResource(R.string.settings_updates_installed, state.installedVersionName, state.installedVersionCode),
    ) {
        when (val app = state.app) {
            AppUpdateStatus.Idle -> Unit
            AppUpdateStatus.Checking -> Busy(stringResource(R.string.settings_updates_checking))
            AppUpdateStatus.UpToDate -> Status(stringResource(R.string.settings_updates_app_current))
            is AppUpdateStatus.Available -> AvailableDetails(app.update)
            is AppUpdateStatus.Unsupported -> {
                AvailableDetails(app.update)
                Status(stringResource(R.string.settings_updates_unsupported, app.update.minSdk))
            }
            is AppUpdateStatus.Downloading -> {
                Status(stringResource(R.string.settings_updates_downloading, (app.progress * 100).roundToInt()))
                LinearProgressIndicator(progress = { app.progress }, modifier = Modifier.fillMaxWidth())
                Status(stringResource(R.string.settings_updates_verifying_note))
            }
            is AppUpdateStatus.Ready -> {
                AvailableDetails(app.update)
                Status(stringResource(R.string.settings_updates_verified))
                if (app.needsPermission) {
                    Status(stringResource(R.string.settings_updates_permission), isError = true)
                }
            }
            is AppUpdateStatus.Failed -> Status(stringResource(app.reason.messageRes), isError = true)
        }

        // Según el estado se muestra un botón distinto: Descargar, Instalar o Buscar.
        val busy = state.app is AppUpdateStatus.Checking || state.app is AppUpdateStatus.Downloading
        when (state.app) {
            is AppUpdateStatus.Available -> Button(onClick = onDownload, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.settings_updates_download))
            }
            is AppUpdateStatus.Ready -> Button(onClick = onInstall, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.settings_updates_install))
            }
            else -> OutlinedButton(onClick = onCheck, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.settings_updates_check))
            }
        }
    }
}

/** Muestra el número de la versión nueva y sus novedades. */
@Composable
private fun AvailableDetails(update: AppUpdate) {
    Text(
        stringResource(R.string.settings_updates_available, update.versionName),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    if (update.notes.isNotBlank()) {
        Text(update.notes, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Tarjeta del motor de descarga (yt-dlp): se actualiza cuando una página cambia y deja de funcionar. */
@Composable
private fun EngineCard(state: UpdatesUiState, onUpdate: () -> Unit) {
    UpdateCard(
        title = stringResource(R.string.settings_updates_engine),
        subtitle = stringResource(R.string.settings_updates_engine_version, state.engineVersion ?: stringResource(R.string.settings_updates_unknown)),
    ) {
        Text(stringResource(R.string.settings_updates_engine_description), style = MaterialTheme.typography.bodySmall)
        when (val engine = state.engine) {
            EngineStatus.Idle -> Unit
            EngineStatus.Updating -> Busy(stringResource(R.string.settings_updates_engine_updating))
            EngineStatus.Updated -> Status(stringResource(R.string.settings_updates_engine_updated))
            EngineStatus.Current -> Status(stringResource(R.string.settings_updates_engine_current))
            is EngineStatus.Failed -> Status(stringResource(engine.reason.messageRes), isError = true)
        }
        OutlinedButton(
            onClick = onUpdate,
            enabled = state.engine != EngineStatus.Updating,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.settings_updates_engine_action)) }
    }
}

/** Tarjeta de la lista de sitios bloqueados: descarga la lista más reciente. */
@Composable
private fun BlocklistCard(state: UpdatesUiState, onUpdate: () -> Unit) {
    UpdateCard(
        title = stringResource(R.string.settings_updates_blocklist),
        subtitle = stringResource(R.string.settings_updates_blocklist_description),
    ) {
        when (val status = state.blocklist) {
            BlocklistStatus.Idle -> Unit
            BlocklistStatus.Updating -> Busy(stringResource(R.string.settings_updates_checking))
            is BlocklistStatus.Updated -> Status(stringResource(R.string.settings_updates_blocklist_updated, status.domains))
            BlocklistStatus.Current -> Status(stringResource(R.string.settings_updates_blocklist_current))
            is BlocklistStatus.Failed -> Status(stringResource(status.reason.messageRes), isError = true)
        }
        OutlinedButton(
            onClick = onUpdate,
            enabled = state.blocklist != BlocklistStatus.Updating,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.settings_updates_blocklist_action)) }
    }
}

/** Marco común de las tres tarjetas: título, subtítulo y el contenido de cada una. */
@Composable
private fun UpdateCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

/** Círculo que gira con un texto, para indicar que algo está en curso. */
@Composable
private fun Busy(text: String) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Mensaje de estado; en rojo si es un error. */
@Composable
private fun Status(text: String, isError: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    )
}

// Texto que corresponde a cada tipo de fallo de actualización.
private val UpdateFailure.messageRes: Int
    get() = when (this) {
        UpdateFailure.NETWORK -> R.string.settings_updates_error_network
        UpdateFailure.INVALID_MANIFEST -> R.string.settings_updates_error_manifest
        UpdateFailure.NO_COMPATIBLE_APK -> R.string.settings_updates_error_no_apk
        UpdateFailure.HASH_MISMATCH -> R.string.settings_updates_error_hash
        UpdateFailure.SIGNATURE_MISMATCH -> R.string.settings_updates_error_signature
        UpdateFailure.STORAGE -> R.string.settings_updates_error_storage
        UpdateFailure.UNKNOWN -> R.string.settings_updates_error_unknown
    }
