package com.libredescarga.core.engine

import android.content.Context
import android.util.Log
import com.libredescarga.core.model.AnalisisPerf
import com.yausername.youtubedl_android.YoutubeDL
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Proceso de yt-dlp que queda abierto (ver assets/ytdlp_worker.py): importa yt-dlp una sola vez y atiende los análisis,
 * así cada análisis se ahorra arrancar Python e importar yt-dlp (~2,5 s). Usa el mismo Python, entorno y opciones que
 * youtubedl-android, y el mismo `yt_dlp.main(args)` que la línea de comandos: el resultado es idéntico.
 *
 * Además, yt-dlp se extrae a una carpeta (en lugar de leerse del zip) para que Python guarde sus .pyc y no tenga que
 * recompilar ~1000 archivos cada vez que arranca el proceso.
 *
 * Atiende una petición a la vez. Si está ocupado, no arranca o tarda demasiado, [tryExecute] devuelve null y quien llama
 * usa la ejecución clásica de la librería: el proceso persistente nunca es imprescindible.
 */
@Singleton
class YtDlpWorker @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    class Result(val code: Int, val out: String, val err: String)

    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var process: Process? = null
    private var input: BufferedReader? = null
    private var output: BufferedWriter? = null
    private var nextId = 0L

    /** Arranca el proceso para que el primer análisis no espere. Requiere que youtubedl-android ya esté inicializado. */
    suspend fun warmUp() {
        if (!mutex.tryLock()) return
        try {
            runBlockingStep(STARTUP_TIMEOUT_MS) { ensureStarted() }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "No se pudo preparar el proceso persistente de yt-dlp", error)
        } finally {
            mutex.unlock()
        }
    }

    /** Ejecuta yt-dlp con [args] en el proceso persistente, o devuelve null para usar la ejecución clásica. */
    suspend fun tryExecute(args: List<String>): Result? {
        if (!mutex.tryLock()) return null
        return try {
            runBlockingStep(REQUEST_TIMEOUT_MS) {
                ensureStarted()
                send(args)
            }
        } catch (error: TimeoutCancellationException) {
            Log.w(TAG, "El proceso persistente tardó demasiado; se usa la ejecución clásica")
            null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Falló el proceso persistente; se usa la ejecución clásica", error)
            stopLocked()
            null
        } finally {
            mutex.unlock()
        }
    }

    /** Cierra el proceso (por ejemplo, tras actualizar yt-dlp: el siguiente análisis arranca uno nuevo con el código nuevo). */
    fun stop() {
        process?.destroy()
        process = null
    }

    /**
     * Ejecuta [block] (bloqueante) en un hilo de E/S. Si se cancela o se pasa de [timeoutMs], se cierra el proceso: así se
     * libera la lectura bloqueada y el siguiente uso arranca uno nuevo.
     */
    private suspend fun <T> runBlockingStep(timeoutMs: Long, block: () -> T): T = coroutineScope {
        val work = async(Dispatchers.IO) { runCatching(block) }
        try {
            withTimeout(timeoutMs) { work.await() }.getOrThrow()
        } catch (error: CancellationException) {
            stopLocked()
            throw error
        }
    }

    private fun stopLocked() {
        process?.destroy()
        process = null
        input = null
        output = null
    }

    private fun ensureStarted() {
        if (process?.isRunning() == true) return
        AnalisisPerf.measure("yt-dlp persistente: arranque (Python + importar yt-dlp)") {
            val library = LibraryPaths.read()
            val sourceDir = extractSources(library.ytdlp)
            val script = File(workDir(), "ytdlp_worker.py")
            context.assets.open(SCRIPT_ASSET).use { asset -> script.outputStream().use { asset.copyTo(it) } }

            val builder = ProcessBuilder(library.python, script.absolutePath, sourceDir.absolutePath)
            builder.environment().apply {
                put("LD_LIBRARY_PATH", library.ldLibraryPath)
                put("SSL_CERT_FILE", library.sslCertFile)
                put("PATH", "${System.getenv("PATH")}:${library.binDir}")
                put("PYTHONHOME", library.pythonHome)
                put("HOME", library.pythonHome)
                put("TMPDIR", library.tmpDir)
                put("PYTHONIOENCODING", "utf-8")
                remove("PYTHONDONTWRITEBYTECODE")
            }
            val started = builder.start()
            // Errores de arranque de Python (stderr): se leen aparte para que el proceso nunca se bloquee al escribirlos.
            Thread {
                runCatching { started.errorStream.bufferedReader().forEachLine { Log.w(TAG, "python: $it") } }
            }.apply { isDaemon = true }.start()

            val reader = started.inputStream.bufferedReader(Charsets.UTF_8)
            val ready = reader.readLine()?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
            if (ready?.get("ready")?.jsonPrimitive?.booleanOrNull != true) {
                started.destroy()
                throw IOException("El proceso persistente de yt-dlp no arrancó")
            }
            process = started
            input = reader
            output = started.outputStream.bufferedWriter(Charsets.UTF_8)
            quickJsArgs = library.extraArgs
        }
    }

    @Volatile
    private var quickJsArgs: List<String> = emptyList()

    private fun send(args: List<String>): Result {
        val writer = output ?: throw IOException("Proceso no iniciado")
        val reader = input ?: throw IOException("Proceso no iniciado")
        val id = ++nextId
        val request = buildJsonObject {
            put("id", id)
            // Las mismas opciones que añade youtubedl-android (QuickJS para YouTube, FFmpeg y sin caché en disco).
            putJsonArray("args") { (quickJsArgs + args).forEach { add(it) } }
        }
        writer.write(request.toString())
        writer.newLine()
        writer.flush()
        while (true) {
            val line = reader.readLine() ?: throw IOException("El proceso persistente de yt-dlp se cerró")
            val response = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: continue
            if (response["id"]?.jsonPrimitive?.long != id) continue
            return Result(
                code = response["code"]?.jsonPrimitive?.int ?: 1,
                out = (response["out"] as? JsonPrimitive)?.content.orEmpty(),
                err = (response["err"] as? JsonPrimitive)?.content.orEmpty(),
            )
        }
    }

    private fun workDir(): File = File(context.noBackupFilesDir, WORK_DIR).apply { mkdirs() }

    /** Process.isAlive() no existe en Android 7: un proceso que aún corre no tiene código de salida. */
    private fun Process.isRunning(): Boolean = try {
        exitValue()
        false
    } catch (_: IllegalThreadStateException) {
        true
    }

    /**
     * Extrae el zip de yt-dlp a una carpeta (solo si cambió: tras una actualización). Python guardará ahí sus .pyc,
     * cosa que no puede hacer dentro del zip.
     */
    private fun extractSources(zip: File): File {
        val target = File(workDir(), "src")
        val stamp = File(workDir(), "src.stamp")
        val expected = "${zip.length()}:${zip.lastModified()}"
        if (target.isDirectory && stamp.exists() && stamp.readText() == expected) return target

        AnalisisPerf.measure("yt-dlp persistente: extraer código de yt-dlp (solo tras actualizar)") {
            target.deleteRecursively()
            target.mkdirs()
            val root = target.canonicalPath + File.separator
            // El archivo de yt-dlp es un "zipapp": empieza con "#!/usr/bin/env python3" y luego el zip. ZipFile de Android
            // no acepta ese prefijo, así que se lee como flujo desde la primera cabecera del zip ("PK\3\4").
            val bytes = zip.readBytes()
            val start = (0..bytes.size - ZIP_MAGIC.size).firstOrNull { index ->
                ZIP_MAGIC.indices.all { bytes[index + it] == ZIP_MAGIC[it] }
            } ?: throw IOException("El archivo de yt-dlp no contiene un zip")
            ZipInputStream(ByteArrayInputStream(bytes, start, bytes.size - start)).use { archive ->
                while (true) {
                    val entry = archive.nextEntry ?: break
                    val file = File(target, entry.name)
                    // Protección contra rutas que intenten salir de la carpeta ("zip slip").
                    if (!file.canonicalPath.startsWith(root)) continue
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        file.outputStream().use { archive.copyTo(it) }
                    }
                }
            }
            stamp.writeText(expected)
        }
        return target
    }

    /** Rutas y entorno que youtubedl-android preparó al inicializarse (se leen de sus campos internos). */
    private class LibraryPaths(
        val python: String,
        val ytdlp: File,
        val binDir: String,
        val ldLibraryPath: String,
        val sslCertFile: String,
        val pythonHome: String,
        val tmpDir: String,
        val extraArgs: List<String>,
    ) {
        companion object {
            fun read(): LibraryPaths {
                fun field(name: String): Any? = YoutubeDL::class.java.getDeclaredField(name)
                    .apply { isAccessible = true }
                    .get(null)

                val quickJs = field("quickJsPath") as? File
                val ffmpeg = field("ffmpegPath") as? File
                return LibraryPaths(
                    python = (field("pythonPath") as File).absolutePath,
                    ytdlp = field("ytdlpPath") as File,
                    binDir = (field("binDir") as File).absolutePath,
                    ldLibraryPath = field("ENV_LD_LIBRARY_PATH") as String,
                    sslCertFile = field("ENV_SSL_CERT_FILE") as String,
                    pythonHome = field("ENV_PYTHONHOME") as String,
                    tmpDir = field("TMPDIR") as String,
                    extraArgs = buildList {
                        add("--no-cache-dir")
                        quickJs?.let { addAll(listOf("--js-runtimes", "quickjs:${it.absolutePath}")) }
                        ffmpeg?.let { addAll(listOf("--ffmpeg-location", it.absolutePath)) }
                    },
                )
            }
        }
    }

    private companion object {
        const val TAG = "YtDlpWorker"
        const val SCRIPT_ASSET = "ytdlp_worker.py"
        const val WORK_DIR = "ytdlp-worker"
        const val STARTUP_TIMEOUT_MS = 60_000L
        const val REQUEST_TIMEOUT_MS = 90_000L
        val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    }
}
