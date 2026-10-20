package com.libredescarga.core.media

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.libredescarga.core.model.DuplicateGroup
import com.libredescarga.core.model.DuplicateScanState
import com.libredescarga.core.model.LibraryMedia
import com.libredescarga.core.model.MediaKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Busca videos, música e imágenes repetidos. Solo lee: no borra ni modifica nada.
 *
 * Primero se agrupan los archivos por tipo y tamaño (rápido); solo los que coinciden se comparan
 * leyendo su contenido (SHA-256), así que dos archivos con distinto nombre pero idénticos se detectan
 * y dos con el mismo nombre pero distinto contenido no.
 */
@Singleton
class DuplicateScanner @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun scan(): Flow<DuplicateScanState> = flow {
        emit(DuplicateScanState.Scanning(done = 0, total = 0))
        val candidates = MediaKind.entries
            .flatMap { list(it) }
            .filter { it.sizeBytes > 0 }
            .groupBy { it.kind to it.sizeBytes }
            .values
            .filter { it.size > 1 }

        val total = candidates.sumOf { it.size }
        var done = 0
        val groups = mutableListOf<DuplicateGroup>()
        for (sameSize in candidates) {
            val byHash = mutableMapOf<String, MutableList<LibraryMedia>>()
            for (item in sameSize) {
                currentCoroutineContext().ensureActive()
                hash(item.uri)?.let { byHash.getOrPut(it) { mutableListOf() } += item }
                done++
                emit(DuplicateScanState.Scanning(done, total))
            }
            byHash.values.filter { it.size > 1 }.forEach { copies ->
                groups += DuplicateGroup(kind = copies.first().kind, items = copies.sortedBy { it.dateAdded })
            }
        }
        emit(DuplicateScanState.Done(groups.sortedByDescending { it.reclaimableBytes }))
    }.flowOn(Dispatchers.IO)

    /** SHA-256 del contenido, o null si el archivo no se puede leer (se ignora en la comparación). */
    private suspend fun hash(uri: String): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        val stream = context.contentResolver.openInputStream(Uri.parse(uri))
        if (stream == null) {
            null
        } else {
            stream.use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    private fun list(kind: MediaKind): List<LibraryMedia> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.MIME_TYPE,
            folderColumn(),
        )
        // En música se excluyen tonos y notificaciones, igual que la biblioteca.
        val selection = if (kind == MediaKind.AUDIO) "${MediaStore.Audio.AudioColumns.IS_MUSIC} != 0" else null
        val items = mutableListOf<LibraryMedia>()
        try {
            context.contentResolver.query(collection(kind), projection, selection, null, null)?.use { cursor ->
                while (cursor.moveToNext()) items += cursor.toMedia(kind)
            }
        } catch (_: SecurityException) {
            // Sin permiso de archivos solo se ve lo que creó la app; aquí no hay nada que comparar.
        }
        return items
    }

    @Suppress("DEPRECATION")
    private fun folderColumn(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.MediaColumns.RELATIVE_PATH
        } else {
            MediaStore.MediaColumns.DATA
        }

    @Suppress("DEPRECATION")
    private fun Cursor.toMedia(kind: MediaKind): LibraryMedia {
        val id = getLong(getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
        val name = string(MediaStore.MediaColumns.DISPLAY_NAME) ?: id.toString()
        val folder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            string(MediaStore.MediaColumns.RELATIVE_PATH)?.trimEnd('/').orEmpty()
        } else {
            val root = Environment.getExternalStorageDirectory().absolutePath
            string(MediaStore.MediaColumns.DATA)?.let { File(it).parent }
                ?.removePrefix(root)?.trim('/').orEmpty()
        }
        return LibraryMedia(
            uri = ContentUris.withAppendedId(baseUri(kind), id).toString(),
            kind = kind,
            title = name.substringBeforeLast('.'),
            displayName = name,
            folder = folder,
            sizeBytes = long(MediaStore.MediaColumns.SIZE) ?: 0,
            durationMs = 0,
            dateAdded = (long(MediaStore.MediaColumns.DATE_ADDED) ?: 0) * 1000,
            mimeType = string(MediaStore.MediaColumns.MIME_TYPE),
        )
    }

    private fun Cursor.string(column: String): String? =
        getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getString)

    private fun Cursor.long(column: String): Long? =
        getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getLong)

    /** En Android 10+ se consultan todos los volúmenes (memoria interna y tarjeta SD). */
    private fun collection(kind: MediaKind): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (kind) {
                MediaKind.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                MediaKind.AUDIO -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                MediaKind.IMAGE -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            }
        } else {
            baseUri(kind)
        }

    private fun baseUri(kind: MediaKind): Uri = when (kind) {
        MediaKind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        MediaKind.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        MediaKind.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    private companion object {
        const val BUFFER_SIZE = 256 * 1024
    }
}
