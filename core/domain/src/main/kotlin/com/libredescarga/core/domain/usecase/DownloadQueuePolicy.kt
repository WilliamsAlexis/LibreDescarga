package com.libredescarga.core.domain.usecase

import com.libredescarga.core.model.DownloadStatus
import com.libredescarga.core.model.DownloadTask
import com.libredescarga.core.model.UserSettings

/**
 * Reglas de la cola de descargas:
 * - nunca hay más de N descargas asignadas a la vez (en espera de red o descargando);
 * - entran primero las de mayor prioridad y, a igual prioridad, las más antiguas.
 */
object DownloadQueuePolicy {

    fun nextToDispatch(tasks: List<DownloadTask>, maxConcurrent: Int): List<String> {
        val limit = maxConcurrent.coerceIn(
            UserSettings.MIN_CONCURRENT_DOWNLOADS,
            UserSettings.MAX_CONCURRENT_DOWNLOADS,
        )
        val busy = tasks.count { it.status == DownloadStatus.WAITING || it.status == DownloadStatus.RUNNING }
        val free = limit - busy
        if (free <= 0) return emptyList()
        return tasks
            .filter { it.status == DownloadStatus.QUEUED }
            .sortedWith(compareByDescending<DownloadTask> { it.priority }.thenBy { it.createdAt })
            .take(free)
            .map { it.id }
    }

    /** Prioridad para pasar una descarga delante de todas las demás. */
    fun topPriority(tasks: List<DownloadTask>): Int = (tasks.maxOfOrNull { it.priority } ?: 0) + 1
}
