package com.libredescarga.feature.player.navigation

import com.libredescarga.feature.player.image.ImageViewerScreen
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.libredescarga.feature.player.music.NowPlayingScreen
import com.libredescarga.feature.player.video.VideoArgs
import com.libredescarga.feature.player.video.VideoPlayerScreen
import kotlinx.serialization.Serializable

/** Dirección de la pantalla del video: lleva el archivo, el título y la duración. */
@Serializable
data class VideoPlayerRoute(val uri: String, val title: String, val durationMs: Long)

/** Dirección del visor de imágenes: las direcciones de las imágenes, sus nombres y cuál mostrar primero. */
@Serializable
data class ImageViewerRoute(val uris: List<String>, val titles: List<String>, val index: Int)

/** Dirección de la pantalla "Reproduciendo ahora" (música). */
@Serializable
data object NowPlayingRoute

/** Registra la pantalla del reproductor de video en el mapa de navegación. */
fun NavGraphBuilder.videoPlayerScreen(onBack: () -> Unit) {
    composable<VideoPlayerRoute> { entry ->
        val route = entry.toRoute<VideoPlayerRoute>()
        VideoPlayerScreen(args = VideoArgs(route.uri, route.title, route.durationMs), onBack = onBack)
    }
}

/** Registra el visor de imágenes en el mapa de navegación. */
fun NavGraphBuilder.imageViewerScreen(onBack: () -> Unit) {
    composable<ImageViewerRoute> { entry ->
        val route = entry.toRoute<ImageViewerRoute>()
        ImageViewerScreen(uris = route.uris, titles = route.titles, startIndex = route.index, onBack = onBack)
    }
}

/** Registra la pantalla de música en el mapa de navegación. */
fun NavGraphBuilder.nowPlayingScreen(onBack: () -> Unit) {
    composable<NowPlayingRoute> {
        NowPlayingScreen(onBack = onBack)
    }
}
