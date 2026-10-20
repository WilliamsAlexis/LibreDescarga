package com.libredescarga.core.player

import com.libredescarga.core.domain.player.PlaybackController
import com.libredescarga.core.domain.player.SleepTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Temporizador de apagado: pausa la música cuando termina la cuenta atrás. */
@Singleton
class PlaybackSleepTimer @Inject constructor(
    private val controller: PlaybackController,
) : SleepTimer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private val _remainingMs = MutableStateFlow<Long?>(null)
    override val remainingMs: StateFlow<Long?> = _remainingMs.asStateFlow()

    override fun start(minutes: Int) {
        job?.cancel()
        val endAt = System.currentTimeMillis() + minutes * 60_000L
        job = scope.launch {
            while (true) {
                val left = endAt - System.currentTimeMillis()
                if (left <= 0) break
                _remainingMs.value = left
                delay(minOf(left, TICK_MS))
            }
            _remainingMs.value = null
            controller.pause()
        }
    }

    override fun cancel() {
        job?.cancel()
        job = null
        _remainingMs.value = null
    }

    private companion object {
        const val TICK_MS = 1_000L
    }
}
