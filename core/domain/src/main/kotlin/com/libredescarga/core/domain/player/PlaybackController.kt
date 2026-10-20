package com.libredescarga.core.domain.player

import com.libredescarga.core.model.LibraryMedia
import com.libredescarga.core.model.PlaybackState
import com.libredescarga.core.model.RepeatMode
import kotlinx.coroutines.flow.StateFlow

/**
 * Reproductor compartido por la música y los videos. Solo reproduce archivos
 * locales (descargados o ya presentes en el teléfono).
 */
interface PlaybackController {
    val state: StateFlow<PlaybackState>

    /** Conecta con el servicio de reproducción (para mostrar lo que ya está sonando). */
    fun connect()

    /** Reemplaza la cola y empieza por [startIndex], reanudando donde se dejó. */
    fun playQueue(items: List<LibraryMedia>, startIndex: Int)

    fun togglePlayPause()

    fun pause()

    fun seekTo(positionMs: Long)

    fun seekBy(deltaMs: Long)

    fun next()

    fun previous()

    fun skipTo(index: Int)

    fun removeFromQueue(index: Int)

    fun setShuffle(enabled: Boolean)

    fun setRepeatMode(mode: RepeatMode)

    fun setSpeed(speed: Float)

    fun stop()
}

/** Acciones propias del reproductor de video. */
interface VideoPlayback {
    /** Abre un video; si ya es el actual, no lo reinicia. */
    fun open(media: LibraryMedia)

    /** Añade un subtítulo externo (SRT/VTT) al video actual sin perder la posición. */
    fun attachSubtitle(uri: String, mimeType: String)
}

/** Temporizador de apagado: pausa la reproducción al terminar el tiempo. */
interface SleepTimer {
    /** Milisegundos restantes, o null si no hay temporizador. */
    val remainingMs: StateFlow<Long?>

    fun start(minutes: Int)

    fun cancel()
}
