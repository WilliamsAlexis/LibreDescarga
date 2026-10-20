package com.libredescarga.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.libredescarga.feature.analysis.MediaAnalysisSheet
import com.libredescarga.feature.browser.navigation.BrowserRoute
import com.libredescarga.feature.browser.navigation.browserScreen
import com.libredescarga.feature.downloads.navigation.downloadsScreen
import com.libredescarga.feature.home.navigation.HomeRoute
import com.libredescarga.feature.home.navigation.homeScreen
import com.libredescarga.feature.library.navigation.DuplicatesRoute
import com.libredescarga.feature.library.navigation.duplicatesScreen
import com.libredescarga.feature.library.navigation.libraryScreen
import com.libredescarga.feature.onboarding.navigation.OnboardingRoute
import com.libredescarga.feature.onboarding.navigation.onboardingScreen
import com.libredescarga.feature.player.navigation.ImageViewerRoute
import com.libredescarga.feature.player.navigation.VideoPlayerRoute
import com.libredescarga.feature.player.navigation.imageViewerScreen
import com.libredescarga.feature.player.navigation.nowPlayingScreen
import com.libredescarga.feature.player.navigation.videoPlayerScreen
import com.libredescarga.feature.settings.navigation.settingsGraph
import com.libredescarga.feature.vault.navigation.vaultScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    startWithOnboarding: Boolean,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = if (startWithOnboarding) OnboardingRoute else HomeRoute,
        modifier = modifier,
    ) {
        onboardingScreen(
            onFinished = {
                navController.navigate(HomeRoute) {
                    popUpTo<OnboardingRoute> { inclusive = true }
                }
            },
        )
        homeScreen(
            analysisSheet = { url, onDismiss, onQueued ->
                MediaAnalysisSheet(url = url, onDismiss = onDismiss, onQueued = onQueued)
            },
            // Los sitios y las búsquedas por categoría se abren en el navegador integrado.
            onOpenSite = { url ->
                navController.navigate(BrowserRoute(url))
                true
            },
            onOpenBrowser = { navController.navigate(BrowserRoute()) },
        )
        browserScreen(
            analysisSheet = { url, onDismiss, onQueued ->
                MediaAnalysisSheet(url = url, onDismiss = onDismiss, onQueued = onQueued)
            },
            onClose = { navController.popBackStack() },
        )
        downloadsScreen(
            onOpenVideo = { uri, title, durationMs -> navController.navigate(VideoPlayerRoute(uri, title, durationMs)) },
            onOpenImages = { uris, titles, index -> navController.navigate(ImageViewerRoute(uris, titles, index)) },
        )
        duplicatesScreen(onBack = { navController.popBackStack() })
        libraryScreen(
            onOpenDuplicates = { navController.navigate(DuplicatesRoute) },
            onOpenVideo = { media ->
                navController.navigate(VideoPlayerRoute(media.uri, media.title, media.durationMs))
            },
        )
        videoPlayerScreen(onBack = { navController.popBackStack() })
        imageViewerScreen(onBack = { navController.popBackStack() })
        nowPlayingScreen(onBack = { navController.popBackStack() })
        vaultScreen(
            onOpenVideo = { item ->
                navController.navigate(VideoPlayerRoute(item.uri, item.name.substringBeforeLast('.'), item.durationMs))
            },
        )
        settingsGraph(navController)
    }
}

/** Cambia de pestaña conservando el estado de cada una. */
fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        // Inicio es siempre la raíz una vez terminada la bienvenida.
        popUpTo<HomeRoute> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
