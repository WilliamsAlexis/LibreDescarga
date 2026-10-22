package com.libredescarga.core.engine

import android.content.Context
import android.os.Build
import com.libredescarga.core.model.MediaError
import com.libredescarga.core.model.SearchProvider
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class EngineUpdateResult { UPDATED, ALREADY_UP_TO_DATE }

/**
 * Envoltorio de yt-dlp (youtubedl-android). Todo se ejecuta en el teléfono:
 * no hay ningún servidor intermedio.
 */
@Singleton
class YtDlpEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val initMutex = Mutex()

    @Volatile
    private var initialized = false

    private val json = Json { ignoreUnknownKeys = true }

    /** Extrae Python, yt-dlp y FFmpeg la primera vez (unos segundos); después es inmediato. */
    suspend fun initialize() {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return
            withContext(Dispatchers.IO) {
                try {
                    resetEngineFilesIfNewVersion()
                    YoutubeDL.init(context)
                    FFmpeg.init(context)
                    markEngineFilesValid()
                } catch (error: YoutubeDLException) {
                    throw MediaError.Unknown(error.message)
                } catch (error: LinkageError) {
                    // Librerías nativas que no cargan en este teléfono: error normal, no cierre de la app.
                    throw MediaError.Unknown(error.message)
                }
            }
            initialized = true
        }
    }

    /**
     * Si la app cambió de versión desde la última extracción correcta, se borran los archivos del motor
     * (solo su carpeta interna) para extraerlos de nuevo. Repara instalaciones que quedaron con Python o
     * yt-dlp a medias o corruptos (error "zlib.error ... truncated stream") sin pedir borrar los datos de la app.
     */
    private fun resetEngineFilesIfNewVersion() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getLong(KEY_ENGINE_FILES_VERSION, -1L) == appVersionCode()) return
        File(context.noBackupFilesDir, ENGINE_DIR).deleteRecursively()
        // El yt-dlp incluido en la app es viejo: se fuerza actualizarlo en el próximo arranque.
        prefs.edit().remove(KEY_LAST_UPDATE_CHECK).apply()
    }

    private fun markEngineFilesValid() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong(KEY_ENGINE_FILES_VERSION, appVersionCode()).apply()
    }

    @Suppress("DEPRECATION")
    private fun appVersionCode(): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
    }

    /** Analiza un enlace sin descargar nada. Las listas se devuelven planas (rápido). */
    suspend fun fetchInfo(url: String): JsonObject = runJson(
        target = url,
        options = listOf("--dump-single-json", "--flat-playlist", "--no-playlist", "--no-warnings"),
    )

    suspend fun search(provider: SearchProvider, query: String, limit: Int): JsonObject = runJson(
        target = "${provider.searchPrefix}$limit:$query",
        options = listOf("--dump-single-json", "--flat-playlist", "--no-warnings"),
    )

    /**
     * Descarga con los [options] indicados. Se puede cancelar cancelando la corrutina
     * o con [cancel] usando el mismo [processId].
     */
    suspend fun download(
        processId: String,
        url: String,
        options: List<String>,
        onProgress: (EngineProgress) -> Unit,
    ) {
        initialize()
        val request = YoutubeDLRequest(url).addCommands(options)
        try {
            runDownload(processId, request, onProgress)
        } catch (error: MediaError) {
            // Extractor roto por un cambio del sitio (TikTok, Facebook...): se actualiza yt-dlp y se reintenta una vez.
            if ((error is MediaError.Unknown || error is MediaError.Unsupported) && updateForRetry()) {
                runDownload(processId, YoutubeDLRequest(url).addCommands(options), onProgress)
            } else {
                throw error
            }
        }
    }

    private suspend fun runDownload(processId: String, request: YoutubeDLRequest, onProgress: (EngineProgress) -> Unit) {
        execute(processId, request) { percent, eta, line ->
            onProgress(ProgressLineParser.parse(percent, eta, line))
        }
    }

    fun cancel(processId: String): Boolean = YoutubeDL.destroyProcessById(processId)

    /** Actualiza yt-dlp desde sus versiones estables oficiales, sin reinstalar la app. */
    suspend fun update(): EngineUpdateResult {
        initialize()
        return withContext(Dispatchers.IO) {
            try {
                when (YoutubeDL.updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)) {
                    YoutubeDL.UpdateStatus.DONE -> EngineUpdateResult.UPDATED
                    else -> EngineUpdateResult.ALREADY_UP_TO_DATE
                }
            } catch (error: YoutubeDLException) {
                throw EngineErrorMapper.map(error.message)
            }
        }
    }

    /**
     * Actualiza el motor solo si pasó más de [maxAgeMs] desde la última comprobación exitosa
     * (por defecto, una semana). Si no hay internet no hace nada y se reintenta en el próximo arranque.
     * YouTube cambia seguido: un motor viejo termina fallando con errores como el 403.
     */
    suspend fun updateIfStale(maxAgeMs: Long = WEEK_MS) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        if (System.currentTimeMillis() - last < maxAgeMs) return
        try {
            update()
            prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, System.currentTimeMillis()).apply()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Sin internet o GitHub no disponible: no pasa nada, se vuelve a intentar al abrir la app.
        }
    }

    /** Versión instalada de yt-dlp, por ejemplo "2026.09.27". */
    suspend fun version(): String? {
        initialize()
        return withContext(Dispatchers.IO) { YoutubeDL.versionName(context) }
    }

    private companion object {
        const val PREFS_NAME = "engine_prefs"
        const val KEY_LAST_UPDATE_CHECK = "last_update_check"
        const val KEY_LAST_FORCED_UPDATE = "last_forced_update"
        const val FORCED_UPDATE_INTERVAL_MS = 6L * 60 * 60 * 1000
        const val KEY_ENGINE_FILES_VERSION = "engine_files_version"
        const val ENGINE_DIR = "youtubedl-android"
        const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    }

    /**
     * Si yt-dlp no logra leer el enlace (sitios como TikTok o Facebook cambian seguido y rompen sus extractores),
     * se actualiza el motor y se reintenta una vez. Como máximo una actualización forzada cada 6 horas.
     */
    private suspend fun runJson(target: String, options: List<String>): JsonObject {
        initialize()
        return try {
            runJsonOnce(target, options)
        } catch (error: MediaError) {
            if ((error is MediaError.Unknown || error is MediaError.Unsupported) && updateForRetry()) {
                runJsonOnce(target, options)
            } else {
                throw error
            }
        }
    }

    private suspend fun updateForRetry(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_FORCED_UPDATE, 0L) < FORCED_UPDATE_INTERVAL_MS) return false
        prefs.edit().putLong(KEY_LAST_FORCED_UPDATE, now).apply()
        return try {
            update() == EngineUpdateResult.UPDATED
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun runJsonOnce(target: String, options: List<String>): JsonObject {
        val request = YoutubeDLRequest(target).addCommands(options)
        val response = execute(UUID.randomUUID().toString(), request, callback = null)
        val line = response.out.lineSequence().map { it.trim() }.lastOrNull { it.startsWith("{") }
            ?: throw EngineErrorMapper.map(response.err)
        return try {
            json.parseToJsonElement(line).jsonObject
        } catch (error: IllegalArgumentException) {
            throw MediaError.Unknown(error.message)
        }
    }

    private suspend fun execute(
        processId: String,
        request: YoutubeDLRequest,
        callback: ((Float, Long, String) -> Unit)?,
    ): YoutubeDLResponse = try {
        runInterruptible(Dispatchers.IO) {
            if (callback == null) {
                YoutubeDL.execute(request, processId)
            } else {
                YoutubeDL.execute(request, processId, callback)
            }
        }
    } catch (error: CancellationException) {
        YoutubeDL.destroyProcessById(processId)
        throw error
    } catch (_: YoutubeDL.CanceledException) {
        throw CancellationException("Descarga cancelada")
    } catch (_: InterruptedException) {
        YoutubeDL.destroyProcessById(processId)
        throw CancellationException("Descarga interrumpida")
    } catch (error: YoutubeDLException) {
        android.util.Log.w("YtDlpEngine", "yt-dlp falló: ${error.message}")
        throw EngineErrorMapper.map(error.message)
    }
}
