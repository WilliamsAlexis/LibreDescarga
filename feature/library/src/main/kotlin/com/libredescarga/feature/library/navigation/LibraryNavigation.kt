package com.libredescarga.feature.library.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.libredescarga.core.model.LibraryMedia
import com.libredescarga.feature.library.LibraryScreen
import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

/** @param onOpenVideo abre el reproductor de video; @param onOpenDuplicates abre "Archivos duplicados" (los resuelve :app). */
fun NavGraphBuilder.libraryScreen(onOpenVideo: (LibraryMedia) -> Unit, onOpenDuplicates: () -> Unit = {}) {
    composable<LibraryRoute> {
        LibraryScreen(onOpenVideo = onOpenVideo, onOpenDuplicates = onOpenDuplicates)
    }
}
