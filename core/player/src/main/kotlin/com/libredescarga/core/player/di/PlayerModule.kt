package com.libredescarga.core.player.di

import com.libredescarga.core.domain.player.PlaybackController
import com.libredescarga.core.domain.player.SleepTimer
import com.libredescarga.core.domain.player.VideoPlayback
import com.libredescarga.core.player.MediaControllerPlaybackController
import com.libredescarga.core.player.PlayerSurfaceProvider
import com.libredescarga.core.player.PlaybackSleepTimer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Conecta los contratos del reproductor con la clase que realmente reproduce (una sola pieza hace los tres trabajos). */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class PlayerModule {

    // Controles de reproducción: play, pausa, siguiente, anterior.
    @Binds
    abstract fun bindPlaybackController(impl: MediaControllerPlaybackController): PlaybackController

    // Reproducción de video en pantalla.
    @Binds
    abstract fun bindVideoPlayback(impl: MediaControllerPlaybackController): VideoPlayback

    // Superficie donde se dibuja el video.
    @Binds
    abstract fun bindPlayerSurfaceProvider(impl: MediaControllerPlaybackController): PlayerSurfaceProvider

    // Temporizador para apagar la música después de un tiempo.
    @Binds
    abstract fun bindSleepTimer(impl: PlaybackSleepTimer): SleepTimer
}
