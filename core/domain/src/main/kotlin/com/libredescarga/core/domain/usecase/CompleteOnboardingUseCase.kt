package com.libredescarga.core.domain.usecase

import com.libredescarga.core.config.AppConfig
import com.libredescarga.core.domain.repository.UserPreferencesRepository
import javax.inject.Inject

/** Marca la bienvenida como completada y registra la versión de los términos aceptada. */
class CompleteOnboardingUseCase @Inject constructor(
    private val repository: UserPreferencesRepository,
) {
    suspend operator fun invoke() {
        repository.completeOnboarding(AppConfig.TERMS_VERSION)
    }
}
