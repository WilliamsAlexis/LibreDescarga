package com.libredescarga.feature.downloads.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.core.model.DownloadErrorCodes
import com.libredescarga.core.model.DownloadStatus
import com.libredescarga.core.model.DownloadTask
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.model.VaultItem
import com.libredescarga.core.ui.MediaThumbnail
import com.libredescarga.core.ui.formatBytes
import com.libredescarga.core.ui.formatDuration
import com.libredescarga.core.ui.label
import com.libredescarga.core.ui.userMessage
import com.libredescarga.feature.downloads.R
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** Tarjeta de una descarga con su progreso y las acciones según su estado. */
@Composable
internal fun DownloadItem(
    task: DownloadTask,
    wifiOnly: Boolean,
    isFirstInQueue: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onPrioritize: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val request = task.request
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MediaThumbnail(
                    url = request.thumbnailUrl,
                    durationSeconds = request.durationSeconds,
                    modifier = Modifier.size(width = 112.dp, height = 63.dp),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = request.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatLabel(task),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            ProgressSection(task = task, wifiOnly = wifiOnly)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (task.status) {
                    DownloadStatus.RUNNING, DownloadStatus.WAITING -> {
                        Action(LibreIcons.Pause, stringResource(R.string.downloads_action_pause), onPause)
                        Action(LibreIcons.Close, stringResource(R.string.downloads_action_cancel), onCancel)
                    }

                    DownloadStatus.QUEUED -> {
                        if (!isFirstInQueue) {
                            Action(LibreIcons.MoveToTop, stringResource(R.string.downloads_action_prioritize), onPrioritize)
                        }
                        Action(LibreIcons.Pause, stringResource(R.string.downloads_action_pause), onPause)
                        Action(LibreIcons.Close, stringResource(R.string.downloads_action_cancel), onCancel)
                    }

                    DownloadStatus.PAUSED -> {
                        Action(LibreIcons.Play, stringResource(R.string.downloads_action_resume), onResume)
                        Action(LibreIcons.Close, stringResource(R.string.downloads_action_cancel), onCancel)
                    }

                    DownloadStatus.COMPLETED -> {
                        Action(LibreIcons.Open, stringResource(R.string.downloads_action_open), onOpen)
                        Action(LibreIcons.Share, stringResource(R.string.downloads_action_share), onShare)
                        Action(LibreIcons.Delete, stringResource(R.string.downloads_action_remove), onRemove)
                    }

                    DownloadStatus.FAILED -> {
                        Action(LibreIcons.Retry, stringResource(R.string.downloads_action_retry), onRetry)
                        Action(LibreIcons.Delete, stringResource(R.string.downloads_action_remove), onRemove)
                    }

                    DownloadStatus.CANCELLED -> Unit
                }
            }
        }
    }
}

@Composable
private fun ProgressSection(task: DownloadTask, wifiOnly: Boolean) {
    when (task.status) {
        DownloadStatus.RUNNING -> {
            val progress by animateFloatAsState(task.progressPercent / 100f, label = "progress")
            if (task.progressPercent > 0f) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            StatusText(runningDetails(task))
        }

        DownloadStatus.WAITING -> {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            StatusText(stringResource(if (wifiOnly) R.string.downloads_waiting_wifi else R.string.downloads_waiting_network))
        }

        DownloadStatus.QUEUED -> StatusText(stringResource(R.string.downloads_queued))

        DownloadStatus.PAUSED -> {
            LinearProgressIndicator(
                progress = { task.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.outline,
            )
            StatusText(stringResource(R.string.downloads_paused, task.progressPercent.roundToInt()))
        }

        DownloadStatus.COMPLETED -> {
            val size = task.totalBytes ?: task.request.estimatedBytes
            val date = task.completedAt?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)) }
            StatusText(listOfNotNull(size?.let { formatBytes(it) }, date).joinToString(" · "))
        }

        DownloadStatus.FAILED -> Text(
            text = DownloadErrorCodes.toError(task.errorMessage).userMessage(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )

        DownloadStatus.CANCELLED -> Unit
    }
}

/** "45 % · 12 MB de 50 MB · 2,1 MB/s · 0:20 restantes" */
@Composable
private fun runningDetails(task: DownloadTask): String {
    val parts = mutableListOf("${task.progressPercent.roundToInt()} %")
    val total = task.totalBytes
    val downloaded = task.downloadedBytes
    if (total != null && downloaded != null) {
        parts += stringResource(R.string.downloads_size_of, formatBytes(downloaded), formatBytes(total))
    }
    task.speedBytesPerSecond?.takeIf { it > 0 }?.let { parts += stringResource(R.string.downloads_speed, formatBytes(it)) }
    task.etaSeconds?.takeIf { it > 0 }?.let { parts += stringResource(R.string.downloads_eta, formatDuration(it)) }
    return parts.joinToString(" · ")
}

/** "Video · 720p", "Audio · MP3 · 320 kbps", etc. */
@Composable
private fun formatLabel(task: DownloadTask): String {
    val request = task.request
    return when (request.kind) {
        MediaKind.VIDEO -> {
            val quality = request.video?.maxHeight?.let { "${it}p" }
                ?: request.video?.container?.name
            listOfNotNull(stringResource(R.string.downloads_kind_video), quality).joinToString(" · ")
        }
        MediaKind.AUDIO -> listOfNotNull(
            stringResource(R.string.downloads_kind_audio),
            request.audioFormat?.label(),
        ).joinToString(" · ")
        MediaKind.IMAGE -> stringResource(R.string.downloads_kind_image)
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Action(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = label)
    }
}

/** Quitar de la lista, con la opción de borrar también el archivo descargado. */
@Composable
internal fun RemoveDownloadDialog(
    task: DownloadTask,
    onConfirm: (deleteFile: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val hasFile = task.status == DownloadStatus.COMPLETED && task.outputUri != null && !VaultItem.isVaultUri(task.outputUri)
    var deleteFile by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.downloads_remove_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(task.request.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (hasFile) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(value = deleteFile, role = Role.Checkbox, onValueChange = { deleteFile = it }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = deleteFile, onCheckedChange = null)
                        Text(
                            text = stringResource(R.string.downloads_remove_delete_file),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(hasFile && deleteFile) }) {
                Text(stringResource(R.string.downloads_remove_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.downloads_cancel)) }
        },
    )
}
