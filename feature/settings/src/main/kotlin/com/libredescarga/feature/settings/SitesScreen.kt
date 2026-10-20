package com.libredescarga.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.feature.settings.component.BodyText
import com.libredescarga.feature.settings.component.SettingsScaffold
import com.libredescarga.feature.settings.component.SettingsSection

/** Sitios de la cuadrícula de Inicio (activar, ordenar, quitar los propios) y sitios bloqueados por el usuario. */
@Composable
internal fun SitesScreen(onBack: () -> Unit, viewModel: SitesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddSite by rememberSaveable { mutableStateOf(false) }
    var showBlockDomain by rememberSaveable { mutableStateOf(false) }

    SettingsScaffold(title = stringResource(R.string.settings_sites), onBack = onBack) {
        SettingsSection(stringResource(R.string.settings_sites_grid)) {
            BodyText(stringResource(R.string.settings_sites_grid_description))
            state.sites.forEachIndexed { index, site ->
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Switch(checked = site.enabled, onCheckedChange = { viewModel.onSetEnabled(site, it) })
                    androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(site.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            site.url.removePrefix("https://"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    IconButton(onClick = { viewModel.onMove(site, up = true) }, enabled = index > 0) {
                        Icon(LibreIcons.ArrowUp, contentDescription = stringResource(R.string.settings_sites_move_up, site.name))
                    }
                    IconButton(onClick = { viewModel.onMove(site, up = false) }, enabled = index < state.sites.lastIndex) {
                        Icon(LibreIcons.ArrowDown, contentDescription = stringResource(R.string.settings_sites_move_down, site.name))
                    }
                    if (site.isCustom) {
                        IconButton(onClick = { viewModel.onRemove(site) }) {
                            Icon(LibreIcons.Delete, contentDescription = stringResource(R.string.settings_sites_remove, site.name))
                        }
                    }
                }
            }
            OutlinedButton(onClick = { showAddSite = true }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Icon(LibreIcons.Add, contentDescription = null)
                Text(stringResource(R.string.settings_sites_add), modifier = Modifier.padding(start = 8.dp))
            }
        }

        SettingsSection(stringResource(R.string.settings_sites_blocked)) {
            BodyText(stringResource(R.string.settings_sites_blocked_description))
            state.blockedDomains.forEach { domain ->
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(domain, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.onUnblockDomain(domain) }) {
                        Icon(LibreIcons.Close, contentDescription = stringResource(R.string.settings_sites_unblock, domain))
                    }
                }
            }
            OutlinedButton(onClick = { showBlockDomain = true }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Icon(LibreIcons.NoAds, contentDescription = null)
                Text(stringResource(R.string.settings_sites_block), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }

    if (showAddSite) {
        var name by rememberSaveable { mutableStateOf("") }
        var url by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddSite = false },
            title = { Text(stringResource(R.string.settings_sites_add)) },
            text = {
                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(stringResource(R.string.settings_sites_name)) })
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.settings_sites_url)) },
                        isError = state.error == SitesError.InvalidSite,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    if (state.error == SitesError.InvalidSite) {
                        Text(stringResource(R.string.settings_sites_invalid_site), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onAddSite(name, url); showAddSite = false }, enabled = url.isNotBlank()) {
                    Text(stringResource(R.string.settings_sites_add_confirm))
                }
            },
            dismissButton = { TextButton(onClick = { showAddSite = false }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }

    if (showBlockDomain) {
        var domain by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showBlockDomain = false },
            title = { Text(stringResource(R.string.settings_sites_block)) },
            text = {
                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = domain,
                        onValueChange = { domain = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.settings_sites_domain)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    Text(stringResource(R.string.settings_sites_block_hint), style = MaterialTheme.typography.bodySmall)
                    if (state.error == SitesError.InvalidDomain) {
                        Text(stringResource(R.string.settings_sites_invalid_domain), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onBlockDomain(domain); showBlockDomain = false }, enabled = domain.isNotBlank()) {
                    Text(stringResource(R.string.settings_sites_block_confirm))
                }
            },
            dismissButton = { TextButton(onClick = { showBlockDomain = false }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
}
