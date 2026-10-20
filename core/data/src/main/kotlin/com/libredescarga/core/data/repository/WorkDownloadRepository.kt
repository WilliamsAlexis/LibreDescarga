package com.libredescarga.core.data.repository

import com.libredescarga.core.database.dao.DownloadDao
import com.libredescarga.core.database.mapper.toEntity
import com.libredescarga.core.database.mapper.toModel
import com.libredescarga.core.domain.repository.DownloadRepository
import com.libredescarga.core.downloader.DownloadController
import com.libredescarga.core.downloader.DownloadQueueManager
import com.libredescarga.core.model.DownloadRequest
import com.libredescarga.core.model.DownloadTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

internal class WorkDownloadRepository @Inject constructor(
    private val dao: DownloadDao,
    private val queue: DownloadQueueManager,
    private val controller: DownloadController,
) : DownloadRepository {

    override val tasks: Flow<List<DownloadTask>> =
        dao.observeAll().map { list -> list.map { it.toModel() } }

    override suspend fun enqueue(requests: List<DownloadRequest>): List<String> {
        val now = System.currentTimeMillis()
        // Mismo orden en la cola que en la selección del usuario.
        val entities = requests.mapIndexed { index, request ->
            request.toEntity(id = UUID.randomUUID().toString(), createdAt = now + index)
        }
        dao.insertAll(entities)
        queue.pump()
        return entities.map { it.id }
    }

    override suspend fun pause(id: String) = controller.pause(id)

    override suspend fun resume(id: String) = controller.resume(id)

    override suspend fun cancel(id: String) = controller.cancel(id)

    override suspend fun retry(id: String) = controller.retry(id)

    override suspend fun prioritize(id: String) = controller.prioritize(id)

    override suspend fun remove(id: String, deleteFile: Boolean) = controller.remove(id, deleteFile)

    override suspend fun pauseAll() = controller.pauseAll()

    override suspend fun resumeAll() = controller.resumeAll()
}
