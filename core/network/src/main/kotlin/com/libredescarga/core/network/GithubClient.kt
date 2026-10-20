package com.libredescarga.core.network

import com.libredescarga.core.model.UpdateException
import com.libredescarga.core.model.UpdateFailure
import com.libredescarga.core.security.UpdateSecurity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Único punto de acceso a internet de la app fuera del motor de descarga y del navegador.
 * Solo habla con GitHub (https) y no envía cookies, cuentas ni identificadores.
 */
@Singleton
class GithubClient @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.MINUTES)
        .followRedirects(true)
        .followSslRedirects(false) // Nunca se sigue una redirección hacia http.
        .build()

    /** Descarga un texto pequeño (JSON), con un tope de tamaño para no llenar la memoria. */
    suspend fun fetchText(url: String, maxBytes: Long = DEFAULT_MAX_TEXT_BYTES): String {
        requireAllowed(url)
        return execute(url) { response ->
            val body = response.body ?: throw UpdateException(UpdateFailure.NETWORK, "Respuesta vacía")
            val declared = body.contentLength()
            if (declared > maxBytes) throw UpdateException(UpdateFailure.INVALID_MANIFEST, "Archivo demasiado grande")
            val source = body.source()
            // Se pide un byte más que el máximo: si llega, el archivo es demasiado grande.
            source.request(maxBytes + 1)
            if (source.buffer.size > maxBytes) throw UpdateException(UpdateFailure.INVALID_MANIFEST, "Archivo demasiado grande")
            val bytes = source.readByteArray()
            bytes.toString(Charsets.UTF_8)
        }
    }

    /** Descarga [url] a [target], informando el avance (0 a 1; 0 si el servidor no dice el tamaño). */
    suspend fun downloadTo(url: String, target: File, onProgress: (Float) -> Unit) {
        requireAllowed(url)
        execute(url) { response ->
            val body = response.body ?: throw UpdateException(UpdateFailure.NETWORK, "Respuesta vacía")
            val total = body.contentLength()
            var written = 0L
            try {
                body.byteStream().use { input ->
                    target.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            written += read
                            if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            } catch (error: IOException) {
                target.delete()
                // Sin espacio en el teléfono es un caso aparte, con su propio mensaje.
                val noSpace = error.message?.contains("ENOSPC", ignoreCase = true) == true ||
                    error.message?.contains("No space", ignoreCase = true) == true
                throw UpdateException(if (noSpace) UpdateFailure.STORAGE else UpdateFailure.NETWORK, cause = error)
            }
        }
    }

    private fun requireAllowed(url: String) {
        if (!UpdateSecurity.isAllowedDownloadUrl(url)) {
            throw UpdateException(UpdateFailure.INVALID_MANIFEST, "Dirección no permitida")
        }
    }

    private suspend fun <T> execute(url: String, handle: (Response) -> T): T = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        val response = awaitResponse(client.newCall(request))
        response.use {
            if (!it.isSuccessful) throw UpdateException(UpdateFailure.NETWORK, "HTTP ${it.code}")
            handle(it)
        }
    }

    private suspend fun awaitResponse(call: Call): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onResponse(call: Call, response: Response) = continuation.resume(response)

                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(UpdateException(UpdateFailure.NETWORK, cause = e))
                    }
                }
            },
        )
    }

    private companion object {
        const val DEFAULT_MAX_TEXT_BYTES = 512L * 1024
        const val USER_AGENT = "LibreDescarga-Updater"
    }
}
