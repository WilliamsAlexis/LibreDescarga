package com.libredescarga.feature.vault.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.core.model.LibraryMedia
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.model.VaultItem
import com.libredescarga.core.model.VaultSettings
import com.libredescarga.core.ui.EmptyState
import com.libredescarga.core.ui.LocalMediaThumbnail
import com.libredescarga.core.ui.formatBytes
import com.libredescarga.core.ui.formatDuration
import com.libredescarga.feature.vault.R

/** Contenido de la bóveda abierta: lista de archivos protegidos, botón para agregar y acceso a los ajustes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VaultContent(
    items: List<VaultItem>,
    settings: VaultSettings,
    biometricAvailable: Boolean,
    snackbarHostState: SnackbarHostState,
    onItemClick: (VaultItem) -> Unit,
    onRestore: (VaultItem) -> Unit,
    onDelete: (VaultItem) -> Unit,
    onRename: (VaultItem, String) -> Unit,
    onAdd: () -> Unit,
    onLock: () -> Unit,
    onChangeCredential: () -> Unit,
    onBiometricChange: (Boolean) -> Unit,
    onEncryptChange: (Boolean) -> Unit,
    onDownloadsChange: (Boolean) -> Unit,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var renameFor by remember { mutableStateOf<VaultItem?>(null) }
    var deleteFor by remember { mutableStateOf<VaultItem?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_vault_title)) },
                actions = {
                    IconButton(onClick = onLock) {
                        Icon(LibreIcons.Lock, contentDescription = stringResource(R.string.vault_lock_now))
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(LibreIcons.Settings, contentDescription = stringResource(R.string.vault_settings))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(LibreIcons.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.vault_add)) },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (items.isEmpty()) {
                EmptyState(
                    icon = LibreIcons.Lock,
                    title = stringResource(R.string.vault_empty_title),
                    message = stringResource(R.string.vault_empty_message),
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)) {
                    items(items, key = { it.id }) { item ->
                        VaultRow(
                            item = item,
                            onClick = { onItemClick(item) },
                            onRestore = { onRestore(item) },
                            onRename = { renameFor = item },
                            onDelete = { deleteFor = item },
                        )
                    }
                }
            }
        }
    }

    renameFor?.let { item ->
        var text by rememberSaveable(item.id) { mutableStateOf(item.name.substringBeforeLast('.')) }
        AlertDialog(
            onDismissRequest = { renameFor = null },
            title = { Text(stringResource(R.string.vault_rename)) },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text(stringResource(R.string.vault_name)) }) },
            confirmButton = { TextButton(onClick = { onRename(item, text); renameFor = null }, enabled = text.isNotBlank()) { Text(stringResource(R.string.vault_save)) } },
            dismissButton = { TextButton(onClick = { renameFor = null }) { Text(stringResource(R.string.vault_cancel)) } },
        )
    }
    deleteFor?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text(stringResource(R.string.vault_delete_title)) },
            text = { Text(stringResource(R.string.vault_delete_message, item.name)) },
            confirmButton = { TextButton(onClick = { onDelete(item); deleteFor = null }) { Text(stringResource(R.string.vault_delete_confirm)) } },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text(stringResource(R.string.vault_cancel)) } },
        )
    }
    if (showSettings) {
        VaultSettingsSheet(
            settings = settings,
            biometricAvailable = biometricAvailable,
            onDismiss = { showSettings = false },
            onChangeCredential = { showSettings = false; onChangeCredential() },
            onBiometricChange = onBiometricChange,
            onEncryptChange = onEncryptChange,
            onDownloadsChange = onDownloadsChange,
            onLock = { showSettings = false; onLock() },
        )
    }
}

/** Fila de un archivo de la bóveda con su nombre, tamaño y menú (restaurar, renombrar, borrar). */
@Composable
private fun VaultRow(
    item: VaultItem,
    onClick: () -> Unit,
    onRestore: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(48.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (item.kind == MediaKind.VIDEO) LibreIcons.Library else LibreIcons.Music,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = listOfNotNull(
                    formatBytes(item.sizeBytes).takeIf { item.sizeBytes > 0 },
                    formatDuration(item.durationMs / 1000).takeIf { item.durationMs > 0 },
                    stringResource(if (item.encrypted) R.string.vault_encrypted else R.string.vault_not_encrypted),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(LibreIcons.More, contentDescription = stringResource(R.string.vault_actions_for, item.name))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.vault_restore)) },
                    leadingIcon = { Icon(LibreIcons.Restore, contentDescription = null) },
                    onClick = { menu = false; onRestore() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.vault_rename)) },
                    leadingIcon = { Icon(LibreIcons.Edit, contentDescription = null) },
                    onClick = { menu = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.vault_delete)) },
                    leadingIcon = { Icon(LibreIcons.Delete, contentDescription = null) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}

/** Hoja de ajustes de la bóveda: huella/rostro, cifrado, descargas directas, cambiar PIN y bloquear. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultSettingsSheet(
    settings: VaultSettings,
    biometricAvailable: Boolean,
    onDismiss: () -> Unit,
    onChangeCredential: () -> Unit,
    onBiometricChange: (Boolean) -> Unit,
    onEncryptChange: (Boolean) -> Unit,
    onDownloadsChange: (Boolean) -> Unit,
    onLock: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.vault_settings), style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = onChangeCredential, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.vault_change_credential))
            }
            if (biometricAvailable) {
                SettingSwitch(R.string.vault_setting_biometric, R.string.vault_setting_biometric_desc, settings.biometricEnabled, onBiometricChange)
            }
            SettingSwitch(R.string.vault_setting_encrypt, R.string.vault_setting_encrypt_desc, settings.encryptNewFiles, onEncryptChange)
            SettingSwitch(R.string.vault_setting_downloads, R.string.vault_setting_downloads_desc, settings.downloadsToVault, onDownloadsChange)
            HorizontalDivider()
            Text(
                stringResource(R.string.vault_setting_autolock),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onLock, modifier = Modifier.fillMaxWidth()) {
                Icon(LibreIcons.Lock, contentDescription = null)
                Text(stringResource(R.string.vault_lock_now), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** Fila de ajuste con título, explicación e interruptor. */
@Composable
private fun SettingSwitch(title: Int, description: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange).heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Elegir archivos de la biblioteca para moverlos a la carpeta privada. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddFromLibrarySheet(
    choices: List<LibraryMedia>,
    onMove: (List<LibraryMedia>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(setOf<String>()) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Text(stringResource(R.string.vault_add_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            Text(
                stringResource(R.string.vault_add_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            if (choices.isEmpty()) {
                Text(stringResource(R.string.vault_add_empty), modifier = Modifier.padding(24.dp))
            } else {
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(choices, key = { it.uri }) { media ->
                        val checked = media.uri in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(value = checked, role = Role.Checkbox) {
                                    selected = if (checked) selected - media.uri else selected + media.uri
                                }
                                .heightIn(min = 64.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            LocalMediaThumbnail(uri = media.uri, kind = media.kind, modifier = Modifier.size(48.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(media.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    formatBytes(media.sizeBytes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            Button(
                onClick = { onMove(choices.filter { it.uri in selected }) },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Text(stringResource(R.string.vault_add_confirm, selected.size))
            }
        }
    }
}
