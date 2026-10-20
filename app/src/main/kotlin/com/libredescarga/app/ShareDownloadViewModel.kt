package com.libredescarga.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.libredescarga.core.domain.repository.UserPreferencesRepository
import com.libredescarga.core.domain.usecase.EnqueueDownloadsUseCase
import com.libredescarga.core.domain.usecase.ImageDownloads
import com.libredescarga.core.domain.usecase.ShouldShowOnboardingUseCase
import com.libredescarga.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Estado de la ventana que aparece al compartir un enlace: mientras se leen los ajustes no se muestra nada. */
sealed interface ShareUiState {
    data object Loading : ShareUiState

    /** [needsOnboarding]: si aún no se aceptó la bienvenida y los términos, se abre la app completa. */
    data class Ready(val settings: UserSettings, val needsOnboarding: Boolean) : ShareUiState
}

/** Lógica de "Compartir con LibreDescarga": tema, bienvenida pendiente y descarga directa de imágenes. */
@HiltViewModel
class ShareDownloadViewModel @Inject constructor(
    preferences: UserPreferencesRepository,
    shouldShowOnboarding: ShouldShowOnboardingUseCase,
    private val enqueueDownloads: EnqueueDownloadsUseCase,
) : ViewModel() {

    val uiState: StateFlow<ShareUiState> = preferences.settings
        .map { settings -> ShareUiState.Ready(settings, shouldShowOnboarding(settings)) as ShareUiState }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShareUiState.Loading)

    /** Descarga directa de un enlace que es una imagen (no hay nada que elegir). Devuelve cuántas se agregaron a la cola. */
    suspend fun downloadImage(url: String): Int {
        val request = ImageDownloads.request(url) ?: return 0
        return enqueueDownloads(listOf(request))
    }
}
