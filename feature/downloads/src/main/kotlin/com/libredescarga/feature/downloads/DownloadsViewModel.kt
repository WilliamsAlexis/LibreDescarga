package com.libredescarga.feature.downloads

import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.Channel
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.model.LibraryMedia
import com.libredescarga.core.model.DownloadTask
import com.libredescarga.core.domain.player.PlaybackController
import com.libredescarga.core.model.VaultItem
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.libredescarga.core.domain.repository.DownloadRepository
import com.libredescarga.core.domain.repository.StorageRepository
import com.libredescarga.core.domain.repository.UserPreferencesRepository
import com.libredescarga.core.domain.usecase.DownloadSections
import com.libredescarga.core.domain.usecase.ObserveDownloadSectionsUseCase
import com.libredescarga.core.model.DownloadStatus
import com.libredescarga.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DownloadsTab { ACTIVE, COMPLETED, FAILED }

/** Acciones de una sola vez que la pantalla de descargas debe hacer (abrir un reproductor, avisar...). */
sealed interface DownloadsEvent {
    /** Abrir un video descargado en el reproductor de la app. */
    data class OpenVideo(val uri: String, val title: String, val durationMs: Long) : DownloadsEvent

    /** Abrir las imágenes descargadas en el visor de la app, empezando por la de posición [index]. */
    data class OpenImages(val uris: List<String>, val titles: List<String>, val index: Int) : DownloadsEvent

    /** El archivo está en la carpeta privada: solo se abre desde ahí. */
    data object InVault : DownloadsEvent
}

/** Ajustes del gestor que se pueden cambiar desde la propia pantalla de descargas. */
data class DownloadSettingsUi(
    val maxConcurrent: Int = UserSettings.DEFAULT_CONCURRENT_DOWNLOADS,
    val concurrentFragments: Int = UserSettings.DEFAULT_FRAGMENTS,
    val wifiOnly: Boolean = false,
    val speedLimitKbps: Int = 0,
    val treeUri: String? = null,
)

