package com.libredescarga.feature.player.music.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.core.model.QueueEntry
import com.libredescarga.core.player.EqualizerState
import com.libredescarga.feature.player.R
import com.libredescarga.feature.player.music.MusicPlayerViewModel
import kotlin.math.roundToInt

/** Cola de reproducción: tocar una canción salta a ella; la X la quita de la cola. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueueSheet(
    queue: List<QueueEntry>,
    currentIndex: Int,
    onSkipTo: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { if (currentIndex in queue.indices) listState.scrollToItem(currentIndex) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.player_queue),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(state = listState, modifier = Modifier.navigationBarsPadding()) {
            itemsIndexed(queue, key = { index, entry -> "$index-${entry.uri}" }) { index, entry ->
                val isCurrent = index == currentIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSkipTo(index) }
                        .heightIn(min = 56.dp)
                        .padding(start = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isCurrent) {
                        Icon(LibreIcons.Play, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = if (isCurrent) 12.dp else 36.dp)) {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        entry.artist?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                    IconButton(onClick = { onRemove(index) }) {
                        Icon(LibreIcons.Close, contentDescription = stringResource(R.string.player_queue_remove, entry.title))
                    }
                }
            }
        }
    }
}

@Composable
internal fun SleepTimerDialog(
    remainingMs: Long?,
    onStart: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_sleep_timer)) },
        text = {
            Column {
                MusicPlayerViewModel.SLEEP_OPTIONS_MINUTES.forEach { minutes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStart(minutes) }
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.player_sleep_minutes, minutes), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            if (remainingMs != null) {
                TextButton(onClick = onCancelTimer) { Text(stringResource(R.string.player_sleep_cancel)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.player_close)) } },
    )
}

/** Ecualizador básico: activar, elegir un preajuste o mover cada banda. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EqualizerSheet(
    state: EqualizerState,
    onEnabledChange: (Boolean) -> Unit,
    onPreset: (Int) -> Unit,
    onBand: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.player_equalizer), style = MaterialTheme.typography.titleLarge)
            if (!state.available) {
                Text(
                    stringResource(R.string.player_equalizer_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = state.enabled, role = Role.Switch, onValueChange = onEnabledChange)
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.player_equalizer_enable), modifier = Modifier.weight(1f))
                Switch(checked = state.enabled, onCheckedChange = null)
            }
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.presets.forEachIndexed { index, name ->
                    FilterChip(
                        selected = state.presetIndex == index,
                        onClick = { onPreset(index) },
                        enabled = state.enabled,
                        label = { Text(name) },
                    )
                }
            }
            state.bands.forEach { band ->
                var level by remember(band.levelMb) { mutableFloatStateOf(band.levelMb.toFloat()) }
                Column {
                    Row {
                        Text(bandLabel(band.centerHz), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        Text("${(level / 100).roundToInt()} dB", style = MaterialTheme.typography.labelLarge)
                    }
                    Slider(
                        value = level,
                        onValueChange = { level = it },
                        onValueChangeFinished = { onBand(band.index, level.roundToInt()) },
                        valueRange = state.minLevelMb.toFloat()..state.maxLevelMb.toFloat(),
                        enabled = state.enabled,
                    )
                }
            }
        }
    }
}

private fun bandLabel(hz: Int): String = if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz"
