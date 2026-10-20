package com.libredescarga.app.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.libredescarga.app.R
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.feature.downloads.navigation.DownloadsRoute
import com.libredescarga.feature.home.navigation.HomeRoute
import com.libredescarga.feature.library.navigation.LibraryRoute
import com.libredescarga.feature.settings.navigation.SettingsGraph
import com.libredescarga.feature.vault.navigation.VaultRoute
import kotlin.reflect.KClass

/** Destinos de la navegación inferior (o lateral en tablets). */
enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    @param:StringRes val labelRes: Int,
) {
    HOME(
        route = HomeRoute,
        routeClass = HomeRoute::class,
        selectedIcon = LibreIcons.Home,
        unselectedIcon = LibreIcons.HomeOutlined,
        labelRes = R.string.nav_home,
    ),
    DOWNLOADS(
        route = DownloadsRoute,
        routeClass = DownloadsRoute::class,
        selectedIcon = LibreIcons.Downloads,
        unselectedIcon = LibreIcons.DownloadsOutlined,
        labelRes = R.string.nav_downloads,
    ),
    LIBRARY(
        route = LibraryRoute,
        routeClass = LibraryRoute::class,
        selectedIcon = LibreIcons.Library,
        unselectedIcon = LibreIcons.LibraryOutlined,
        labelRes = R.string.nav_library,
    ),
    VAULT(
        route = VaultRoute,
        routeClass = VaultRoute::class,
        selectedIcon = LibreIcons.Vault,
        unselectedIcon = LibreIcons.VaultOutlined,
        labelRes = R.string.nav_vault,
    ),
    SETTINGS(
        route = SettingsGraph,
        routeClass = SettingsGraph::class,
        selectedIcon = LibreIcons.Settings,
        unselectedIcon = LibreIcons.SettingsOutlined,
        labelRes = R.string.nav_settings,
    ),
}
