package com.libredescarga.feature.vault.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.libredescarga.core.model.VaultItem
import com.libredescarga.feature.vault.VaultScreen
import kotlinx.serialization.Serializable

@Serializable
data object VaultRoute

/** @param onOpenVideo abre el reproductor de video con un archivo de la carpeta privada. */
fun NavGraphBuilder.vaultScreen(onOpenVideo: (VaultItem) -> Unit) {
    composable<VaultRoute> {
        VaultScreen(onOpenVideo = onOpenVideo)
    }
}
