package com.libredescarga.core.domain.usecase

import com.libredescarga.core.model.DownloadErrorCodes
import com.libredescarga.core.model.DownloadStatus
import com.libredescarga.core.model.DownloadTask
import com.libredescarga.core.model.MediaError
import com.libredescarga.core.model.VideoQuality
import com.libredescarga.core.testing.TestMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadQueueTest {

    private fun task(id: String, status: DownloadStatus, priority: Int = 0, createdAt: Long = 0, completedAt: Long? = null) =
        DownloadTask(
            id = id,
            request = DownloadRequestFactory.video(TestMedia.entry, VideoQuality.P720),
            status = status,
            progressPercent = 0f,
            speedBytesPerSecond = null,
            etaSeconds = null,
            totalBytes = null,
            downloadedBytes = null,
            outputUri = null,
            errorMessage = null,
            priority = priority,
            createdAt = createdAt,
            completedAt = completedAt,
        )

    @Test
    fun `respeta el límite de descargas simultáneas`() {
        val tasks = listOf(
            task("running", DownloadStatus.RUNNING),
            task("q1", DownloadStatus.QUEUED, createdAt = 1),
            task("q2", DownloadStatus.QUEUED, createdAt = 2),
            task("q3", DownloadStatus.QUEUED, createdAt = 3),
        )
        assertEquals(listOf("q1", "q2"), DownloadQueuePolicy.nextToDispatch(tasks, maxConcurrent = 3))
        assertTrue(DownloadQueuePolicy.nextToDispatch(tasks, maxConcurrent = 1).isEmpty())
    }

    @Test
    fun `las que esperan red ocupan hueco`() {
        val tasks = listOf(
            task("waiting", DownloadStatus.WAITING),
            task("paused", DownloadStatus.PAUSED),
            task("q1", DownloadStatus.QUEUED),
        )
        assertTrue(DownloadQueuePolicy.nextToDispatch(tasks, maxConcurrent = 1).isEmpty())
        assertEquals(listOf("q1"), DownloadQueuePolicy.nextToDispatch(tasks, maxConcurrent = 2))
    }

    @Test
    fun `la prioridad adelanta en la cola`() {
        val tasks = listOf(
            task("antigua", DownloadStatus.QUEUED, createdAt = 1),
            task("urgente", DownloadStatus.QUEUED, priority = 5, createdAt = 9),
        )
        assertEquals(listOf("urgente"), DownloadQueuePolicy.nextToDispatch(tasks, maxConcurrent = 1))
        assertEquals(6, DownloadQueuePolicy.topPriority(tasks))
    }

    @Test
    fun `el límite se acota entre 1 y 5`() {
        val queued = (1..10).map { task("q$it", DownloadStatus.QUEUED, createdAt = it.toLong()) }
        assertEquals(5, DownloadQueuePolicy.nextToDispatch(queued, maxConcurrent = 99).size)
        assertEquals(1, DownloadQueuePolicy.nextToDispatch(queued, maxConcurrent = 0).size)
    }

    @Test
    fun `separa las descargas en pestañas`() {
        val sections = ObserveDownloadSectionsUseCase.split(
            listOf(
                task("paused", DownloadStatus.PAUSED),
                task("queued", DownloadStatus.QUEUED),
                task("running", DownloadStatus.RUNNING),
                task("done-old", DownloadStatus.COMPLETED, completedAt = 1),
                task("done-new", DownloadStatus.COMPLETED, completedAt = 2),
                task("failed", DownloadStatus.FAILED),
                task("cancelled", DownloadStatus.CANCELLED),
            ),
        )
        assertEquals(listOf("running", "queued", "paused"), sections.active.map { it.id })
        assertEquals(listOf("done-new", "done-old"), sections.completed.map { it.id })
        assertEquals(listOf("failed"), sections.failed.map { it.id })
    }

    @Test
    fun `los códigos de error se guardan y se recuperan`() {
        listOf(
            MediaError.DrmProtected(),
            MediaError.Network(),
            MediaError.InsufficientStorage(),
            MediaError.NotPublic(),
        ).forEach { error ->
            val restored = DownloadErrorCodes.toError(DownloadErrorCodes.fromError(error))
            assertEquals(error::class, restored::class)
        }
        val blocked = DownloadErrorCodes.toError(DownloadErrorCodes.fromError(MediaError.BlockedSite("x.com")))
        assertEquals("x.com", (blocked as MediaError.BlockedSite).domain)
        val unknown = DownloadErrorCodes.toError(DownloadErrorCodes.fromError(MediaError.Unknown("fallo")))
        assertEquals("fallo", (unknown as MediaError.Unknown).details)
    }
}
