package com.libredescarga.feature.player.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.libredescarga.core.domain.player.PlaybackController
import com.libredescarga.core.domain.player.SleepTimer
import com.libredescarga.core.domain.repository.FavoritesRepository
import com.libredescarga.core.model.PlaybackState
import com.libredescarga.core.model.RepeatMode
import com.libredescarga.core.player.AudioEffectsController
import com.libredescarga.core.player.EqualizerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MusicPlayerUiState(
    val playback: PlaybackState = PlaybackState(),
    val isFavorite: Boolean = false,
    /** Milisegundos que faltan para que se apague la música, o null sin temporizador. */
    val sleepRemainingMs: Long? = null,
    val equalizer: EqualizerState = EqualizerState(),
)

/** Estado y acciones del reproductor de música, compartido por la pantalla completa y el mini reproductor. */
@HiltViewModel
class MusicPlayerViewModel @Inject constructor(
    private val playback: PlaybackController,
    private val sleepTimer: SleepTimer,
    private val effects: AudioEffectsController,
    private val favorites: FavoritesRepository,
) : ViewModel() {

    val uiState: StateFlow<MusicPlayerUiState> = combine(
        playback.state,
        sleepTimer.remainingMs,
        effects.state,
        favorites.favorites,
    ) { state, remaining, equalizer, favoriteUris ->
        MusicPlayerUiState(
            playback = state,
            isFavorite = state.current?.uri in favoriteUris,
            sleepRemainingMs = remaining,
            equalizer = equalizer,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MusicPlayerUiState())

    init {
        // Si el servicio ya estaba sonando (app reabierta), se reconecta para mostrarlo.
        playback.connect()
    }

    fun onTogglePlayPause() = playback.togglePlayPause()

    fun onNext() = playback.next()

    fun onPrevious() = playback.previous()

    fun onSeekTo(positionMs: Long) = playback.seekTo(positionMs)

    fun onSkipTo(index: Int) = playback.skipTo(index)

    fun onRemoveFromQueue(index: Int) = playback.removeFromQueue(index)

    fun onToggleShuffle() = playback.setShuffle(!uiState.value.playback.shuffle)

    /** Sin repetir → repetir toda la cola → repetir una canción. */
    fun onCycleRepeat() = playback.setRepeatMode(
        when (uiState.value.playback.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        },
    )

    fun onStop() = playback.stop()

    fun onToggleFavorite() {
        val uri = uiState.value.playback.current?.uri ?: return
        viewModelScope.launch { favorites.toggle(uri) }
    }

    fun onStartSleepTimer(minutes: Int) = sleepTimer.start(minutes)

    fun onCancelSleepTimer() = sleepTimer.cancel()

    fun onEqualizerEnabledChange(enabled: Boolean) = effects.setEnabled(enabled)

    fun onEqualizerPreset(index: Int) = effects.usePreset(index)

    fun onEqualizerBand(band: Int, levelMb: Int) = effects.setBandLevel(band, levelMb)

    companion object {
        /** Minutos ofrecidos para el temporizador de apagado. */
        val SLEEP_OPTIONS_MINUTES = listOf(5, 10, 15, 30, 45, 60, 90)
    }
}
