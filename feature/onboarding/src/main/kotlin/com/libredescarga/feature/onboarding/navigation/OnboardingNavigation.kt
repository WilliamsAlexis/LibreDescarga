package com.libredescarga.feature.onboarding.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.libredescarga.feature.onboarding.OnboardingScreen
import kotlinx.serialization.Serializable

/** Dirección de la pantalla de bienvenida (se muestra solo la primera vez). */
@Serializable
data object OnboardingRoute

/** Registra la bienvenida en el mapa de navegación; al terminar llama a onFinished. */
fun NavGraphBuilder.onboardingScreen(onFinished: () -> Unit) {
    composable<OnboardingRoute> {
        OnboardingScreen(onFinished = onFinished)
    }
}
