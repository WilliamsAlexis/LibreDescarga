package com.libredescarga.core.player

import androidx.media3.common.Player
import kotlinx.coroutines.flow.StateFlow

/** Da acceso al reproductor conectado, para que la pantalla de video dibuje la imagen. */
interface PlayerSurfaceProvider {
    /** El reproductor, o null mientras se conecta con el servicio. */
    val player: StateFlow<Player?>
}
