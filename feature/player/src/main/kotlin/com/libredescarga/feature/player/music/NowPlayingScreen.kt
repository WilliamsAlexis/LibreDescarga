package com.libredescarga.feature.player.music

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.model.PlaybackState
import com.libredescarga.core.model.RepeatMode
import com.libredescarga.core.model.VaultItem
import com.libredescarga.core.ui.LocalMediaThumbnail
import com.libredescarga.core.ui.formatDuration
import com.libredescarga.feature.player.R
import com.libredescarga.feature.player.SecureWindowEffect
import com.libredescarga.feature.player.music.component.EqualizerSheet
import com.libredescarga.feature.player.music.component.QueueSheet
import com.libredescarga.feature.player.music.component.SleepTimerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NowPlayingScreen(onBack: () -> Unit, viewModel: MusicPlayerViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playback = uiState.playback
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var showTimer by rememberSaveable { mutableStateOf(false) }
    var showEqualizer by rememberSaveable { mutableStateOf(false) }

    SecureWindowEffect(enabled = VaultItem.isVaultUri(playback.current?.uri))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.player_now_playing)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(LibreIcons.ExpandCircle, contentDescription = stringResource(R.string.player_minimize))
                    }
                },
                actions = {
                    IconButton(onClick = { showTimer = true }) {
                        Icon(
                            LibreIcons.Timer,
                            contentDescription = stringResource(R.string.player_sleep_timer),
                            tint = if (uiState.sleepRemainingMs != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = { showEqualizer = true }) {
                        Icon(LibreIcons.Equalizer, contentDescription = stringResource(R.string.player_equalizer))
                    }
                },
            )
        },
    ) { innerPadding ->
        val current = playback.current
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.player_nothing_playing), style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Artwork(playback, Modifier.widthIn(max = 360.dp).fillMaxWidth().aspectRatio(1f))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(current.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    current.artist?.let {
                        Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                IconButton(onClick = viewModel::onToggleFavorite) {
                    Icon(
                        imageVector = LibreIcons.Heart,
                        contentDescription = stringResource(
                            if (uiState.isFavorite) R.string.player_unfavorite else R.string.player_favorite,
                        ),
                        tint = if (uiState.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SeekBar(playback, onSeek = viewModel::onSeekTo)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = viewModel::onToggleShuffle) {
                    Icon(
                        LibreIcons.Shuffle,
                        contentDescription = stringResource(R.string.player_shuffle),
                        tint = if (playback.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = viewModel::onPrevious, modifier = Modifier.size(56.dp)) {
                    Icon(LibreIcons.SkipPrevious, contentDescription = stringResource(R.string.player_previous), modifier = Modifier.size(36.dp))
                }
                FilledIconButton(onClick = viewModel::onTogglePlayPause, modifier = Modifier.size(72.dp)) {
                    Icon(
                        imageVector = if (playback.isPlaying) LibreIcons.Pause else LibreIcons.Play,
                        contentDescription = stringResource(if (playback.isPlaying) R.string.player_pause else R.string.player_play),
                        modifier = Modifier.size(40.dp),
                    )
                }
                IconButton(onClick = viewModel::onNext, modifier = Modifier.size(56.dp)) {
                    Icon(LibreIcons.SkipNext, contentDescription = stringResource(R.string.player_next), modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = viewModel::onCycleRepeat) {
                    Icon(
                        imageVector = if (playback.repeatMode == RepeatMode.ONE) LibreIcons.RepeatOne else LibreIcons.Repeat,
                        contentDescription = stringResource(
                            when (playback.repeatMode) {
                                RepeatMode.OFF -> R.string.player_repeat_off
                                RepeatMode.ALL -> R.string.player_repeat_all
                                RepeatMode.ONE -> R.string.player_repeat_one
                            },
                        ),
                        tint = if (playback.repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            uiState.sleepRemainingMs?.let { remaining ->
                Text(
                    text = stringResource(R.string.player_sleep_remaining, formatDuration(remaining / 1000)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            IconButton(onClick = { showQueue = true }) {
                Icon(LibreIcons.Queue, contentDescription = stringResource(R.string.player_queue))
            }
        }
    }

    if (showQueue) {
        QueueSheet(
            queue = playback.queue,
            currentIndex = playback.currentIndex,
            onSkipTo = viewModel::onSkipTo,
            onRemove = viewModel::onRemoveFromQueue,
            onDismiss = { showQueue = false },
        )
    }
    if (showTimer) {
        SleepTimerDialog(
            remainingMs = uiState.sleepRemainingMs,
            onStart = { viewModel.onStartSleepTimer(it); showTimer = false },
            onCancelTimer = { viewModel.onCancelSleepTimer(); showTimer = false },
            onDismiss = { showTimer = false },
        )
    }
    if (showEqualizer) {
        EqualizerSheet(
            state = uiState.equalizer,
            onEnabledChange = viewModel::onEqualizerEnabledChange,
            onPreset = viewModel::onEqualizerPreset,
            onBand = viewModel::onEqualizerBand,
            onDismiss = { showEqualizer = false },
        )
    }
}

/** Carátula incrustada en el audio; si no hay, la miniatura del archivo. */
@Composable
internal fun Artwork(playback: PlaybackState, modifier: Modifier = Modifier) {
    val bitmap = remember(playback.artwork) {
        playback.artwork?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    Box(modifier = modifier.clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        val current = playback.current
        when {
            bitmap != null -> Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            current != null -> LocalMediaThumbnail(
                uri = current.uri,
                kind = if (current.isVideo) MediaKind.VIDEO else MediaKind.AUDIO,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
internal fun SeekBar(playback: PlaybackState, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val duration = playback.durationMs.coerceAtLeast(1).toFloat()
    val shown = if (dragging) dragValue else playback.positionMs.toFloat()
    Column {
        Slider(
            value = shown.coerceIn(0f, duration),
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = { onSeek(dragValue.toLong()); dragging = false },
            valueRange = 0f..duration,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration((shown / 1000).toLong()), style = MaterialTheme.typography.labelMedium)
            Text(formatDuration(playback.durationMs / 1000), style = MaterialTheme.typography.labelMedium)
        }
    }
}
