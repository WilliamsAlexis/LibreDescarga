package com.libredescarga.feature.home.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.libredescarga.feature.home.HomeScreen
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

/**
 * Hoja de análisis que muestra Inicio. La aporta :app (módulo :feature:analysis),
 * para que los módulos de funciones no dependan entre sí.
 */
typealias AnalysisSheetSlot = @Composable (
    url: String,
    onDismiss: () -> Unit,
    onQueued: (count: Int) -> Unit,
) -> Unit

/**
 * @param onOpenSite abre un sitio de la cuadrícula; devuelve false si no se pudo abrir.
 */
fun NavGraphBuilder.homeScreen(
    analysisSheet: AnalysisSheetSlot,
    onOpenSite: (url: String) -> Boolean,
    onOpenBrowser: () -> Unit,
) {
    composable<HomeRoute> {
        HomeScreen(analysisSheet = analysisSheet, onOpenSite = onOpenSite, onOpenBrowser = onOpenBrowser)
    }
}
