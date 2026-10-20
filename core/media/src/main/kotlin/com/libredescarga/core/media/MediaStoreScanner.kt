package com.libredescarga.core.media

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.libredescarga.core.model.LibraryMedia
import com.libredescarga.core.model.MediaKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lee los videos y el audio del teléfono desde MediaStore y avisa cuando cambian.
 * Sin el permiso de archivos multimedia, Android 10+ solo devuelve los archivos
 * creados por la propia app (las descargas).
 */
@Singleton
class MediaStoreScanner @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val refreshes = MutableStateFlow(0)

    fun refresh() = refreshes.update { it + 1 }

    @OptIn(FlowPreview::class)
    fun observe(kind: MediaKind): Flow<List<LibraryMedia>> =
        combine(changes(collection(kind)), refreshes) { _, _ -> Unit }
            .debounce(CHANGE_DEBOUNCE_MS)
            .map { query(kind) }
            .flowOn(Dispatchers.IO)

    private fun changes(uri: Uri): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(uri, true, observer)
        trySend(Unit)
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }

    fun query(kind: MediaKind): List<LibraryMedia> {
        val collection = collection(kind)
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.TITLE)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(durationColumn(kind))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                add(MediaStore.MediaColumns.DATA)
            }
            when (kind) {
                MediaKind.VIDEO -> {
                    add(MediaStore.Video.VideoColumns.WIDTH)
                    add(MediaStore.Video.VideoColumns.HEIGHT)
                }
                MediaKind.AUDIO -> {
                    add(MediaStore.Audio.AudioColumns.ARTIST)
                    add(MediaStore.Audio.AudioColumns.ALBUM)
                }
                MediaKind.IMAGE -> error(SIN_IMAGENES)
            }
        }.toTypedArray()

        val selection = if (kind == MediaKind.AUDIO) "${MediaStore.Audio.AudioColumns.IS_MUSIC} != 0" else null
        val items = mutableListOf<LibraryMedia>()
        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    items += cursor.toMedia(kind)
                }
            }
        } catch (_: SecurityException) {
            // Sin permiso en Android 7–9: la biblioteca queda vacía hasta concederlo.
        }
        return items
    }

    private fun Cursor.toMedia(kind: MediaKind): LibraryMedia {
        val id = getLong(getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
        val displayName = string(MediaStore.MediaColumns.DISPLAY_NAME) ?: id.toString()
        val folder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            string(MediaStore.MediaColumns.RELATIVE_PATH)?.trimEnd('/').orEmpty()
        } else {
            @Suppress("DEPRECATION")
            legacyFolder(string(MediaStore.MediaColumns.DATA))
        }
        return LibraryMedia(
            uri = ContentUris.withAppendedId(baseUri(kind), id).toString(),
            kind = kind,
            title = string(MediaStore.MediaColumns.TITLE)?.takeIf { it.isNotBlank() }
                ?: displayName.substringBeforeLast('.'),
            displayName = displayName,
            folder = folder,
            sizeBytes = long(MediaStore.MediaColumns.SIZE) ?: 0,
            durationMs = long(durationColumn(kind)) ?: 0,
            dateAdded = (long(MediaStore.MediaColumns.DATE_ADDED) ?: 0) * 1000,
            mimeType = string(MediaStore.MediaColumns.MIME_TYPE),
            artist = if (kind == MediaKind.AUDIO) {
                string(MediaStore.Audio.AudioColumns.ARTIST)?.takeUnless { it == MediaStore.UNKNOWN_STRING }
            } else {
                null
            },
            album = if (kind == MediaKind.AUDIO) string(MediaStore.Audio.AudioColumns.ALBUM) else null,
            width = if (kind == MediaKind.VIDEO) int(MediaStore.Video.VideoColumns.WIDTH) else null,
            height = if (kind == MediaKind.VIDEO) int(MediaStore.Video.VideoColumns.HEIGHT) else null,
        )
    }

    private fun legacyFolder(path: String?): String {
        val parent = path?.let { File(it).parent } ?: return ""
        @Suppress("DEPRECATION")
        val root = Environment.getExternalStorageDirectory().absolutePath
        return parent.removePrefix(root).trim('/')
    }

    private fun Cursor.string(column: String): String? =
        getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getString)

    private fun Cursor.long(column: String): Long? =
        getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getLong)

    private fun Cursor.int(column: String): Int? =
        getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getInt)

    private fun durationColumn(kind: MediaKind): String = when (kind) {
        MediaKind.VIDEO -> MediaStore.Video.VideoColumns.DURATION
        MediaKind.AUDIO -> MediaStore.Audio.AudioColumns.DURATION
        MediaKind.IMAGE -> error(SIN_IMAGENES)
    }

    /** En Android 10+ se consultan todos los volúmenes (memoria interna y tarjeta SD). */
    private fun collection(kind: MediaKind): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (kind) {
                MediaKind.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                MediaKind.AUDIO -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                MediaKind.IMAGE -> error(SIN_IMAGENES)
            }
        } else {
            baseUri(kind)
        }

    private fun baseUri(kind: MediaKind): Uri = when (kind) {
        MediaKind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        MediaKind.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        MediaKind.IMAGE -> error(SIN_IMAGENES)
    }

    private companion object {
        const val CHANGE_DEBOUNCE_MS = 250L
        const val SIN_IMAGENES = "La biblioteca de la app solo incluye videos y audios"
    }
}
