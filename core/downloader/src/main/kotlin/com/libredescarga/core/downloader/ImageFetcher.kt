package com.libredescarga.core.downloader

import com.libredescarga.core.domain.repository.PageImageResolver
import com.libredescarga.core.domain.repository.RemoteFileSizeProbe
import com.libredescarga.core.domain.usecase.ImageDownloads
import com.libredescarga.core.model.MediaError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Descarga imágenes directamente (sin yt-dlp, que está pensado para video y audio) y también
 * encuentra la imagen principal de una página web.
 *
 * Reglas de seguridad: solo https (una dirección http se convierte a https), las redirecciones
 * también deben ser https, solo se aceptan respuestas que sean imágenes y hay un tamaño máximo.
 */
@Singleton
class ImageFetcher @Inject constructor() : PageImageResolver, RemoteFileSizeProbe {

    /**
     * Guarda la imagen de [url] dentro de [targetDir] con el nombre [baseName] y la extensión que corresponda.
     * [onProgress] recibe los bytes descargados y el total (null si el servidor no lo informa).
     * En Pinterest primero intenta la versión original, que es más grande.
     */
    suspend fun fetch(
        url: String,
        targetDir: File,
        baseName: String,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        targetDir.mkdirs()
        val candidates = ImageDownloads.candidates(url.toHttps())
        var lastError: MediaError? = null
        for (candidate in candidates) {
            try {
                return@withContext download(candidate, targetDir, baseName, onProgress)
            } catch (error: MediaError) {
                lastError = error // Se prueba con la siguiente dirección, si hay.
            }
        }
        throw lastError ?: MediaError.Unknown("No se pudo descargar la imagen")
    }

