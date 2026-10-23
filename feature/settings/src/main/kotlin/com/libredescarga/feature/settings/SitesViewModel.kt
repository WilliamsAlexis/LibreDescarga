package com.libredescarga.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.libredescarga.core.domain.repository.SiteRepository
import com.libredescarga.core.domain.repository.UserPreferencesRepository
import com.libredescarga.core.model.MediaError
import com.libredescarga.core.model.Site
import com.libredescarga.core.security.DomainRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SitesError { InvalidSite, InvalidDomain }

data class SitesUiState(
    /** Todos los sitios en el orden de la cuadrícula de Inicio, incluidos los desactivados. */
    val sites: List<Site> = emptyList(),
    /** Sitios que el usuario decidió bloquear. */
    val blockedDomains: List<String> = emptyList(),
    val error: SitesError? = null,
)

@HiltViewModel
class SitesViewModel @Inject constructor(
    private val sitesRepository: SiteRepository,
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    private val error = MutableStateFlow<SitesError?>(null)

    val uiState: StateFlow<SitesUiState> = combine(
        sitesRepository.sites,
        preferences.settings.map { it.userBlockedDomains.sorted() },
        error,
    ) { sites, blocked, error -> SitesUiState(sites, blocked, error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SitesUiState())

    fun onSetEnabled(site: Site, enabled: Boolean) {
        viewModelScope.launch { sitesRepository.setEnabled(site.id, enabled) }
    }

    fun onMove(site: Site, up: Boolean) {
        viewModelScope.launch { sitesRepository.move(site.id, up) }
    }

    /** Solo se pueden quitar los sitios propios; los predefinidos se desactivan. */
    fun onRemove(site: Site) {
        if (!site.isCustom) return
        viewModelScope.launch { sitesRepository.removeSite(site.id) }
    }

    fun onAddSite(name: String, url: String) {
        viewModelScope.launch {
            try {
                sitesRepository.addCustomSite(name, url)
                error.value = null
            } catch (_: MediaError) {
                error.value = SitesError.InvalidSite
            }
        }
    }

    fun onBlockDomain(input: String) {
        val domain = DomainRules.normalize(input)
        if (domain == null) {
            error.value = SitesError.InvalidDomain
            return
        }
        error.value = null
        viewModelScope.launch {
            preferences.setUserBlockedDomains(preferences.settings.first().userBlockedDomains + domain)
        }
    }

    fun onUnblockDomain(domain: String) {
        viewModelScope.launch {
            preferences.setUserBlockedDomains(preferences.settings.first().userBlockedDomains - domain)
        }
    }
}
