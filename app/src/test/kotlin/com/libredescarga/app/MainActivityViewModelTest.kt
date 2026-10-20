package com.libredescarga.app

import com.libredescarga.core.config.AppConfig
import com.libredescarga.core.domain.usecase.ShouldShowOnboardingUseCase
import com.libredescarga.core.model.ThemeMode
import com.libredescarga.core.model.UserSettings
import com.libredescarga.core.testing.FakeIncomingLinkRepository
import com.libredescarga.core.testing.FakeUserPreferencesRepository
import com.libredescarga.core.testing.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MainActivityViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeUserPreferencesRepository()
    private val incomingLinks = FakeIncomingLinkRepository()
    private lateinit var viewModel: MainActivityViewModel

    // Se crea después de que la regla reemplace Dispatchers.Main.
    @Before
    fun setUp() {
        viewModel = MainActivityViewModel(repository, ShouldShowOnboardingUseCase(), incomingLinks)
    }

    @Test
    fun `responder el mensaje de apoyo lo marca como respondido para siempre`() = runTest {
        assertFalse(repository.current.donationPromptAnswered)
        viewModel.onDonationPromptAnswered()
        assertTrue(repository.current.donationPromptAnswered)
    }

    @Test
    fun `un texto compartido con enlace se entrega a Inicio`() {
        viewModel.onSharedText("Te recomiendo esto http://example.com/v/1 ¡mira!")
        assertEquals("https://example.com/v/1", incomingLinks.pendingLink.value)
    }

    @Test
    fun `un texto compartido sin enlace válido se ignora`() {
        viewModel.onSharedText("javascript:alert(1)")
        viewModel.onSharedText("solo palabras")
        viewModel.onSharedText(null)
        assertEquals(null, incomingLinks.pendingLink.value)
    }

    @Test
    fun `empieza en estado de carga`() {
        assertEquals(MainActivityUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `en el primer uso muestra la bienvenida`() = runTest {
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect {} }

        val state = viewModel.uiState.value as MainActivityUiState.Success
        assertTrue(state.showOnboarding)
    }

    @Test
    fun `refleja el tema y omite la bienvenida ya aceptada`() = runTest {
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect {} }

        repository.emit(
            UserSettings(
                themeMode = ThemeMode.DARK,
                onboardingCompleted = true,
                acceptedTermsVersion = AppConfig.TERMS_VERSION,
            ),
        )

        val state = viewModel.uiState.value as MainActivityUiState.Success
        assertFalse(state.showOnboarding)
        assertEquals(ThemeMode.DARK, state.settings.themeMode)
    }
}