data class DownloadsUiState(
    val sections: DownloadSections = DownloadSections.Empty,
    val selectedTab: DownloadsTab = DownloadsTab.ACTIVE,
    val settings: DownloadSettingsUi = DownloadSettingsUi(),
    /** Nombre de la carpeta elegida; null si se usan las carpetas del sistema o ya no es accesible. */
    val folderName: String? = null,
    val availableBytes: Long? = null,
) {
    val folderUnavailable: Boolean get() = settings.treeUri != null && folderName == null
    val lowStorage: Boolean get() = availableBytes != null && availableBytes < LOW_STORAGE_BYTES
    val hasPaused: Boolean get() = sections.active.any { it.status == DownloadStatus.PAUSED }
    val hasRunning: Boolean get() = sections.active.any { it.status != DownloadStatus.PAUSED }

    /** La primera de la cola no necesita "Descargar primero". */
    val firstQueuedId: String? get() = sections.active.firstOrNull { it.status == DownloadStatus.QUEUED }?.id

    companion object {
        const val LOW_STORAGE_BYTES = 500L * 1024 * 1024
    }
}

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    observeSections: ObserveDownloadSectionsUseCase,
    private val downloads: DownloadRepository,
    private val preferences: UserPreferencesRepository,
    private val storage: StorageRepository,
    private val player: PlaybackController,
) : ViewModel() {

    private val _events = Channel<DownloadsEvent>(Channel.BUFFERED)
    val events: Flow<DownloadsEvent> = _events.receiveAsFlow()

    private val selectedTab = MutableStateFlow(DownloadsTab.ACTIVE)
    private val availableBytes = MutableStateFlow<Long?>(null)

    private val settings = preferences.settings
        .map {
            DownloadSettingsUi(
                maxConcurrent = it.maxConcurrentDownloads,
                concurrentFragments = it.concurrentFragments,
                wifiOnly = it.wifiOnly,
                speedLimitKbps = it.speedLimitKbps,
                treeUri = it.downloadTreeUri,
            )
        }
        .distinctUntilChanged()

    private val folderName = settings
        .map { it.treeUri }
        .distinctUntilChanged()
        .mapLatest { uri -> uri?.let { storage.folderName(it) } }

    val uiState: StateFlow<DownloadsUiState> = combine(
        observeSections(),
        selectedTab,
        settings,
        folderName,
        availableBytes,
    ) { sections, tab, settings, folder, free ->
        DownloadsUiState(
            sections = sections,
            selectedTab = tab,
            settings = settings,
            folderName = folder,
            availableBytes = free,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState())

    fun onTabSelected(tab: DownloadsTab) = selectedTab.update { tab }

    /** Se llama al mostrar la pantalla, para el aviso de poco espacio. */
    fun refreshStorage() {
        viewModelScope.launch { availableBytes.value = runCatching { storage.availableBytes() }.getOrNull() }
    }

    // --- Acciones por descarga -------------------------------------------------------------

    fun onPause(id: String) = launch { downloads.pause(id) }

    fun onResume(id: String) = launch { downloads.resume(id) }

    fun onCancel(id: String) = launch { downloads.cancel(id) }

    fun onRetry(id: String) = launch { downloads.retry(id) }

    fun onPrioritize(id: String) = launch { downloads.prioritize(id) }

    fun onRemove(id: String, deleteFile: Boolean) = launch {
        downloads.remove(id, deleteFile)
        if (deleteFile) refreshStorage()
    }

    /**
     * Quita de la lista todas las descargas completadas o todas las fallidas.
     * Si [deleteFiles] es verdadero, también borra del dispositivo los archivos descargados
     * (los que están en la carpeta privada no se tocan).
     */
    fun onClearAll(tab: DownloadsTab, deleteFiles: Boolean) = launch {
        val sections = uiState.value.sections
        val tasks = when (tab) {
            DownloadsTab.COMPLETED -> sections.completed
            DownloadsTab.FAILED -> sections.failed
            DownloadsTab.ACTIVE -> return@launch // Las descargas en curso no se limpian de golpe.
        }
        tasks.forEach { task ->
            val hasFile = task.status == DownloadStatus.COMPLETED && !VaultItem.isVaultUri(task.outputUri)
            downloads.remove(task.id, deleteFiles && hasFile)
        }
        if (deleteFiles) refreshStorage()
    }

    /**
     * Abre una descarga completada con el reproductor o visor de la propia app:
     * el video en el reproductor de video, el audio en el de música (con los demás audios descargados en la cola)
     * y las imágenes en el visor, pudiendo deslizar entre las descargadas.
     */
    fun onOpen(task: DownloadTask) {
        val uri = task.outputUri
        if (task.status != DownloadStatus.COMPLETED || uri == null) return
        if (VaultItem.isVaultUri(uri)) {
            send(DownloadsEvent.InVault)
            return
        }
        val openable = uiState.value.sections.completed.filter {
            it.request.kind == task.request.kind && it.outputUri != null && !VaultItem.isVaultUri(it.outputUri)
        }
        val index = openable.indexOfFirst { it.id == task.id }.coerceAtLeast(0)
        when (task.request.kind) {
            MediaKind.VIDEO -> send(
                DownloadsEvent.OpenVideo(uri, task.request.title, (task.request.durationSeconds ?: 0L) * MILLIS_PER_SECOND),
            )
            MediaKind.AUDIO -> player.playQueue(openable.map { it.toLibraryMedia() }, index)
            MediaKind.IMAGE -> send(
                DownloadsEvent.OpenImages(
                    uris = openable.mapNotNull { it.outputUri },
                    titles = openable.map { it.request.title },
                    index = index,
                ),
            )
        }
    }

    private fun send(event: DownloadsEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    private fun DownloadTask.toLibraryMedia() = LibraryMedia(
        uri = outputUri.orEmpty(),
        kind = request.kind,
        title = request.title,
        displayName = request.fileName,
        folder = "",
        sizeBytes = totalBytes ?: 0L,
        durationMs = (request.durationSeconds ?: 0L) * MILLIS_PER_SECOND,
        dateAdded = completedAt ?: createdAt,
        mimeType = null,
        artist = request.uploader,
    )

    fun onPauseAll() = launch { downloads.pauseAll() }

    fun onResumeAll() = launch { downloads.resumeAll() }

    // --- Ajustes del gestor ----------------------------------------------------------------

    fun onMaxConcurrentChange(count: Int) = launch { preferences.setMaxConcurrentDownloads(count) }

    fun onFragmentsChange(count: Int) = launch { preferences.setConcurrentFragments(count) }

    fun onWifiOnlyChange(enabled: Boolean) = launch { preferences.setWifiOnly(enabled) }

    fun onSpeedLimitChange(kbps: Int) = launch { preferences.setSpeedLimitKbps(kbps) }

    /** null = volver a Movies/ y Music/. */
    fun onFolderChange(treeUri: String?) = launch { preferences.setDownloadTreeUri(treeUri) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}
