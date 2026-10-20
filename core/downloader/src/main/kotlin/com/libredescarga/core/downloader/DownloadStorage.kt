package com.libredescarga.core.downloader

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile
import com.libredescarga.core.config.AppConfig
import com.libredescarga.core.model.MediaKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Dónde se guardan las descargas terminadas:
 * - por defecto, carpetas públicas mediante MediaStore (Android 10+, sin permisos):
 *   videos → Movies/LibreDescarga, audio → Music/LibreDescarga, subtítulos → Download/LibreDescarga;
 * - o una carpeta elegida por el usuario con el Storage Access Framework (incluida la tarjeta SD).
 *
 * En Android 7–9 se escribe en la carpeta pública con WRITE_EXTERNAL_STORAGE.
 */
@Singleton
class DownloadStorage @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val workRoot: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "work")

    /** Carpeta temporal privada donde trabaja yt-dlp para una descarga. */
    fun workDirFor(downloadId: String): File = File(workRoot, downloadId).apply { mkdirs() }

    fun deleteWorkDir(downloadId: String) {
        File(workRoot, downloadId).deleteRecursively()
    }

    /**
     * Copia un archivo del teléfono a una subcarpeta de trabajo para convertirlo.
     * Va en una subcarpeta para no confundirlo con el resultado.
     */
    suspend fun copyLocalSource(contentUri: String, workDir: File): File = withContext(Dispatchers.IO) {
        val uri = Uri.parse(contentUri)
        val extension = context.contentResolver.getType(uri)
            ?.let { android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?: "mp4"
        val folder = File(workDir, SOURCE_DIR).apply { mkdirs() }
        val target = File(folder, "source.$extension")
        if (!target.exists()) {
            // Se copia a un temporal y se renombra al final: una copia a medias nunca se reutiliza.
            val partial = File(folder, "source.partial")
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("No se pudo leer $contentUri")
            input.use { source -> partial.outputStream().use { source.copyTo(it, BUFFER_SIZE) } }
            if (!partial.renameTo(target)) throw IOException("No se pudo preparar el archivo de origen")
        }
        target
    }

    fun deleteLocalSource(workDir: File) {
        File(workDir, SOURCE_DIR).deleteRecursively()
    }

    /** Espacio libre donde se descarga (los archivos pasan primero por la carpeta de trabajo). */
    fun availableBytes(): Long {
        val root = workRoot.apply { mkdirs() }
        return StatFs(root.path).availableBytes
    }

    /** Nombre visible de una carpeta elegida, o null si ya no hay permiso para escribir en ella. */
    fun folderName(treeUri: String): String? = runCatching {
        DocumentFile.fromTreeUri(context, Uri.parse(treeUri))?.takeIf { it.canWrite() }?.name
    }.getOrNull()

    suspend fun publishMedia(file: File, kind: MediaKind, treeUri: String?): Uri = withContext(Dispatchers.IO) {
        treeUri?.let { publishToTree(file, it) } ?: publish(
            file,
            when (kind) {
                MediaKind.VIDEO -> Target.Video
                MediaKind.AUDIO -> Target.Audio
                MediaKind.IMAGE -> Target.Image
            },
        )
    }

    suspend fun publishSubtitle(file: File, treeUri: String?): Uri = withContext(Dispatchers.IO) {
        treeUri?.let { publishToTree(file, it) } ?: publish(file, Target.Subtitle)
    }

    /** Borra un archivo descargado. Devuelve false si ya no existe o no hay permiso. */
    suspend fun delete(uriString: String): Boolean = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriString)
        try {
            when {
                uri.scheme == "file" -> uri.path?.let { File(it).delete() } ?: false
                DocumentsContract.isDocumentUri(context, uri) ->
                    DocumentsContract.deleteDocument(context.contentResolver, uri)
                else -> context.contentResolver.delete(uri, null, null) > 0
            }
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    /** Copia a la carpeta elegida; si ya no hay permiso, devuelve null y se usa la carpeta del sistema. */
    private fun publishToTree(file: File, treeUri: String): Uri? {
        val folder = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) }.getOrNull()
            ?.takeIf { it.canWrite() } ?: return null
        val document = folder.createFile(mimeTypeOf(file), file.name) ?: return null
        try {
            val output = context.contentResolver.openOutputStream(document.uri)
                ?: throw IOException("No se pudo abrir ${document.uri}")
            output.use { out -> file.inputStream().use { it.copyTo(out, BUFFER_SIZE) } }
        } catch (error: Exception) {
            document.delete()
            throw error
        }
        return document.uri
    }

    private suspend fun publish(file: File, target: Target): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishScoped(file, target)
        } else {
            publishLegacy(file, target)
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun publishScoped(file: File, target: Target): Uri {
        val resolver = context.contentResolver
        val collection = when (target) {
            Target.Video -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            Target.Audio -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            Target.Image -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            Target.Subtitle -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeTypeOf(file))
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${target.directory}/${AppConfig.DOWNLOAD_FOLDER_NAME}")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: throw IOException("MediaStore no aceptó el archivo ${file.name}")
        try {
            val output = resolver.openOutputStream(uri) ?: throw IOException("No se pudo abrir $uri")
            output.use { out -> file.inputStream().use { it.copyTo(out, BUFFER_SIZE) } }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
        return uri
    }

    @Suppress("DEPRECATION")
    private suspend fun publishLegacy(file: File, target: Target): Uri {
        val publicDir = Environment.getExternalStoragePublicDirectory(target.directory)
        val folder = File(publicDir, AppConfig.DOWNLOAD_FOLDER_NAME)
        if (!folder.exists() && !folder.mkdirs()) throw IOException("No se pudo crear $folder")

        val destination = uniqueFile(folder, file.name)
        file.inputStream().use { input ->
            destination.outputStream().use { input.copyTo(it, BUFFER_SIZE) }
        }
        return scan(destination) ?: Uri.fromFile(destination)
    }

    private fun uniqueFile(folder: File, name: String): File {
        val base = name.substringBeforeLast('.')
        val extension = name.substringAfterLast('.', missingDelimiterValue = "")
        var candidate = File(folder, name)
        var index = 1
        while (candidate.exists()) {
            val suffix = if (extension.isEmpty()) "" else ".$extension"
            candidate = File(folder, "$base ($index)$suffix")
            index++
        }
        return candidate
    }

    private suspend fun scan(file: File): Uri? = suspendCancellableCoroutine { continuation ->
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf(mimeTypeOf(file)),
        ) { _, uri -> if (continuation.isActive) continuation.resume(uri) }
    }

    private enum class Target(val directory: String) {
        Video(Environment.DIRECTORY_MOVIES),
        Audio(Environment.DIRECTORY_MUSIC),
        Image(Environment.DIRECTORY_PICTURES),
        Subtitle(Environment.DIRECTORY_DOWNLOADS),
    }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
        private const val SOURCE_DIR = "source"

        private val MIME_TYPES = mapOf(
            "mp4" to "video/mp4",
            "m4v" to "video/mp4",
            "webm" to "video/webm",
            "mkv" to "video/x-matroska",
            "mov" to "video/quicktime",
            "3gp" to "video/3gpp",
            "mp3" to "audio/mpeg",
            "m4a" to "audio/mp4",
            "aac" to "audio/aac",
            "opus" to "audio/ogg",
            "ogg" to "audio/ogg",
            "flac" to "audio/flac",
            "wav" to "audio/wav",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "png" to "image/png",
            "webp" to "image/webp",
            "gif" to "image/gif",
            "bmp" to "image/bmp",
            "avif" to "image/avif",
            "heic" to "image/heic",
            "heif" to "image/heif",
            "srt" to "application/x-subrip",
            "vtt" to "text/vtt",
        )

        val SUBTITLE_EXTENSIONS = setOf("srt", "vtt")
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif", "heic", "heif")

        /** Extensiones que pueden ser el resultado principal de una descarga de este tipo. */
        fun mediaExtensionsFor(kind: MediaKind): Set<String> = when (kind) {
            MediaKind.IMAGE -> IMAGE_EXTENSIONS
            // En video y audio las imágenes sueltas son carátulas temporales, no el resultado.
            MediaKind.VIDEO, MediaKind.AUDIO -> MIME_TYPES.keys - SUBTITLE_EXTENSIONS - IMAGE_EXTENSIONS
        }

        fun mimeTypeOf(file: File): String =
            MIME_TYPES[file.extension.lowercase()] ?: "application/octet-stream"
    }
}
