package com.libredescarga.core.domain.usecase

import com.libredescarga.core.domain.repository.DownloadRepository
import com.libredescarga.core.model.DownloadStatus
import com.libredescarga.core.model.DownloadTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Descargas agrupadas en las pestañas "En curso", "Completadas" y "Fallidas". */
data class DownloadSections(
    val active: List<DownloadTask>,
    val completed: List<DownloadTask>,
    val failed: List<DownloadTask>,
) {
    companion object {
        val Empty = DownloadSections(emptyList(), emptyList(), emptyList())
    }
}

class ObserveDownloadSectionsUseCase @Inject constructor(
    private val repository: DownloadRepository,
) {
    operator fun invoke(): Flow<DownloadSections> = repository.tasks.map(::split)

    companion object {
        private val ACTIVE_ORDER = listOf(
            DownloadStatus.RUNNING,
            DownloadStatus.WAITING,
            DownloadStatus.QUEUED,
            DownloadStatus.PAUSED,
        )

        fun split(tasks: List<DownloadTask>): DownloadSections = DownloadSections(
            // En curso: primero las que descargan, luego la cola en el orden en que entrarán.
            active = tasks
                .filter { it.status in ACTIVE_ORDER }
                .sortedWith(
                    compareBy<DownloadTask> { ACTIVE_ORDER.indexOf(it.status) }
                        .thenByDescending { it.priority }
                        .thenBy { it.createdAt },
                ),
            completed = tasks
                .filter { it.status == DownloadStatus.COMPLETED }
                .sortedByDescending { it.completedAt ?: it.createdAt },
            failed = tasks
                .filter { it.status == DownloadStatus.FAILED }
                .sortedByDescending { it.createdAt },
        )
    }
}
