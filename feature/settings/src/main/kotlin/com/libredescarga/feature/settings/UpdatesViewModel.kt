package com.libredescarga.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.libredescarga.core.domain.settings.AppUpdateRepository
import com.libredescarga.core.domain.settings.BlocklistUpdater
import com.libredescarga.core.domain.settings.EngineRepository
import com.libredescarga.core.domain.settings.UpdateReminder
import com.libredescarga.core.model.AppUpdate
import com.libredescarga.core.model.BlocklistRefresh
import com.libredescarga.core.model.EngineUpdateOutcome
import com.libredescarga.core.model.InstallOutcome
import com.libredescarga.core.model.UpdateCheck
import com.libredescarga.core.model.UpdateException
import com.libredescarga.core.model.UpdateFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface EngineStatus {
    data object Idle : EngineStatus
    data object Updating : EngineStatus
    data object Updated : EngineStatus
    data object Current : EngineStatus
    data class Failed(val reason: UpdateFailure) : EngineStatus
}

sealed interface AppUpdateStatus {
    data object Idle : AppUpdateStatus
    data object Checking : AppUpdateStatus
    data object UpToDate : AppUpdateStatus
    data class Available(val update: AppUpdate) : AppUpdateStatus

    /** La versión nueva necesita un Android más reciente. */
    data class Unsupported(val update: AppUpdate) : AppUpdateStatus
    data class Downloading(val update: AppUpdate, val progress: Float) : AppUpdateStatus

    /** Descargada y verificada (hash y firma). Falta que el usuario la instale. */
    data class Ready(val update: AppUpdate, val apk: File, val needsPermission: Boolean = false) : AppUpdateStatus
    data class Failed(val reason: UpdateFailure) : AppUpdateStatus
}

sealed interface BlocklistStatus {
    data object Idle : BlocklistStatus
    data object Updating : BlocklistStatus
    data class Updated(val domains: Int) : BlocklistStatus
    data object Current : BlocklistStatus
    data class Failed(val reason: UpdateFailure) : BlocklistStatus
}

data class UpdatesUiState(
    val installedVersionName: String = "",
    val installedVersionCode: Long = 0,
    val engineVersion: String? = null,
    val engine: EngineStatus = EngineStatus.Idle,
    val app: AppUpdateStatus = AppUpdateStatus.Idle,
    val blocklist: BlocklistStatus = BlocklistStatus.Idle,
)

@HiltViewModel
class UpdatesViewModel @Inject constructor(
    private val appUpdater: AppUpdateRepository,
    private val engineRepository: EngineRepository,
    private val blocklistUpdater: BlocklistUpdater,
    private val reminder: UpdateReminder,
) : ViewModel() {

    /** Si la app avisa sola cuando hay una versión nueva en GitHub. */
    val autoCheck: StateFlow<Boolean> = reminder.enabled

    fun onAutoCheckChanged(enabled: Boolean) = reminder.setEnabled(enabled)

    private val _uiState = MutableStateFlow(
        UpdatesUiState(
            installedVersionName = appUpdater.installedVersionName,
            installedVersionCode = appUpdater.installedVersionCode,
        ),
    )
    val uiState: StateFlow<UpdatesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val version = engineRepository.version()
            _uiState.update { it.copy(engineVersion = version) }
        }
    }

    // --- Motor de descarga (yt-dlp) -----------------------------------------------------------------------

    fun onUpdateEngine() {
        if (_uiState.value.engine == EngineStatus.Updating) return
        _uiState.update { it.copy(engine = EngineStatus.Updating) }
        viewModelScope.launch {
            val status = try {
                when (engineRepository.update()) {
                    EngineUpdateOutcome.UPDATED -> EngineStatus.Updated
                    EngineUpdateOutcome.ALREADY_UP_TO_DATE -> EngineStatus.Current
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                EngineStatus.Failed(UpdateFailure.NETWORK)
            }
            val version = engineRepository.version()
            _uiState.update { it.copy(engine = status, engineVersion = version ?: it.engineVersion) }
        }
    }

    // --- La app ---------------------------------------------------------------------------------------------------

    fun onCheckApp() {
        if (_uiState.value.app is AppUpdateStatus.Checking || _uiState.value.app is AppUpdateStatus.Downloading) return
        _uiState.update { it.copy(app = AppUpdateStatus.Checking) }
        viewModelScope.launch {
            val status = try {
                when (val check = appUpdater.check()) {
                    UpdateCheck.UpToDate -> AppUpdateStatus.UpToDate
                    is UpdateCheck.Available -> AppUpdateStatus.Available(check.update)
                    is UpdateCheck.Unsupported -> AppUpdateStatus.Unsupported(check.update)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: UpdateException) {
                AppUpdateStatus.Failed(error.failure)
            } catch (_: Exception) {
                AppUpdateStatus.Failed(UpdateFailure.UNKNOWN)
            }
            _uiState.update { it.copy(app = status) }
        }
    }

    /** Descarga, verifica (SHA-256 y firma) y deja el APK listo para instalar. */
    fun onDownloadUpdate() {
        val available = _uiState.value.app as? AppUpdateStatus.Available ?: return
        val update = available.update
        _uiState.update { it.copy(app = AppUpdateStatus.Downloading(update, 0f)) }
        viewModelScope.launch {
            val status = try {
                val apk = appUpdater.download(update) { progress ->
                    _uiState.update { state ->
                        if (state.app is AppUpdateStatus.Downloading) state.copy(app = AppUpdateStatus.Downloading(update, progress)) else state
                    }
                }
                AppUpdateStatus.Ready(update, apk)
            } catch (error: CancellationException) {
                throw error
            } catch (error: UpdateException) {
                AppUpdateStatus.Failed(error.failure)
            } catch (_: Exception) {
                AppUpdateStatus.Failed(UpdateFailure.UNKNOWN)
            }
            _uiState.update { it.copy(app = status) }
        }
    }

    /** Abre el instalador de Android. Si falta el permiso, lo explica y se puede volver a pulsar. */
    fun onInstallUpdate() {
        val ready = _uiState.value.app as? AppUpdateStatus.Ready ?: return
        val outcome = try {
            appUpdater.install(ready.apk)
        } catch (_: Exception) {
            _uiState.update { it.copy(app = AppUpdateStatus.Failed(UpdateFailure.UNKNOWN)) }
            return
        }
        _uiState.update { it.copy(app = ready.copy(needsPermission = outcome == InstallOutcome.NEEDS_PERMISSION)) }
    }

    // --- Lista de sitios bloqueados ------------------------------------------------------------------------------

    fun onUpdateBlocklist() {
        if (_uiState.value.blocklist == BlocklistStatus.Updating) return
        _uiState.update { it.copy(blocklist = BlocklistStatus.Updating) }
        viewModelScope.launch {
            val status = try {
                when (val result = blocklistUpdater.refresh()) {
                    is BlocklistRefresh.Updated -> BlocklistStatus.Updated(result.domains)
                    BlocklistRefresh.AlreadyCurrent -> BlocklistStatus.Current
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: UpdateException) {
                BlocklistStatus.Failed(error.failure)
            } catch (_: Exception) {
                BlocklistStatus.Failed(UpdateFailure.UNKNOWN)
            }
            _uiState.update { it.copy(blocklist = status) }
        }
    }
}
