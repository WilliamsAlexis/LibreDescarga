package com.libredescarga.core.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.concurrent.futures.await
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.libredescarga.core.domain.player.PlaybackController
import com.libredescarga.core.domain.player.VideoPlayback
import com.libredescarga.core.domain.repository.PlaybackPositionRepository
import com.libredescarga.core.domain.usecase.ResumePositionPolicy
import com.libredescarga.core.model.LibraryMedia
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.model.PlaybackState
import com.libredescarga.core.model.QueueEntry
import com.libredescarga.core.model.RepeatMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Conecta la interfaz con [PlaybackService] mediante un MediaController.
 * También expone el [Player] para que el reproductor de video dibuje la imagen.
 */
@Singleton
class MediaControllerPlaybackController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val positions: PlaybackPositionRepository,
) : PlaybackController, VideoPlayback, PlayerSurfaceProvider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private var connecting: Job? = null
    private var ticker: Job? = null
    private val pending = mutableListOf<(MediaController) -> Unit>()

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _player = MutableStateFlow<Player?>(null)

    override val player: StateFlow<Player?> = _player.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val queueChanged = events.containsAny(
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_MEDIA_METADATA_CHANGED,
            )
            publish(rebuildQueue = queueChanged)
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) updateTicker(player.isPlaying)
        }
    }

    override fun connect() {
        withController { }
    }

    override fun playQueue(items: List<LibraryMedia>, startIndex: Int) {
        if (items.isEmpty()) return
        val index = startIndex.coerceIn(items.indices)
        scope.launch {
            val start = ResumePositionPolicy.startPosition(positions.get(items[index].uri), items[index].durationMs)
            withController { controller ->
                controller.setMediaItems(items.map(::toMediaItem), index, start)
                controller.prepare()
                controller.play()
            }
        }
    }

    /** Si [media] ya es el elemento actual, no lo reinicia (por ejemplo, al volver desde el mini reproductor). */
    override fun open(media: LibraryMedia) {
        if (_state.value.current?.uri == media.uri) {
            withController { if (!it.isPlaying) it.play() }
            return
        }
        playQueue(listOf(media), 0)
    }

    override fun attachSubtitle(uri: String, mimeType: String) = withController { controller ->
        val index = controller.currentMediaItemIndex
        val item = controller.currentMediaItem ?: return@withController
        val position = controller.currentPosition
        val subtitle = MediaItem.SubtitleConfiguration.Builder(Uri.parse(uri))
            .setMimeType(mimeType)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        controller.replaceMediaItem(index, item.buildUpon().setSubtitleConfigurations(listOf(subtitle)).build())
        controller.seekTo(index, position)
    }

    override fun togglePlayPause() = withController { if (it.isPlaying) it.pause() else it.play() }

    override fun pause() = withController { it.pause() }

    override fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs.coerceAtLeast(0)) }

    override fun seekBy(deltaMs: Long) = withController {
        val duration = it.duration.takeIf { d -> d != C.TIME_UNSET } ?: Long.MAX_VALUE
        it.seekTo((it.currentPosition + deltaMs).coerceIn(0, duration))
    }

    override fun next() = withController { it.seekToNext() }

    override fun previous() = withController { it.seekToPrevious() }

    override fun skipTo(index: Int) = withController {
        if (index in 0 until it.mediaItemCount) it.seekTo(index, 0)
        it.play()
    }

    override fun removeFromQueue(index: Int) = withController {
        if (index in 0 until it.mediaItemCount) it.removeMediaItem(index)
    }

    override fun setShuffle(enabled: Boolean) = withController { it.shuffleModeEnabled = enabled }

    override fun setRepeatMode(mode: RepeatMode) = withController {
        it.repeatMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
    }

    override fun setSpeed(speed: Float) = withController { it.playbackParameters = PlaybackParameters(speed) }

    override fun stop() = withController {
        it.stop()
        it.clearMediaItems()
    }

    private fun withController(action: (MediaController) -> Unit) {
        controller?.let {
            action(it)
            return
        }
        pending += action
        if (connecting != null) return
        connecting = scope.launch {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val connected = try {
                MediaController.Builder(context, token).buildAsync().await()
            } catch (_: Exception) {
                connecting = null
                pending.clear()
                return@launch
            }
            controller = connected
            _player.value = connected
            connected.addListener(listener)
            publish(rebuildQueue = true)
            updateTicker(connected.isPlaying)
            pending.toList().forEach { it(connected) }
            pending.clear()
        }
    }

    /** Actualiza la posición dos veces por segundo mientras suena. */
    private fun updateTicker(playing: Boolean) {
        ticker?.cancel()
        if (!playing) return
        ticker = scope.launch {
            while (isActive) {
                publish(rebuildQueue = false)
                delay(TICK_MS)
            }
        }
    }

    private fun publish(rebuildQueue: Boolean) {
        val player = controller ?: return
        _state.update { previous ->
            val queue = if (rebuildQueue || previous.queue.size != player.mediaItemCount) {
                (0 until player.mediaItemCount).map { player.getMediaItemAt(it).toEntry() }
            } else {
                previous.queue
            }
            PlaybackState(
                isConnected = true,
                queue = queue,
                currentIndex = if (queue.isEmpty()) -1 else player.currentMediaItemIndex,
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                positionMs = player.currentPosition.coerceAtLeast(0),
                durationMs = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0,
                speed = player.playbackParameters.speed,
                shuffle = player.shuffleModeEnabled,
                repeatMode = when (player.repeatMode) {
                    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                    else -> RepeatMode.OFF
                },
                artwork = player.mediaMetadata.artworkData,
            )
        }
    }

    private fun MediaItem.toEntry() = QueueEntry(
        uri = mediaId,
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.artist?.toString(),
        isVideo = mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_VIDEO,
    )

    private fun toMediaItem(media: LibraryMedia): MediaItem = MediaItem.Builder()
        .setMediaId(media.uri)
        .setUri(media.uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(media.title)
                .setArtist(media.artist)
                .setAlbumTitle(media.album)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(
                    if (media.kind == MediaKind.VIDEO) MediaMetadata.MEDIA_TYPE_VIDEO else MediaMetadata.MEDIA_TYPE_MUSIC,
                )
                .build(),
        )
        .build()

    private companion object {
        const val TICK_MS = 500L
    }
}
