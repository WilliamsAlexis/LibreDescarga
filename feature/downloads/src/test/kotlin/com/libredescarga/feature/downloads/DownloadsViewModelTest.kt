package com.libredescarga.feature.downloads

import app.cash.turbine.test
import com.libredescarga.core.domain.usecase.DownloadRequestFactory
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.testing.FakePlaybackController
import com.libredescarga.core.domain.usecase.ObserveDownloadSectionsUseCase
import com.libredescarga.core.model.DownloadStatus
import com.libredescarga.core.model.DownloadTask
import com.libredescarga.core.model.VideoQuality
import com.libredescarga.core.testing.FakeDownloadRepository
import com.libredescarga.core.testing.FakeStorageRepository
import com.libredescarga.core.testing.FakeUserPreferencesRepository
import com.libredescarga.core.testing.MainDispatcherRule
import com.libredescarga.core.testing.TestMedia
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DownloadsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val downloads = FakeDownloadRepository()
    private val preferences = FakeUserPreferencesRepository()
    private val storage = FakeStorageRepository(folderNames = mapOf("content://tree/sd" to "Tarjeta SD"))
    private val player = FakePlaybackController()
    private lateinit var viewModel: DownloadsViewModel

    @Before
    fun setUp() {
        downloads.setTasks(
            listOf(
                task("running", DownloadStatus.RUNNING),
                task("queued-1", DownloadStatus.QUEUED, createdAt = 1),
                task("queued-2", DownloadStatus.QUEUED, createdAt = 2),
                task("done", DownloadStatus.COMPLETED),
                task("failed", DownloadStatus.FAILED),
            ),
        )
        viewModel = DownloadsViewModel(
            observeSections = ObserveDownloadSectionsUseCase(downloads),
            downloads = downloads,
            preferences = preferences,
            storage = storage,
            player = player,
        )
    }

    private fun task(id: String, status: DownloadStatus, createdAt: Long = 0) = DownloadTask(
        id = id,
        request = DownloadRequestFactory.video(TestMedia.entry, VideoQuality.P720),
        status = status,
        progressPercent = 40f,
        speedBytesPerSecond = null,
        etaSeconds = null,
        totalBytes = null,
        downloadedBytes = null,
        outputUri = if (status == DownloadStatus.COMPLETED) "content://media/1" else null,
        errorMessage = null,
        priority = 0,
        createdAt = createdAt,
        completedAt = null,
    )

    private fun TestScope.collectState() =
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect {} }

    private fun status(id: String) = downloads.current.first { it.id == id }.status

    @Test
    fun `agrupa las descargas en pestañas`() = runTest {
        collectState()
        val sections = viewModel.uiState.value.sections
        assertEquals(listOf("running", "queued-1", "queued-2"), sections.active.map { it.id })
        assertEquals(listOf("done"), sections.completed.map { it.id })
        assertEquals(listOf("failed"), sections.failed.map { it.id })
        assertEquals("queued-1", viewModel.uiState.value.firstQueuedId)
    }

    @Test
    fun `pausar, reanudar y cancelar`() = runTest {
        collectState()
        viewModel.onPause("running")
        assertEquals(DownloadStatus.PAUSED, status("running"))
        assertTrue(viewModel.uiState.value.hasPaused)

        viewModel.onResume("running")
        assertEquals(DownloadStatus.QUEUED, status("running"))

        viewModel.onCancel("queued-2")
        assertTrue(downloads.current.none { it.id == "queued-2" })
    }

    @Test
    fun `reintentar devuelve la descarga fallida a la cola`() = runTest {
        collectState()
        viewModel.onRetry("failed")
        assertEquals(DownloadStatus.QUEUED, status("failed"))
        assertTrue(viewModel.uiState.value.sections.failed.isEmpty())
    }

    @Test
    fun `descargar primero sube la prioridad`() = runTest {
        collectState()
        viewModel.onPrioritize("queued-2")
        assertEquals("queued-2", viewModel.uiState.value.firstQueuedId)
    }

    @Test
    fun `quitar una completada puede borrar también el archivo`() = runTest {
        collectState()
        viewModel.onRemove("done", deleteFile = true)
        assertEquals(listOf("done"), downloads.deletedFiles)
        assertTrue(viewModel.uiState.value.sections.completed.isEmpty())
    }

    private fun completed(id: String, kind: MediaKind, uri: String) = task(id, DownloadStatus.COMPLETED).copy(
        outputUri = uri,
        request = task(id, DownloadStatus.COMPLETED).request.copy(kind = kind, title = "Título $id"),
    )

    @Test
    fun `abrir un video lo manda al reproductor de video de la app`() = runTest {
        withCompleted(completed("v1", MediaKind.VIDEO, "content://media/video/1"))
        collectState()

        viewModel.events.test {
            viewModel.onOpen(downloads.current.first { it.id == "v1" })
            val event = awaitItem() as DownloadsEvent.OpenVideo
            assertEquals("content://media/video/1", event.uri)
            assertEquals("Título v1", event.title)
        }
    }

    @Test
    fun `abrir un audio lo reproduce en la cola con los demas audios descargados`() = runTest {
        withCompleted(
            completed("a1", MediaKind.AUDIO, "content://media/audio/1"),
            completed("a2", MediaKind.AUDIO, "content://media/audio/2"),
            completed("v1", MediaKind.VIDEO, "content://media/video/1"),
        )
        collectState()

        viewModel.onOpen(downloads.current.first { it.id == "a2" })

        val queue = player.state.value
        assertEquals(listOf("content://media/audio/1", "content://media/audio/2"), queue.queue.map { it.uri })
        assertEquals(1, queue.currentIndex)
    }

    @Test
    fun `abrir una imagen abre el visor con todas las imagenes descargadas`() = runTest {
        withCompleted(
            completed("i1", MediaKind.IMAGE, "content://media/image/1"),
            completed("i2", MediaKind.IMAGE, "content://media/image/2"),
        )
        collectState()

        viewModel.events.test {
            viewModel.onOpen(downloads.current.first { it.id == "i2" })
            val event = awaitItem() as DownloadsEvent.OpenImages
            assertEquals(listOf("content://media/image/1", "content://media/image/2"), event.uris)
            assertEquals(listOf("Título i1", "Título i2"), event.titles)
            assertEquals(1, event.index)
        }
    }

    @Test
    fun `un archivo de la carpeta privada avisa en vez de abrirse`() = runTest {
        withCompleted(completed("p1", MediaKind.VIDEO, "vault://abc"))
        collectState()

        viewModel.events.test {
            viewModel.onOpen(downloads.current.first { it.id == "p1" })
            assertEquals(DownloadsEvent.InVault, awaitItem())
        }
    }

    @Test
    fun `abrir una descarga que no esta completa no hace nada`() = runTest {
        collectState()

        viewModel.events.test {
            viewModel.onOpen(downloads.current.first { it.id == "running" })
            expectNoEvents()
        }
        assertTrue(player.state.value.queue.isEmpty())
    }

    private fun withCompleted(vararg extra: DownloadTask) {
        downloads.setTasks(downloads.current + extra)
    }

    @Test
    fun `limpiar completadas las quita todas sin tocar las demas`() = runTest {
        withCompleted(task("done-2", DownloadStatus.COMPLETED), task("failed-2", DownloadStatus.FAILED))
        collectState()

        viewModel.onClearAll(DownloadsTab.COMPLETED, deleteFiles = false)

        val sections = viewModel.uiState.value.sections
        assertTrue(sections.completed.isEmpty())
        assertEquals(listOf("failed", "failed-2"), sections.failed.map { it.id })
        assertEquals(3, sections.active.size)
        assertTrue(downloads.deletedFiles.isEmpty())
    }

    @Test
    fun `limpiar completadas con la casilla tambien borra los archivos`() = runTest {
        withCompleted(task("done-2", DownloadStatus.COMPLETED))
        collectState()

        viewModel.onClearAll(DownloadsTab.COMPLETED, deleteFiles = true)

        assertEquals(listOf("done", "done-2"), downloads.deletedFiles)
        assertTrue(viewModel.uiState.value.sections.completed.isEmpty())
    }

    @Test
    fun `limpiar completadas no borra los archivos de la carpeta privada`() = runTest {
        downloads.setTasks(
            downloads.current + task("done-vault", DownloadStatus.COMPLETED).copy(outputUri = "vault://abc"),
        )
        collectState()

        viewModel.onClearAll(DownloadsTab.COMPLETED, deleteFiles = true)

        assertEquals(listOf("done"), downloads.deletedFiles)
        assertTrue(viewModel.uiState.value.sections.completed.isEmpty())
    }

    @Test
    fun `limpiar fallidas quita solo las fallidas`() = runTest {
        withCompleted(task("failed-2", DownloadStatus.FAILED))
        collectState()

        viewModel.onClearAll(DownloadsTab.FAILED, deleteFiles = true)

        val sections = viewModel.uiState.value.sections
        assertTrue(sections.failed.isEmpty())
        assertEquals(listOf("done"), sections.completed.map { it.id })
        assertTrue(downloads.deletedFiles.isEmpty()) // Una descarga fallida no tiene archivo que borrar.
    }

    @Test
    fun `las descargas en curso no se limpian de golpe`() = runTest {
        collectState()

        viewModel.onClearAll(DownloadsTab.ACTIVE, deleteFiles = true)

        assertEquals(3, viewModel.uiState.value.sections.active.size)
    }

    @Test
    fun `pausar todas y reanudar todas`() = runTest {
        collectState()
        viewModel.onPauseAll()
        assertTrue(viewModel.uiState.value.sections.active.all { it.status == DownloadStatus.PAUSED })
        assertFalse(viewModel.uiState.value.hasRunning)

        viewModel.onResumeAll()
        assertTrue(viewModel.uiState.value.sections.active.all { it.status == DownloadStatus.QUEUED })
    }

    @Test
    fun `los ajustes del gestor se guardan`() = runTest {
        collectState()
        viewModel.onMaxConcurrentChange(4)
        viewModel.onFragmentsChange(8)
        viewModel.onWifiOnlyChange(true)
        viewModel.onSpeedLimitChange(2_000)

        val settings = viewModel.uiState.value.settings
        assertEquals(4, settings.maxConcurrent)
        assertEquals(8, settings.concurrentFragments)
        assertTrue(settings.wifiOnly)
        assertEquals(2_000, settings.speedLimitKbps)
    }

    @Test
    fun `carpeta elegida y carpeta inaccesible`() = runTest {
        collectState()
        viewModel.onFolderChange("content://tree/sd")
        assertEquals("Tarjeta SD", viewModel.uiState.value.folderName)
        assertFalse(viewModel.uiState.value.folderUnavailable)

        viewModel.onFolderChange("content://tree/perdida")
        assertNull(viewModel.uiState.value.folderName)
        assertTrue(viewModel.uiState.value.folderUnavailable)

        viewModel.onFolderChange(null)
        assertFalse(viewModel.uiState.value.folderUnavailable)
    }

    @Test
    fun `avisa cuando queda poco espacio`() = runTest {
        collectState()
        storage.available = 100L * 1024 * 1024
        viewModel.refreshStorage()
        assertTrue(viewModel.uiState.value.lowStorage)

        storage.available = 5L * 1024 * 1024 * 1024
        viewModel.refreshStorage()
        assertFalse(viewModel.uiState.value.lowStorage)
    }
}
