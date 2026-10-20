package com.libredescarga.core.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.libredescarga.core.domain.repository.PlaybackPositionRepository
import com.libredescarga.core.vault.VaultStreams
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Servicio de reproducción (música y videos). Media3 se encarga de la notificación,
 * la pantalla de bloqueo, los auriculares y el foco de audio.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var positions: PlaybackPositionRepository

    @Inject
    lateinit var effects: AudioEffectsController

    @Inject
    lateinit var vaultStreams: VaultStreams

    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** No se cancela al destruir el servicio, para que el último guardado termine. */
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            // Los archivos de la carpeta privada (vault://) se descifran al vuelo al reproducirlos.
            .setMediaSourceFactory(DefaultMediaSourceFactory(VaultAwareDataSourceFactory(this, vaultStreams)))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // Pausa al desconectar los auriculares.
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
            .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) savePosition(player)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                val previous = oldPosition.mediaItem ?: return
                if (oldPosition.mediaItemIndex == newPosition.mediaItemIndex) return
                saveScope.launch {
                    if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                        positions.clear(previous.mediaId) // Terminó: la próxima vez empieza desde el inicio.
                    } else {
                        positions.save(previous.mediaId, oldPosition.positionMs, 0)
                    }
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                effects.attach(audioSessionId)
            }
        })
        effects.attach(player.audioSessionId)

        session = MediaSession.Builder(this, player)
            .apply { openAppIntent()?.let(::setSessionActivity) }
            .setCallback(object : MediaSession.Callback {
                // Los elementos llegan con su id (la URI del archivo); se completa la URI si falta.
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
                    mediaItems.map { item ->
                        if (item.localConfiguration == null) item.buildUpon().setUri(item.mediaId).build() else item
                    }.toMutableList(),
                )
            })
            .build()

        // Guarda la posición periódicamente, por si el sistema cierra la app.
        scope.launch {
            while (isActive) {
                delay(SAVE_INTERVAL_MS)
                if (player.isPlaying) savePosition(player)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Al cerrar la app desde recientes: si no está sonando nada, el servicio termina. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.player?.let(::savePosition)
        effects.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    private fun savePosition(player: Player) {
        val item = player.currentMediaItem ?: return
        val position = player.currentPosition
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0
        saveScope.launch { positions.save(item.mediaId, position, duration) }
    }

    private fun openAppIntent(): PendingIntent? {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private companion object {
        const val SEEK_INCREMENT_MS = 10_000L
        const val SAVE_INTERVAL_MS = 5_000L
    }
}
