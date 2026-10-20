package com.libredescarga.feature.analysis.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.core.model.AudioFormat
import com.libredescarga.core.model.MediaEntry
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.model.VideoQuality
import com.libredescarga.core.ui.MediaThumbnail
import com.libredescarga.core.ui.label
import com.libredescarga.feature.analysis.AnalysisUiState
import com.libredescarga.feature.analysis.PlaylistAudioFormats
import com.libredescarga.feature.analysis.PlaylistVideoQualities
import com.libredescarga.feature.analysis.R

/** Lista de reproducción o canal: casillas, calidad global y descarga por lotes. */
@Composable
internal fun PlaylistContent(
    state: AnalysisUiState.Playlist,
    submitError: String?,
    onEntryToggled: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onKindSelected: (MediaKind) -> Unit,
    onVideoQualitySelected: (VideoQuality) -> Unit,
    onAudioFormatSelected: (AudioFormat) -> Unit,
    onDownload: () -> Unit,
) {
    val playlist = state.playlist
    Column(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                MediaHeader(
                    title = playlist.title,
                    subtitle = playlist.uploader,
                    details = pluralStringResource(
                        R.plurals.analysis_playlist_items,
                        playlist.entries.size,
                        playlist.entries.size,
                    ),
                    thumbnailUrl = playlist.entries.firstOrNull()?.thumbnailUrl,
                    durationSeconds = null,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf(MediaKind.VIDEO, MediaKind.AUDIO).forEachIndexed { index, kind ->
                        SegmentedButton(
                            selected = state.kind == kind,
                            onClick = { onKindSelected(kind) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                            label = {
                                Text(
                                    stringResource(
                                        if (kind == MediaKind.VIDEO) R.string.analysis_tab_video else R.string.analysis_tab_audio,
                                    ),
                                )
                            },
                        )
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.analysis_playlist_quality),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (state.kind) {
                        MediaKind.VIDEO -> items(PlaylistVideoQualities) { quality ->
                            FilterChip(
                                selected = quality == state.videoQuality,
                                onClick = { onVideoQualitySelected(quality) },
                                label = { Text(quality.label()) },
                            )
                        }

                        MediaKind.AUDIO -> items(PlaylistAudioFormats) { format ->
                            FilterChip(
                                selected = format == state.audioFormat,
                                onClick = { onAudioFormatSelected(format) },
                                label = { Text(format.label()) },
                            )
                        }

                        MediaKind.IMAGE -> Unit // Las listas solo se descargan como video o audio.
                    }
                }
            }

            item {
                val toggleState = when {
                    state.allSelected -> ToggleableState.On
                    state.selectedIds.isEmpty() -> ToggleableState.Off
                    else -> ToggleableState.Indeterminate
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .toggleable(
                            value = state.allSelected,
                            role = Role.Checkbox,
                            onValueChange = { onSelectAll(!state.allSelected) },
                        )
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TriStateCheckbox(state = toggleState, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.analysis_playlist_select_all), style = MaterialTheme.typography.titleSmall)
                }
                HorizontalDivider()
            }

            items(playlist.entries, key = { it.id }) { entry ->
                PlaylistEntryRow(
                    entry = entry,
                    checked = entry.id in state.selectedIds,
                    onToggle = { onEntryToggled(entry.id) },
                )
            }
        }

        HorizontalDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            submitError?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = onDownload,
                enabled = state.canDownload,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Icon(LibreIcons.Downloads, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    pluralStringResource(
                        R.plurals.analysis_playlist_download,
                        state.selectedIds.size,
                        state.selectedIds.size,
                    ),
                )
            }
        }
    }
}

@Composable
private fun PlaylistEntryRow(entry: MediaEntry, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        MediaThumbnail(
            url = entry.thumbnailUrl,
            durationSeconds = entry.durationSeconds,
            modifier = Modifier.size(width = 96.dp, height = 54.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            entry.uploader?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
