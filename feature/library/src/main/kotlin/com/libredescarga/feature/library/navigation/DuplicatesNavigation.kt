package com.libredescarga.feature.library.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.libredescarga.feature.library.duplicates.DuplicatesScreen
import kotlinx.serialization.Serializable

/** Pantalla "Archivos duplicados" (se abre desde la Biblioteca). */
@Serializable
data object DuplicatesRoute

fun NavGraphBuilder.duplicatesScreen(onBack: () -> Unit) {
    composable<DuplicatesRoute> {
        DuplicatesScreen(onBack = onBack)
    }
}
