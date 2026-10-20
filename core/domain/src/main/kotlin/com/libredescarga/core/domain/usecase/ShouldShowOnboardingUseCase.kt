package com.libredescarga.core.domain.usecase

import com.libredescarga.core.config.AppConfig
import com.libredescarga.core.model.UserSettings
import javax.inject.Inject

/**
 * La bienvenida se muestra si nunca se completó o si los términos de uso
 * cambiaron desde la última vez que el usuario los aceptó.
 */
class ShouldShowOnboardingUseCase @Inject constructor() {
    operator fun invoke(settings: UserSettings): Boolean =
        !settings.onboardingCompleted || settings.acceptedTermsVersion < AppConfig.TERMS_VERSION
}
