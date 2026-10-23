package com.libredescarga.core.downloader

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.libredescarga.core.database.dao.DownloadDao
import com.libredescarga.core.database.mapper.toRequest
import com.libredescarga.core.domain.repository.AnalysisInfoSource
import com.libredescarga.core.domain.repository.UserPreferencesRepository
import com.libredescarga.core.domain.vault.VaultAuthRepository
import com.libredescarga.core.domain.vault.VaultRepository
import com.libredescarga.core.engine.DownloadCommandBuilder
import com.libredescarga.core.engine.DownloadTuning
import com.libredescarga.core.engine.EngineProgress
import com.libredescarga.core.engine.YtDlpEngine
import com.libredescarga.core.model.AnalisisPerf
import com.libredescarga.core.model.DownloadErrorCodes
import com.libredescarga.core.model.DownloadRequest
import com.libredescarga.core.model.DownloadStatus
import com.libredescarga.core.model.MediaError
import com.libredescarga.core.model.MediaKind
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Ejecuta una descarga en segundo plano (servicio en primer plano de WorkManager):
 * yt-dlp descarga a una carpeta privada y al terminar el archivo se publica
 * en Movies/, Music/ o en la carpeta elegida por el usuario.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: DownloadDao,
    private val engine: YtDlpEngine,
    private val imageFetcher: ImageFetcher,
    private val storage: DownloadStorage,
    private val notifier: DownloadNotifier,
    private val queue: DownloadQueueManager,
    private val preferences: UserPreferencesRepository,
    private val vault: VaultRepository,
    private val vaultAuth: VaultAuthRepository,
    private val analysisInfo: AnalysisInfoSource,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return Result.failure()
        val entity = dao.getById(downloadId) ?: return Result.failure()
        // Pausada, cancelada o ya terminada mientras esperaba: no hay nada que hacer.
        if (entity.status !in RUNNABLE_STATUSES) return Result.success()

        val request = entity.toRequest()
        val settings = preferences.settings.first()
        // Si la carpeta privada está configurada y se pidió, la descarga va directo ahí (sin pasar por la galería).
        val toVault = vaultAuth.settings.first().let { it.isSetUp && it.downloadsToVault }
        notifier.ensureChannels()

        if (!hasEnoughSpace(request)) {
            return fail(downloadId, request.title, MediaError.InsufficientStorage())
        }

        dao.updateStatus(downloadId, DownloadStatus.RUNNING.name, null)
        showForeground(notifier.foregroundInfo(downloadId, request.title, percent = null))

        val workDir = storage.workDirFor(downloadId)
        val tuning = DownloadTuning(
            concurrentFragments = settings.concurrentFragments,
            rateLimitKbps = settings.speedLimitKbps.takeIf { it > 0 },
        )
        return try {
            if (request.kind == MediaKind.IMAGE) {
                // Las imágenes se bajan directamente, sin yt-dlp.
                fetchImage(downloadId, request, workDir)
            } else {
                // "Convertir a MP3" de la biblioteca: el origen es un archivo del teléfono.
                val source = if (request.url.startsWith("content://")) {
                    request.copy(url = storage.copyLocalSource(request.url, workDir).toURI().toString())
                } else {
                    request
                }
                runDownload(downloadId, source, DownloadCommandBuilder.build(source, workDir.absolutePath, tuning), workDir)
                storage.deleteLocalSource(workDir)
            }
            // Las imágenes siempre van a la galería: la bóveda solo guarda videos y audios.
            val published = publishResults(
                workDir,
                request.kind,
                settings.downloadTreeUri,
                toVault && request.kind != MediaKind.IMAGE,
            )
            dao.markCompleted(
                id = downloadId,
                status = DownloadStatus.COMPLETED.name,
                outputUri = published.mediaUri.toString(),
                completedAt = System.currentTimeMillis(),
            )
            storage.deleteWorkDir(downloadId)
            preferences.incrementCompletedDownloads()
            if (published.isPrivate) {
                notifier.showCompletedPrivate(downloadId, request.title)
            } else {
                notifier.showCompleted(downloadId, request.title, published.mediaUri, published.mimeType)
            }
            queue.pump()
            Result.success()
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { onStopped(downloadId) }
            throw cancelled
        } catch (error: MediaError.Network) {
            if (runAttemptCount < MAX_NETWORK_RETRIES) {
                // Sigue programada: WorkManager la reintentará cuando vuelva la red.
                dao.updateStatus(downloadId, DownloadStatus.WAITING.name, null)
                Result.retry()
            } else {
                fail(downloadId, request.title, error)
            }
        } catch (error: Throwable) {
            // También errores nativos (librerías de Python/FFmpeg que no cargan, poca memoria...):
            // en algunos teléfonos escaparían del worker y cerrarían toda la app.
            fail(downloadId, request.title, error)
        }
    }

    /**
     * Muestra la notificación del servicio en primer plano. Algunos fabricantes (Motorola, Xiaomi...)
     * o Android 12+ pueden negarla; la descarga sigue igual, solo sin la notificación fija.
     */
    private suspend fun showForeground(info: ForegroundInfo) {
        try {
            setForeground(info)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // IllegalStateException / SecurityException: no es un error de la descarga.
        }
    }

    /** Descarga una imagen y va guardando el progreso (en bytes) para mostrarlo en la lista de descargas. */
    @OptIn(FlowPreview::class)
    private suspend fun fetchImage(downloadId: String, request: DownloadRequest, workDir: File) {
        val progress = MutableStateFlow<Pair<Long, Long?>?>(null)
        coroutineScope {
            val reporter = launch {
                progress.filterNotNull().sample(PROGRESS_INTERVAL_MS).collect { (downloaded, total) ->
                    val percent = total?.let { (downloaded * 100f / it).coerceIn(0f, 100f) } ?: 0f
                    dao.updateProgress(
                        id = downloadId,
                        progress = percent,
                        speed = null,
                        eta = null,
                        totalBytes = total,
                        downloadedBytes = downloaded,
                    )
                }
            }
            try {
                imageFetcher.fetch(request.url, workDir, request.fileName) { downloaded, total ->
                    progress.value = downloaded to total
                }
            } finally {
                reporter.cancel()
            }
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun runDownload(downloadId: String, request: DownloadRequest, args: List<String>, workDir: File) {
        val progress = MutableStateFlow<EngineProgress?>(null)
        coroutineScope {
            // El motor informa muchas veces por segundo: se guarda y notifica cada 500 ms.
            val reporter = launch {
                progress.filterNotNull().sample(PROGRESS_INTERVAL_MS).collect { update ->
                    dao.updateProgress(
                        id = downloadId,
                        progress = update.percent,
                        speed = update.speedBytesPerSecond,
                        eta = update.etaSeconds,
                        totalBytes = update.totalBytes,
                        downloadedBytes = update.downloadedBytes,
                    )
                    showForeground(
                        notifier.foregroundInfo(downloadId, request.title, update.percent, update.speedBytesPerSecond),
                    )
                }
            }
            // Diagnóstico (AnalisisPerf): cuánto tarda en empezar a llegar el archivo y cuánto dura toda la descarga.
            val host = AnalisisPerf.hostOf(request.url)
            val startedAt = System.nanoTime()
            var firstBytesLogged = false
            val onUpdate: (EngineProgress) -> Unit = { update ->
                if (!firstBytesLogged && ((update.downloadedBytes ?: 0) > 0 || update.percent > 0f)) {
                    firstBytesLogged = true
                    AnalisisPerf.duration("descarga: espera hasta el primer avance", (System.nanoTime() - startedAt) / 1_000_000, host)
                }
                progress.value = update
            }
            try {
                val savedInfo = reusableAnalysis(request, workDir)
                if (savedInfo != null && downloadFromAnalysis(downloadId, savedInfo, args, onUpdate, host)) {
                    AnalisisPerf.duration("descarga: TOTAL (reutilizando el análisis)", (System.nanoTime() - startedAt) / 1_000_000, host)
                } else {
                    engine.download(downloadId, request.url, args, onUpdate)
                    AnalisisPerf.duration("descarga: TOTAL (yt-dlp extrae de nuevo)", (System.nanoTime() - startedAt) / 1_000_000, host)
                }
            } finally {
                reporter.cancel()
            }
        }
    }

    /**
     * El análisis que el usuario acaba de ver (guardado en disco, con las direcciones directas de cada formato), si sirve
     * para esta descarga: reciente y sin subtítulos (esos se piden aparte al extraer). Se copia a la carpeta de trabajo.
     */
    private suspend fun reusableAnalysis(request: DownloadRequest, workDir: File): File? {
        if (request.subtitle != null || request.url.startsWith("file:")) return null
        val json = try {
            analysisInfo.infoJsonFor(request.url, MAX_ANALYSIS_AGE_MS)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return null
        return withContext(Dispatchers.IO) { File(workDir, ANALYSIS_FILE).apply { writeText(json) } }
    }

    /** Descarga sin volver a extraer el video. Devuelve false si no funcionó (por ejemplo, direcciones caducadas). */
    private suspend fun downloadFromAnalysis(
        downloadId: String,
        info: File,
        args: List<String>,
        onUpdate: (EngineProgress) -> Unit,
        host: String,
    ): Boolean = try {
        engine.downloadFromInfo(downloadId, info, args, onUpdate)
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        // Lo ya bajado (.part) se aprovecha: el siguiente intento continúa donde quedó.
        android.util.Log.w("DownloadWorker", "No sirvió el análisis guardado; yt-dlp extrae de nuevo", error)
        AnalisisPerf.mark("descarga: el análisis guardado no sirvió; se extrae de nuevo", host)
        false
    }

    /**
     * Detenida por el usuario (pausar/cancelar ya fijaron su estado) o por el sistema
     * (sin red, poco espacio...): en ese caso sigue programada y se reanudará sola.
     */
    private suspend fun onStopped(downloadId: String) {
        when (dao.getById(downloadId)?.status) {
            null -> storage.deleteWorkDir(downloadId) // Cancelada: se borran los parciales.
            DownloadStatus.RUNNING.name -> dao.updateStatus(downloadId, DownloadStatus.WAITING.name, null)
            else -> Unit
        }
    }

    /** La descarga ocupa su tamaño en la carpeta de trabajo y otra vez al copiarse al destino. */
    private fun hasEnoughSpace(request: DownloadRequest): Boolean {
        val needed = (request.estimatedBytes ?: 0L) * 2 + MIN_FREE_BYTES
        return storage.availableBytes() >= needed
    }

    private suspend fun publishResults(workDir: File, kind: MediaKind, treeUri: String?, toVault: Boolean): Published {
        val files = workDir.listFiles().orEmpty().filter { it.isFile }
        val media = files
            .filter { it.extension.lowercase() in DownloadStorage.mediaExtensionsFor(kind) }
            .maxByOrNull { it.length() }
            ?: throw MediaError.Unknown("No se generó ningún archivo")

        if (toVault) {
            // Los subtítulos aparte no se publican: dejarían una pista de lo descargado fuera de la carpeta privada.
            val item = vault.importFile(
                file = media,
                kind = kind,
                name = media.name,
                mimeType = DownloadStorage.mimeTypeOf(media),
                durationMs = 0,
            )
            return Published(mediaUri = Uri.parse(item.uri), mimeType = item.mimeType.orEmpty(), isPrivate = true)
        }

        files.filter { it.extension.lowercase() in DownloadStorage.SUBTITLE_EXTENSIONS }
            .forEach { storage.publishSubtitle(it, treeUri) }

        return Published(
            mediaUri = storage.publishMedia(media, kind, treeUri),
            mimeType = DownloadStorage.mimeTypeOf(media),
        )
    }

    private suspend fun fail(downloadId: String, title: String, error: Throwable): Result {
        dao.updateStatus(downloadId, DownloadStatus.FAILED.name, DownloadErrorCodes.fromError(error))
        notifier.showFailed(downloadId, title)
        queue.pump()
        return Result.failure()
    }

    private data class Published(val mediaUri: Uri, val mimeType: String, val isPrivate: Boolean = false)

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        private const val PROGRESS_INTERVAL_MS = 500L
        private const val MAX_NETWORK_RETRIES = 3
        private const val MIN_FREE_BYTES = 100L * 1024 * 1024
        // Las direcciones de YouTube caducan a las ~6 h: un análisis de más de 1 h no se reutiliza.
        private const val MAX_ANALYSIS_AGE_MS = 60L * 60 * 1000
        private const val ANALYSIS_FILE = "analysis.info.json"
        private val RUNNABLE_STATUSES = setOf(
            DownloadStatus.QUEUED.name,
            DownloadStatus.WAITING.name,
            DownloadStatus.RUNNING.name,
        )
    }
}