    /** Lee la página [pageUrl] (solo el inicio) y devuelve su imagen principal, o null si no tiene o no se pudo leer. */
    override suspend fun mainImageOf(pageUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val html = request(pageUrl.toHttps()) { connection, _ ->
                val type = connection.contentType?.lowercase().orEmpty()
                if ("html" !in type) return@request null
                readLimited(connection)
            } ?: return@withContext null
            ImageDownloads.mainImageFromHtml(html, pageUrl.toHttps())
        } catch (error: MediaError) {
            null
        }
    }

    /** Pregunta al servidor cuánto pesa el archivo (petición HEAD, sin descargarlo). */
    override suspend fun sizeOf(url: String): Long? = withContext(Dispatchers.IO) {
        try {
            request(url.toHttps(), method = "HEAD") { connection, _ -> connection.contentLengthLong.takeIf { it > 0 } }
        } catch (error: MediaError) {
            null
        }
    }

    private suspend fun download(
        url: String,
        targetDir: File,
        baseName: String,
        onProgress: (Long, Long?) -> Unit,
    ): File = request(url) { connection, finalUrl ->
        save(connection, targetDir, baseName, finalUrl, onProgress)
    } ?: throw MediaError.Unknown("Respuesta vacía")

    /**
     * Abre [url] siguiendo las redirecciones a mano (cada una debe ser https) y entrega la respuesta correcta a [handle].
     * Convierte los errores de red y los códigos HTTP en [MediaError].
     */
    private suspend fun <T> request(
        url: String,
        method: String = "GET",
        handle: suspend (HttpURLConnection, String) -> T?,
    ): T? {
        var current = url
        // Muchas páginas bloquean imágenes pedidas "desde fuera": se indica que vienen de su propio sitio.
        val referer = origin(current)
        for (attempt in 0..MAX_REDIRECTS) {
            val connection = open(current, referer, method)
            try {
                val code = connection.responseCode
                when {
                    code in REDIRECT_CODES -> {
                        val location = connection.getHeaderField("Location") ?: throw MediaError.Unknown("Redirección inválida")
                        current = URL(URL(current), location).toString().toHttps()
                    }
                    code in 200..299 -> return handle(connection, current)
                    code == 404 || code == 410 -> throw MediaError.Unavailable()
                    code == 401 || code == 403 -> throw MediaError.NotPublic()
                    else -> throw MediaError.Unknown("Error HTTP $code")
                }
            } catch (error: IOException) {
                throw MediaError.Network()
            } finally {
                connection.disconnect()
            }
        }
        throw MediaError.Unknown("Demasiadas redirecciones")
    }

    private fun open(url: String, referer: String?, method: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.instanceFollowRedirects = false // Se siguen a mano para exigir https en cada paso.
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Accept", "text/html,image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
        connection.setRequestProperty("Accept-Language", "es,en;q=0.8")
        if (referer != null) connection.setRequestProperty("Referer", referer)
        return connection
    }

    /** Lee como máximo [MAX_HTML_BYTES] de la respuesta: las etiquetas de vista previa están al inicio de la página. */
    private fun readLimited(connection: HttpURLConnection): String {
        val output = java.io.ByteArrayOutputStream()
        connection.inputStream.use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (output.size() < MAX_HTML_BYTES) {
                val read = input.read(buffer, 0, minOf(buffer.size, MAX_HTML_BYTES - output.size()))
                if (read < 0) break
                output.write(buffer, 0, read)
            }
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private suspend fun save(
        connection: HttpURLConnection,
        targetDir: File,
        baseName: String,
        finalUrl: String,
        onProgress: (Long, Long?) -> Unit,
    ): File {
        val contentType = connection.contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        if (!contentType.startsWith("image/")) throw MediaError.Unsupported()
        val total = connection.contentLengthLong.takeIf { it > 0 }
        if (total != null && total > MAX_BYTES) throw MediaError.Unsupported()

        val extension = extensionFor(contentType, finalUrl)
        val partial = File(targetDir, "$baseName.part")
        val target = File(targetDir, "$baseName.$extension")
        try {
            var downloaded = 0L
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive() // Permite cancelar en cualquier momento.
                        val read = input.read(buffer)
                        if (read < 0) break
                        downloaded += read
                        if (downloaded > MAX_BYTES) throw MediaError.Unsupported()
                        output.write(buffer, 0, read)
                        onProgress(downloaded, total)
                    }
                }
            }
            if (downloaded == 0L) throw MediaError.Unavailable()
            if (!partial.renameTo(target)) throw IOException("No se pudo guardar la imagen")
        } catch (error: Throwable) {
            partial.delete() // Nunca queda un archivo a medias.
            throw error
        }
        return target
    }

    private fun extensionFor(contentType: String, url: String): String {
        CONTENT_TYPE_EXTENSIONS[contentType]?.let { return it }
        val fromUrl = url.substringBefore('#').substringBefore('?').substringAfterLast('.', "").lowercase()
        return if (fromUrl in KNOWN_EXTENSIONS) fromUrl else "jpg"
    }

    private fun String.toHttps(): String =
        if (startsWith("http://", ignoreCase = true)) "https://" + substring(HTTP_PREFIX_LENGTH) else this

    private fun origin(url: String): String? = runCatching {
        val parsed = URL(url)
        "${parsed.protocol}://${parsed.host}/"
    }.getOrNull()

    private companion object {
        const val TIMEOUT_MS = 20_000
        const val BUFFER_SIZE = 32 * 1024
        const val MAX_REDIRECTS = 5
        const val MAX_BYTES = 100L * 1024 * 1024
        const val MAX_HTML_BYTES = 2 * 1024 * 1024
        const val HTTP_PREFIX_LENGTH = 7 // "http://"
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        val CONTENT_TYPE_EXTENSIONS = mapOf(
            "image/jpeg" to "jpg",
            "image/jpg" to "jpg",
            "image/png" to "png",
            "image/webp" to "webp",
            "image/gif" to "gif",
            "image/bmp" to "bmp",
            "image/avif" to "avif",
            "image/heic" to "heic",
            "image/heif" to "heif",
        )
        val KNOWN_EXTENSIONS = CONTENT_TYPE_EXTENSIONS.values.toSet() + "jpeg"
    }
}
