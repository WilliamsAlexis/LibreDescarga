package com.libredescarga.feature.downloads.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.libredescarga.feature.downloads.DownloadsScreen
import kotlinx.serialization.Serializable

/** Dirección de la pantalla de descargas. */
@Serializable
data object DownloadsRoute

/** Registra la pantalla de descargas en el mapa de navegación. */
fun NavGraphBuilder.downloadsScreen(
    onOpenVideo: (uri: String, title: String, durationMs: Long) -> Unit,
    onOpenImages: (uris: List<String>, titles: List<String>, index: Int) -> Unit,
) {
    composable<DownloadsRoute> {
        DownloadsScreen(onOpenVideo = onOpenVideo, onOpenImages = onOpenImages)
    }
}
