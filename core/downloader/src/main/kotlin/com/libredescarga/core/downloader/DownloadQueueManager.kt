package com.libredescarga.core.downloader

import com.libredescarga.core.database.dao.DownloadDao
import com.libredescarga.core.database.mapper.toModel
import com.libredescarga.core.domain.repository.UserPreferencesRepository
import com.libredescarga.core.domain.usecase.DownloadQueuePolicy
import com.libredescarga.core.model.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cola con prioridad: decide qué descargas se entregan al sistema según el
 * límite de descargas simultáneas (1 a 5) y la prioridad de cada una.
 */
@Singleton
class DownloadQueueManager @Inject constructor(
    private val dao: DownloadDao,
    private val scheduler: DownloadScheduler,
    private val preferences: UserPreferencesRepository,
) {
    private val mutex = Mutex()

    /** Asigna huecos libres a las descargas en cola. Se llama tras cualquier cambio. */
    suspend fun pump() = mutex.withLock {
        val settings = preferences.settings.first()
        val tasks = dao.getAll().map { it.toModel() }
        DownloadQueuePolicy.nextToDispatch(tasks, settings.maxConcurrentDownloads).forEach { id ->
            dao.updateStatus(id, DownloadStatus.WAITING.name, null)
            scheduler.schedule(id, settings.wifiOnly)
        }
    }

    /**
     * Al abrir la app: devuelve a la cola lo que el sistema ya no tiene programado,
     * reparte los huecos libres y reacciona a los cambios de ajustes.
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            reconcile()
            pump()

            // Cambiar el límite libera o reserva huecos.
            launch {
                preferences.settings.map { it.maxConcurrentDownloads }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { pump() }
            }
            // "Solo por Wi-Fi": se actualizan las condiciones de las que aún esperan red.
            preferences.settings.map { it.wifiOnly }
                .distinctUntilChanged()
                .drop(1)
                .collect { wifiOnly -> applyNetworkPolicy(wifiOnly) }
        }
    }

    private suspend fun reconcile() = mutex.withLock {
        dao.getByStatuses(listOf(DownloadStatus.WAITING.name, DownloadStatus.RUNNING.name)).forEach { entity ->
            if (!scheduler.isScheduled(entity.id)) {
                dao.updateStatus(entity.id, DownloadStatus.QUEUED.name, null)
            }
        }
    }

    private suspend fun applyNetworkPolicy(wifiOnly: Boolean) = mutex.withLock {
        dao.getByStatuses(listOf(DownloadStatus.WAITING.name)).forEach { entity ->
            scheduler.schedule(entity.id, wifiOnly, replace = true)
        }
    }
}
