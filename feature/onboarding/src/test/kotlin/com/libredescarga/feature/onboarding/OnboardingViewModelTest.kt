package com.libredescarga.feature.onboarding

import com.libredescarga.core.config.AppConfig
import com.libredescarga.core.domain.usecase.CompleteOnboardingUseCase
import com.libredescarga.core.testing.FakeUserPreferencesRepository
import com.libredescarga.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeUserPreferencesRepository()
    private lateinit var viewModel: OnboardingViewModel

    // Se crea después de que la regla reemplace Dispatchers.Main.
    @Before
    fun setUp() {
        viewModel = OnboardingViewModel(CompleteOnboardingUseCase(repository))
    }

    @Test
    fun `al inicio los términos no están aceptados`() {
        val state = viewModel.uiState.value
        assertFalse(state.termsAccepted)
        assertFalse(state.canFinish)
        assertFalse(state.finished)
    }

    @Test
    fun `no se puede terminar sin aceptar los términos`() = runTest {
        viewModel.onFinish()

        assertFalse(viewModel.uiState.value.finished)
        assertFalse(repository.current.onboardingCompleted)
    }

    @Test
    fun `al aceptar y terminar se guarda la bienvenida`() = runTest {
        viewModel.onTermsAcceptedChange(true)
        assertTrue(viewModel.uiState.value.canFinish)

        viewModel.onFinish()

        assertTrue(viewModel.uiState.value.finished)
        assertTrue(repository.current.onboardingCompleted)
        assertEquals(AppConfig.TERMS_VERSION, repository.current.acceptedTermsVersion)
    }

    @Test
    fun `desmarcar los términos vuelve a bloquear el botón`() {
        viewModel.onTermsAcceptedChange(true)
        viewModel.onTermsAcceptedChange(false)

        assertFalse(viewModel.uiState.value.canFinish)
    }
}
