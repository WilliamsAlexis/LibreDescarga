package com.libredescarga.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.libredescarga.core.domain.repository.UserPreferencesRepository
import com.libredescarga.core.domain.settings.BackupRepository
import com.libredescarga.core.domain.settings.BackupSummary
import com.libredescarga.core.domain.settings.CacheRepository
import com.libredescarga.core.model.AccentColor
import com.libredescarga.core.model.AudioFormat
import com.libredescarga.core.model.ThemeMode
import com.libredescarga.core.model.UserSettings
import com.libredescarga.core.model.VideoQuality
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SettingsMessage {
    data class CacheCleared(val freedBytes: Long) : SettingsMessage
    data object BackupSaved : SettingsMessage
    data class BackupRestored(val summary: BackupSummary) : SettingsMessage
    data object BackupInvalid : SettingsMessage
    data object BackupIoError : SettingsMessage
}

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val cacheBytes: Long? = null,
    val busy: Boolean = false,
    val message: SettingsMessage? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val cache: CacheRepository,
    private val backup: BackupRepository,
) : ViewModel() {

    private data class Local(val cacheBytes: Long? = null, val busy: Boolean = false, val message: SettingsMessage? = null)

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<SettingsUiState> = combine(preferences.settings, local) { settings, local ->
        SettingsUiState(settings, local.cacheBytes, local.busy, local.message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        refreshCacheSize()
    }

    // --- Apariencia y descargas -------------------------------------------------------------------------

    fun onThemeMode(mode: ThemeMode) = launch { preferences.setThemeMode(mode) }

    fun onDynamicColor(enabled: Boolean) = launch { preferences.setDynamicColor(enabled) }

    fun onAccent(color: AccentColor) = launch { preferences.setAccentColor(color) }

    fun onClipboardDetection(enabled: Boolean) = launch { preferences.setClipboardDetection(enabled) }

    fun onWifiOnly(enabled: Boolean) = launch { preferences.setWifiOnly(enabled) }

    fun onMaxConcurrent(count: Int) = launch { preferences.setMaxConcurrentDownloads(count) }

    fun onVideoQuality(quality: VideoQuality) = launch { preferences.setDefaultVideoQuality(quality) }

    fun onAudioFormat(format: AudioFormat) = launch { preferences.setDefaultAudioFormat(format) }

    fun onHideDonationReminders(hide: Boolean) = launch { preferences.setHideDonationReminders(hide) }

    // --- Caché --------------------------------------------------------------------------------------------------

    fun onClearCache() = launch {
        local.update { it.copy(busy = true) }
        val freed = cache.clear()
        local.update { it.copy(busy = false, cacheBytes = 0, message = SettingsMessage.CacheCleared(freed)) }
        refreshCacheSize()
    }

    private fun refreshCacheSize() = launch { local.update { it.copy(cacheBytes = cache.sizeBytes()) } }

    // --- Copia de seguridad ---------------------------------------------------------------------------------

    /** Crea la copia y la entrega a [write], que la guarda en el archivo que eligió el usuario (y se encarga del hilo de E/S). */
    fun onExportBackup(write: suspend (String) -> Unit) = launch {
        local.update { it.copy(busy = true) }
        val message = try {
            val json = backup.export()
            write(json)
            SettingsMessage.BackupSaved
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            SettingsMessage.BackupIoError
        }
        local.update { it.copy(busy = false, message = message) }
    }

    /** Lee el archivo con [read] (que se encarga del hilo de E/S) y lo une con los datos actuales. */
    fun onRestoreBackup(read: suspend () -> String) = launch {
        local.update { it.copy(busy = true) }
        val message = try {
            val json = read()
            SettingsMessage.BackupRestored(backup.import(json))
        } catch (error: CancellationException) {
            throw error
        } catch (_: IllegalArgumentException) {
            SettingsMessage.BackupInvalid
        } catch (_: Exception) {
            SettingsMessage.BackupIoError
        }
        local.update { it.copy(busy = false, message = message) }
    }

    fun onMessageShown() = local.update { it.copy(message = null) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
