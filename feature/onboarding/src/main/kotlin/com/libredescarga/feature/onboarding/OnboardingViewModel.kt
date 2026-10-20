package com.libredescarga.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.libredescarga.core.domain.usecase.CompleteOnboardingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val termsAccepted: Boolean = false,
    val isSaving: Boolean = false,
    val finished: Boolean = false,
) {
    val canFinish: Boolean get() = termsAccepted && !isSaving && !finished
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val completeOnboarding: CompleteOnboardingUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onTermsAcceptedChange(accepted: Boolean) {
        _uiState.update { it.copy(termsAccepted = accepted) }
    }

    /** Solo termina si el usuario aceptó los términos de uso. */
    fun onFinish() {
        if (!_uiState.value.canFinish) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            completeOnboarding()
            _uiState.update { it.copy(isSaving = false, finished = true) }
        }
    }
}
