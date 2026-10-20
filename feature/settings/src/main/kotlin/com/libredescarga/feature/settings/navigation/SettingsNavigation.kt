package com.libredescarga.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.libredescarga.feature.settings.AboutScreen
import com.libredescarga.feature.settings.LegalScreen
import com.libredescarga.feature.settings.LicensesScreen
import com.libredescarga.feature.settings.PrivacyScreen
import com.libredescarga.feature.settings.SettingsScreen
import com.libredescarga.feature.settings.SitesScreen
import com.libredescarga.feature.settings.SupportScreen
import com.libredescarga.feature.settings.UpdatesScreen
import kotlinx.serialization.Serializable

/** Grupo de pantallas de Ajustes: la pestaña inferior se mantiene seleccionada en todas. */
@Serializable
data object SettingsGraph

@Serializable
data object SettingsRoute

@Serializable
data object SupportRoute

@Serializable
data object AboutRoute

@Serializable
data object LicensesRoute

@Serializable
data object LegalRoute

@Serializable
data object PrivacyRoute

@Serializable
data object SitesRoute

@Serializable
data object UpdatesRoute

fun NavGraphBuilder.settingsGraph(navController: NavController) {
    val back: () -> Unit = { navController.popBackStack() }
    navigation<SettingsGraph>(startDestination = SettingsRoute) {
        composable<SettingsRoute> {
            SettingsScreen(
                onOpenSupport = { navController.navigate(SupportRoute) },
                onOpenAbout = { navController.navigate(AboutRoute) },
                onOpenSites = { navController.navigate(SitesRoute) },
                onOpenUpdates = { navController.navigate(UpdatesRoute) },
            )
        }
        composable<SupportRoute> { SupportScreen(onBack = back) }
        composable<AboutRoute> {
            AboutScreen(
                onBack = back,
                onOpenLicenses = { navController.navigate(LicensesRoute) },
                onOpenLegal = { navController.navigate(LegalRoute) },
                onOpenPrivacy = { navController.navigate(PrivacyRoute) },
            )
        }
        composable<LicensesRoute> { LicensesScreen(onBack = back) }
        composable<LegalRoute> { LegalScreen(onBack = back) }
        composable<PrivacyRoute> { PrivacyScreen(onBack = back) }
        composable<SitesRoute> { SitesScreen(onBack = back) }
        composable<UpdatesRoute> { UpdatesScreen(onBack = back) }
    }
}
