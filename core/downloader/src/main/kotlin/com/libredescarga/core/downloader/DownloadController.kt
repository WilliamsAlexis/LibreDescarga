package com.libredescarga.core.downloader

import com.libredescarga.core.database.dao.DownloadDao
import com.libredescarga.core.database.mapper.toModel
import com.libredescarga.core.domain.usecase.DownloadQueuePolicy
import com.libredescarga.core.model.DownloadStatus
import javax.inject.Inject
import javax.inject.Singleton

/** Acciones sobre las descargas, usadas por la pantalla y por las notificaciones. */
@Singleton
class DownloadController @Inject constructor(
    private val dao: DownloadDao,
    private val scheduler: DownloadScheduler,
    private val queue: DownloadQueueManager,
    private val storage: DownloadStorage,
    private val notifier: DownloadNotifier,
) {

    /** Pausa conservando los archivos parciales: al reanudar, yt-dlp continúa donde quedó. */
    suspend fun pause(id: String) {
        val entity = dao.getById(id) ?: return
        if (!DownloadStatus.valueOf(entity.status).isActive) return
        dao.updateStatus(id, DownloadStatus.PAUSED.name, null)
        scheduler.cancel(id)
        queue.pump()
    }

    suspend fun resume(id: String) {
        val entity = dao.getById(id) ?: return
        if (entity.status != DownloadStatus.PAUSED.name) return
        dao.requeue(id, DownloadStatus.QUEUED.name)
        queue.pump()
    }

    suspend fun cancel(id: String) {
        dao.delete(id)
        scheduler.cancel(id)
        storage.deleteWorkDir(id)
        notifier.dismiss(id)
        queue.pump()
    }

    /** Reintenta desde cero una descarga fallida. */
    suspend fun retry(id: String) {
        val entity = dao.getById(id) ?: return
        if (entity.status != DownloadStatus.FAILED.name) return
        storage.deleteWorkDir(id)
        dao.updateProgress(id, progress = 0f, speed = null, eta = null, totalBytes = null, downloadedBytes = null)
        dao.requeue(id, DownloadStatus.QUEUED.name)
        notifier.dismiss(id)
        queue.pump()
    }

    suspend fun prioritize(id: String) {
        val tasks = dao.getAll().map { it.toModel() }
        dao.setPriority(id, DownloadQueuePolicy.topPriority(tasks))
        queue.pump()
    }

    suspend fun remove(id: String, deleteFile: Boolean) {
        val entity = dao.getById(id) ?: return
        if (DownloadStatus.valueOf(entity.status).isActive) {
            cancel(id)
            return
        }
        if (deleteFile) entity.outputUri?.let { storage.delete(it) }
        storage.deleteWorkDir(id)
        dao.delete(id)
        notifier.dismiss(id)
    }

    suspend fun pauseAll() {
        dao.getByStatuses(ACTIVE_STATUSES).forEach { entity ->
            dao.updateStatus(entity.id, DownloadStatus.PAUSED.name, null)
            scheduler.cancel(entity.id)
        }
    }

    suspend fun resumeAll() {
        dao.getByStatuses(listOf(DownloadStatus.PAUSED.name)).forEach { entity ->
            dao.requeue(entity.id, DownloadStatus.QUEUED.name)
        }
        queue.pump()
    }

    private companion object {
        val ACTIVE_STATUSES = DownloadStatus.entries.filter { it.isActive }.map { it.name }
    }
}
