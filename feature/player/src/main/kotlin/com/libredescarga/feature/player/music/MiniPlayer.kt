package com.libredescarga.feature.player.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.feature.player.R

/**
 * Mini reproductor persistente, sobre la barra de navegación.
 * @param onOpen abre la pantalla completa (música) o el reproductor de video.
 */
@Composable
fun MiniPlayer(
    onOpenMusic: () -> Unit,
    onOpenVideo: (uri: String, title: String, durationMs: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MusicPlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playback = uiState.playback
    val current = playback.current

    AnimatedVisibility(
        visible = current != null,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier,
    ) {
        if (current == null) return@AnimatedVisibility
        Surface(
            tonalElevation = 3.dp,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (current.isVideo) onOpenVideo(current.uri, current.title, playback.durationMs) else onOpenMusic()
                        }
                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Artwork(playback, Modifier.size(44.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(current.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        current.artist?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                    IconButton(onClick = viewModel::onTogglePlayPause) {
                        Icon(
                            imageVector = if (playback.isPlaying) LibreIcons.Pause else LibreIcons.Play,
                            contentDescription = stringResource(if (playback.isPlaying) R.string.player_pause else R.string.player_play),
                        )
                    }
                    IconButton(onClick = viewModel::onStop) {
                        Icon(LibreIcons.Close, contentDescription = stringResource(R.string.player_stop))
                    }
                }
                LinearProgressIndicator(
                    progress = { if (playback.durationMs > 0) playback.positionMs.toFloat() / playback.durationMs else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
