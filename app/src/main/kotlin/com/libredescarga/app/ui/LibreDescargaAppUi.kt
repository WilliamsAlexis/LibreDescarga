package com.libredescarga.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.libredescarga.app.navigation.AppNavHost
import com.libredescarga.app.navigation.TopLevelDestination
import com.libredescarga.app.navigation.navigateToTopLevel
import com.libredescarga.feature.browser.navigation.BrowserRoute
import com.libredescarga.feature.onboarding.navigation.OnboardingRoute
import com.libredescarga.feature.player.music.MiniPlayer
import com.libredescarga.feature.settings.DonationPromptDialog
import com.libredescarga.feature.settings.navigation.UpdatesRoute
import com.libredescarga.feature.player.navigation.NowPlayingRoute
import com.libredescarga.feature.player.navigation.VideoPlayerRoute

/**
 * Estructura principal: barra inferior en teléfonos y riel de navegación en
 * tablets y plegables (según el WindowSizeClass actual).
 */
@Composable
fun LibreDescargaAppUi(
    showOnboarding: Boolean,
    hasPendingLink: Boolean,
    openUpdates: Boolean,
    onOpenUpdatesHandled: () -> Unit,
    showDonationPrompt: Boolean,
    onDonationPromptAnswered: () -> Unit,
) {
    val navController = rememberNavController()
    // El destino inicial se fija una sola vez, aunque la preferencia cambie después.
    val startWithOnboarding = rememberSaveable { showOnboarding }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val hideNavigation = currentDestination.isOnboarding(fallback = startWithOnboarding)

    // Un enlace compartido desde otra app se atiende en Inicio (tras la bienvenida).
    LaunchedEffect(hasPendingLink, hideNavigation, currentDestination) {
        if (hasPendingLink && !hideNavigation && currentDestination != null &&
            !currentDestination.isInHierarchy(TopLevelDestination.HOME)
        ) {
            navController.navigateToTopLevel(TopLevelDestination.HOME)
        }
    }
    // Al tocar el aviso de versión nueva se abre Ajustes → Actualizaciones (tras la bienvenida).
    LaunchedEffect(openUpdates, hideNavigation, currentDestination) {
        if (openUpdates && !hideNavigation && currentDestination != null) {
            navController.navigateToTopLevel(TopLevelDestination.SETTINGS)
            navController.navigate(UpdatesRoute)
            onOpenUpdatesHandled()
        }
    }
    val isVideoPlayer =currentDestination?.hasRoute(VideoPlayerRoute::class) == true
    val isBrowser = currentDestination?.hasRoute(BrowserRoute::class) == true
    val isPlayerScreen = isVideoPlayer || isBrowser || currentDestination?.hasRoute(NowPlayingRoute::class) == true
    val layoutType = if (hideNavigation || isVideoPlayer || isBrowser) {
        NavigationSuiteType.None
    } else {
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    }

    NavigationSuiteScaffold(
        layoutType = layoutType,
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                val selected = currentDestination.isInHierarchy(destination)
                item(
                    selected = selected,
                    onClick = { navController.navigateToTopLevel(destination) },
                    icon = {
                        Icon(
                            imageVector = if (selected) {
                                destination.selectedIcon
                            } else {
                                destination.unselectedIcon
                            },
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(destination.labelRes)) },
                )
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppNavHost(
                navController = navController,
                startWithOnboarding = startWithOnboarding,
                modifier = Modifier.weight(1f),
            )
            // Mini reproductor persistente; se oculta en la bienvenida y en las pantallas de reproducción.
            if (!hideNavigation && !isPlayerScreen) {
                MiniPlayer(
                    onOpenMusic = { navController.navigate(NowPlayingRoute) },
                    onOpenVideo = { uri, title, durationMs ->
                        navController.navigate(VideoPlayerRoute(uri, title, durationMs))
                    },
                )
            }
        }
    }

    // Mensaje amable y descartable tras 20 descargas. Se responda lo que se responda, no vuelve a salir.
    if (showDonationPrompt && !hideNavigation && !isPlayerScreen && currentDestination != null) {
        DonationPromptDialog(onDonate = onDonationPromptAnswered, onDismiss = onDonationPromptAnswered)
    }
}

private fun NavDestination?.isInHierarchy(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.routeClass) } == true

private fun NavDestination?.isOnboarding(fallback: Boolean): Boolean =
    this?.hasRoute(OnboardingRoute::class) ?: fallback
