package com.libredescarga.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.libredescarga.core.domain.repository.IncomingLinkRepository
import com.libredescarga.core.domain.repository.UserPreferencesRepository
import com.libredescarga.core.domain.usecase.ShouldShowOnboardingUseCase
import com.libredescarga.core.model.UserSettings
import com.libredescarga.core.security.UrlValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MainActivityUiState {
    data object Loading : MainActivityUiState

    data class Success(
        val settings: UserSettings,
        val showOnboarding: Boolean,
    ) : MainActivityUiState
}

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    private val repository: UserPreferencesRepository,
    shouldShowOnboarding: ShouldShowOnboardingUseCase,
    private val incomingLinks: IncomingLinkRepository,
) : ViewModel() {

    val uiState: StateFlow<MainActivityUiState> = repository.settings
        .map { settings ->
            MainActivityUiState.Success(
                settings = settings,
                showOnboarding = shouldShowOnboarding(settings),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MainActivityUiState.Loading,
        )

    /** El usuario respondió el mensaje de apoyo (donó o dijo "No, gracias"): no vuelve a aparecer nunca. */
    fun onDonationPromptAnswered() {
        viewModelScope.launch { repository.setDonationPromptAnswered() }
    }

    /** Enlace recibido de otra app y aún no atendido por Inicio. */
    val pendingLink: StateFlow<String?> = incomingLinks.pendingLink

    /**
     * Texto recibido con "Compartir con LibreDescarga". Solo se aceptan enlaces
     * http(s); cualquier otro contenido se ignora.
     */
    fun onSharedText(text: String?) {
        if (text.isNullOrBlank()) return
        val candidate = UrlValidator.extractFirstUrl(text) ?: text
        val valid = UrlValidator.validate(candidate) as? UrlValidator.Result.Valid ?: return
        incomingLinks.submit(valid.url)
    }
}
