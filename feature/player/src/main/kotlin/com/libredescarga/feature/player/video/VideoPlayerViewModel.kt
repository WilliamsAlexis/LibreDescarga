package com.libredescarga.feature.player.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.libredescarga.core.domain.player.PlaybackController
import com.libredescarga.core.domain.player.VideoPlayback
import com.libredescarga.core.model.LibraryMedia
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.model.PlaybackState
import com.libredescarga.core.player.PlayerSurfaceProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class OrientationMode { AUTO, LANDSCAPE, PORTRAIT }

data class VideoPlayerUiState(
    val playback: PlaybackState = PlaybackState(),
    val controlsVisible: Boolean = true,
    val locked: Boolean = false,
    val orientation: OrientationMode = OrientationMode.AUTO,
    val zoomFilled: Boolean = false,
    /** Si es true, al salir de la pantalla el audio sigue sonando en segundo plano. */
    val keepPlayingInBackground: Boolean = false,
    /** Efecto momentáneo ("-10 s", "+10 s", volumen, brillo) que se muestra en el centro. */
    val feedback: GestureFeedback? = null,
)

sealed interface GestureFeedback {
    data class Seek(val forward: Boolean) : GestureFeedback
    data class Level(val target: VerticalGestureTarget, val percent: Int) : GestureFeedback
    data class Zoom(val filled: Boolean) : GestureFeedback
}

/** Datos mínimos del video, recibidos por la ruta de navegación. */
data class VideoArgs(val uri: String, val title: String, val durationMs: Long)

@HiltViewModel(assistedFactory = VideoPlayerViewModel.Factory::class)
class VideoPlayerViewModel @AssistedInject constructor(
    @Assisted private val args: VideoArgs,
    private val playback: PlaybackController,
    private val video: VideoPlayback,
    surface: PlayerSurfaceProvider,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(args: VideoArgs): VideoPlayerViewModel
    }

    private val local = MutableStateFlow(VideoPlayerUiState())

    val player: StateFlow<Player?> = surface.player

    val uiState: StateFlow<VideoPlayerUiState> = combine(local, playback.state) { state, playbackState ->
        state.copy(playback = playbackState)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VideoPlayerUiState())

    init {
        playback.connect()
        // Reanuda donde quedó; si ya es el video actual (volviendo del mini reproductor), no lo reinicia.
        video.open(
            LibraryMedia(
                uri = args.uri,
                kind = MediaKind.VIDEO,
                title = args.title,
                displayName = args.title,
                folder = "",
                sizeBytes = 0,
                durationMs = args.durationMs,
                dateAdded = 0,
                mimeType = null,
            ),
        )
    }

    fun onTogglePlayPause() = playback.togglePlayPause()

    fun onSeekTo(positionMs: Long) = playback.seekTo(positionMs)

    fun onSeekBy(deltaMs: Long) {
        if (deltaMs == 0L) return
        playback.seekBy(deltaMs)
        showFeedback(GestureFeedback.Seek(forward = deltaMs > 0))
    }

    fun onSpeedSelected(speed: Float) = playback.setSpeed(speed)

    fun onToggleControls() = local.update { if (it.locked) it else it.copy(controlsVisible = !it.controlsVisible) }

    fun onControlsVisibleChange(visible: Boolean) = local.update { it.copy(controlsVisible = visible) }

    fun onToggleLock() = local.update {
        it.copy(locked = !it.locked, controlsVisible = it.locked) // Al bloquear se ocultan; al desbloquear aparecen.
    }

    fun onOrientationSelected(mode: OrientationMode) = local.update { it.copy(orientation = mode) }

    fun onCycleOrientation() = local.update {
        val next = when (it.orientation) {
            OrientationMode.AUTO -> OrientationMode.LANDSCAPE
            OrientationMode.LANDSCAPE -> OrientationMode.PORTRAIT
            OrientationMode.PORTRAIT -> OrientationMode.AUTO
        }
        it.copy(orientation = next)
    }

    fun onPinchZoom(accumulatedScale: Float) {
        val filled = GesturePolicy.zoomFilled(accumulatedScale)
        if (filled == local.value.zoomFilled) return
        local.update { it.copy(zoomFilled = filled) }
        showFeedback(GestureFeedback.Zoom(filled))
    }

    fun onLevelChanged(target: VerticalGestureTarget, level: Float) =
        showFeedback(GestureFeedback.Level(target, (level * 100).toInt().coerceIn(0, 100)))

    fun onKeepPlayingInBackgroundChange(keep: Boolean) = local.update { it.copy(keepPlayingInBackground = keep) }

    fun onFeedbackShown() = local.update { it.copy(feedback = null) }

    fun onSubtitleFile(uri: String, fileName: String) {
        val mime = if (fileName.endsWith(".vtt", ignoreCase = true)) "text/vtt" else "application/x-subrip"
        video.attachSubtitle(uri, mime)
    }

    /** Al salir de la pantalla: pausa, salvo que se haya pedido seguir sonando o esté en ventana flotante. */
    fun onLeave(inPictureInPicture: Boolean) {
        if (!local.value.keepPlayingInBackground && !inPictureInPicture) playback.pause()
    }

    private fun showFeedback(feedback: GestureFeedback) = local.update { it.copy(feedback = feedback) }
}
