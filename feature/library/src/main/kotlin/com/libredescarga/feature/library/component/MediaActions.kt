package com.libredescarga.feature.library.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.libredescarga.core.model.UserPlaylist
import com.libredescarga.core.ui.formatBytes
import com.libredescarga.core.ui.formatDuration
import com.libredescarga.feature.library.R
import com.libredescarga.feature.library.moveTargets
import java.text.DateFormat
import java.util.Date

/** Acciones elegidas en la hoja de un archivo. */
internal enum class MediaAction {
    PLAY_AS_AUDIO, FAVORITE, ADD_TO_PLAYLIST, REMOVE_FROM_PLAYLIST, RENAME, MOVE, MOVE_TO_VAULT, SHARE, DETAILS, CONVERT, DELETE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaActionsSheet(
    media: LibraryMedia,
    isFavorite: Boolean,
    inPlaylist: Boolean,
    onAction: (MediaAction) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
        ) {
            Text(
                text = media.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            HorizontalDivider()
            if (media.kind == MediaKind.VIDEO) {
                ActionRow(LibreIcons.Music, stringResource(R.string.library_action_play_audio)) {
                    onAction(MediaAction.PLAY_AS_AUDIO)
                }
            }
            ActionRow(
                LibreIcons.Heart,
                stringResource(if (isFavorite) R.string.library_action_unfavorite else R.string.library_action_favorite),
            ) { onAction(MediaAction.FAVORITE) }
            ActionRow(LibreIcons.Add, stringResource(R.string.library_action_add_playlist)) {
                onAction(MediaAction.ADD_TO_PLAYLIST)
            }
            if (inPlaylist) {
                ActionRow(LibreIcons.Close, stringResource(R.string.library_action_remove_playlist)) {
                    onAction(MediaAction.REMOVE_FROM_PLAYLIST)
                }
            }
            ActionRow(LibreIcons.Edit, stringResource(R.string.library_action_rename)) { onAction(MediaAction.RENAME) }
            ActionRow(LibreIcons.Folder, stringResource(R.string.library_action_move)) { onAction(MediaAction.MOVE) }
            ActionRow(LibreIcons.Lock, stringResource(R.string.library_action_move_to_vault)) { onAction(MediaAction.MOVE_TO_VAULT) }
            ActionRow(LibreIcons.Share, stringResource(R.string.library_action_share)) { onAction(MediaAction.SHARE) }
            ActionRow(LibreIcons.Info, stringResource(R.string.library_action_details)) { onAction(MediaAction.DETAILS) }
            if (media.kind == MediaKind.VIDEO) {
                ActionRow(LibreIcons.Music, stringResource(R.string.library_action_convert)) {
                    onAction(MediaAction.CONVERT)
                }
            }
            ActionRow(LibreIcons.Delete, stringResource(R.string.library_action_delete)) { onAction(MediaAction.DELETE) }
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel)) } },
    )
}

@Composable
internal fun MoveDialog(media: LibraryMedia, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val targets = moveTargets(media.kind)
    var selected by rememberSaveable { mutableStateOf(targets.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_action_move)) },
        text = {
            Column {
                targets.forEach { target ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = target == selected, role = Role.RadioButton) { selected = target }
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = target == selected, onClick = null)
                        Text(target, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text(stringResource(R.string.library_move_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel)) } },
    )
}

@Composable
internal fun ConfirmDeleteDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_delete_title)) },
        text = { Text(stringResource(R.string.library_delete_message, title)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.library_delete_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel)) } },
    )
}

@Composable
internal fun DetailsDialog(media: LibraryMedia, onDismiss: () -> Unit) {
    val rows = listOfNotNull(
        stringResource(R.string.library_details_name) to media.displayName,
        stringResource(R.string.library_details_folder) to media.folder.ifEmpty { "—" },
        stringResource(R.string.library_details_size) to formatBytes(media.sizeBytes),
        stringResource(R.string.library_details_duration) to formatDuration(media.durationMs / 1000),
        media.width?.let { w -> media.height?.let { h -> stringResource(R.string.library_details_resolution) to "$w × $h" } },
        media.artist?.let { stringResource(R.string.library_details_artist) to it },
        media.album?.let { stringResource(R.string.library_details_album) to it },
        stringResource(R.string.library_details_type) to (media.mimeType ?: media.extension),
        stringResource(R.string.library_details_date) to DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.SHORT,
        ).format(Date(media.dateAdded)),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_action_details)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { (label, value) ->
                    Column {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_close)) } },
    )
}

/** Elegir una lista existente o crear una nueva con el archivo. */
@Composable
internal fun PlaylistPickerDialog(
    playlists: List<UserPlaylist>,
    onPick: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_action_add_playlist)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                playlists.forEach { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(playlist.id) }
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(LibreIcons.Playlist, contentDescription = null)
                        Text(playlist.name, modifier = Modifier.padding(start = 12.dp))
                    }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.library_playlist_new_name)) },
                    singleLine = true,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(newName) }, enabled = newName.isNotBlank()) {
                Text(stringResource(R.string.library_playlist_create))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel)) } },
    )
}
