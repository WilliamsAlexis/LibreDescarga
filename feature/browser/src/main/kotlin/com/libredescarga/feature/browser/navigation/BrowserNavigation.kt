package com.libredescarga.feature.browser.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.libredescarga.feature.browser.BrowserAnalysisSlot
import com.libredescarga.feature.browser.BrowserScreen
import kotlinx.serialization.Serializable

/** [url] vacío abre el navegador con la pestaña actual (o una en blanco). */
@Serializable
data class BrowserRoute(val url: String? = null)

fun NavGraphBuilder.browserScreen(analysisSheet: BrowserAnalysisSlot, onClose: () -> Unit) {
    composable<BrowserRoute> { entry ->
        BrowserScreen(
            initialUrl = entry.toRoute<BrowserRoute>().url,
            analysisSheet = analysisSheet,
            onClose = onClose,
        )
    }
}
