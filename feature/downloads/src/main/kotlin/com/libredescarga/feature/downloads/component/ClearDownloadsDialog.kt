package com.libredescarga.feature.downloads.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.libredescarga.feature.downloads.DownloadsTab
import com.libredescarga.feature.downloads.R

/**
 * Confirmación para limpiar de una vez todas las descargas completadas o fallidas.
 * Si hay archivos que se pueden borrar ([deletableFiles] > 0), ofrece una casilla para eliminarlos también del dispositivo.
 */
@Composable
internal fun ClearDownloadsDialog(
    tab: DownloadsTab,
    count: Int,
    deletableFiles: Int,
    onConfirm: (deleteFiles: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var deleteFiles by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (tab == DownloadsTab.FAILED) R.string.downloads_clear_title_failed else R.string.downloads_clear_title_completed,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(pluralStringResource(R.plurals.downloads_clear_message, count, count))
                if (deletableFiles > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(value = deleteFiles, role = Role.Checkbox, onValueChange = { deleteFiles = it }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = deleteFiles, onCheckedChange = null)
                        Text(
                            text = stringResource(R.string.downloads_clear_delete_files, deletableFiles),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    if (deleteFiles) {
                        Text(
                            text = stringResource(R.string.downloads_clear_delete_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(deletableFiles > 0 && deleteFiles) }) {
                Text(stringResource(R.string.downloads_clear_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.downloads_cancel)) }
        },
    )
}
