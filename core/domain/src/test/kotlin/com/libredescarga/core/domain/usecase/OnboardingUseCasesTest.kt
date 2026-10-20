package com.libredescarga.core.domain.usecase

import com.libredescarga.core.config.AppConfig
import com.libredescarga.core.model.UserSettings
import com.libredescarga.core.testing.FakeUserPreferencesRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingUseCasesTest {

    private val shouldShowOnboarding = ShouldShowOnboardingUseCase()

    @Test
    fun `se muestra en el primer uso`() {
        assertTrue(shouldShowOnboarding(UserSettings()))
    }

    @Test
    fun `no se muestra si se aceptaron los términos vigentes`() {
        val settings = UserSettings(
            onboardingCompleted = true,
            acceptedTermsVersion = AppConfig.TERMS_VERSION,
        )
        assertFalse(shouldShowOnboarding(settings))
    }

    @Test
    fun `se vuelve a mostrar si los términos cambiaron`() {
        val settings = UserSettings(
            onboardingCompleted = true,
            acceptedTermsVersion = AppConfig.TERMS_VERSION - 1,
        )
        assertTrue(shouldShowOnboarding(settings))
    }

    @Test
    fun `completar la bienvenida guarda la versión de los términos`() = runTest {
        val repository = FakeUserPreferencesRepository()

        CompleteOnboardingUseCase(repository)()

        assertTrue(repository.current.onboardingCompleted)
        assertEquals(AppConfig.TERMS_VERSION, repository.current.acceptedTermsVersion)
        assertFalse(shouldShowOnboarding(repository.current))
    }
}
