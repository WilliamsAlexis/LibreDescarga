package com.libredescarga.core.media

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.libredescarga.core.model.FileOperationResult
import com.libredescarga.core.security.FileNameSanitizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renombrar, mover y borrar archivos multimedia.
 *
 * Android 10+ solo deja modificar libremente los archivos que creó la app; para
 * el resto devuelve [FileOperationResult.NeedsUserConsent] con el diálogo del
 * sistema que la interfaz debe mostrar.
 */
@Singleton
class MediaFileOperations @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val resolver get() = context.contentResolver

    suspend fun rename(uriString: String, newName: String): FileOperationResult = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriString)
        val current = displayName(uri) ?: return@withContext FileOperationResult.Failed
        val extension = current.substringAfterLast('.', missingDelimiterValue = "")
        val base = FileNameSanitizer.sanitize(newName.removeSuffix(".$extension"))
        val fullName = if (extension.isEmpty()) base else "$base.$extension"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            withConsent(uri, write = true) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fullName)
                    put(MediaStore.MediaColumns.TITLE, base)
                }
                resolver.update(uri, values, null, null) > 0
            }
        } else {
            legacyMove(uri) { file -> File(file.parentFile, fullName) }
        }
    }

    suspend fun move(uriString: String, folder: String): FileOperationResult = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriString)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            withConsent(uri, write = true) {
                val values = ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/") }
                resolver.update(uri, values, null, null) > 0
            }
        } else {
            legacyMove(uri) { file ->
                @Suppress("DEPRECATION")
                val target = File(Environment.getExternalStorageDirectory(), folder).apply { mkdirs() }
                File(target, file.name)
            }
        }
    }

    suspend fun delete(uriStrings: List<String>): FileOperationResult = withContext(Dispatchers.IO) {
        val uris = uriStrings.map(Uri::parse)
        if (uris.isEmpty()) return@withContext FileOperationResult.Success

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val failed = uris.filterNot { uri ->
                try {
                    resolver.delete(uri, null, null) > 0
                } catch (_: SecurityException) {
                    false
                }
            }
            return@withContext if (failed.isEmpty()) {
                FileOperationResult.Success
            } else {
                // Un único diálogo del sistema para todos los archivos ajenos a la app.
                FileOperationResult.NeedsUserConsent(MediaStore.createDeleteRequest(resolver, failed).intentSender)
            }
        }

        for (uri in uris) {
            val result = if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                withConsent(uri, write = false) { resolver.delete(uri, null, null) > 0 }
            } else {
                legacyDelete(uri)
            }
            if (result != FileOperationResult.Success) return@withContext result
        }
        FileOperationResult.Success
    }

    private inline fun withConsent(uri: Uri, write: Boolean, block: () -> Boolean): FileOperationResult =
        try {
            if (block()) FileOperationResult.Success else FileOperationResult.Failed
        } catch (error: SecurityException) {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> FileOperationResult.NeedsUserConsent(
                    if (write) {
                        MediaStore.createWriteRequest(resolver, listOf(uri)).intentSender
                    } else {
                        MediaStore.createDeleteRequest(resolver, listOf(uri)).intentSender
                    },
                )
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && error is RecoverableSecurityException ->
                    FileOperationResult.NeedsUserConsent(error.userAction.actionIntent.intentSender)
                else -> FileOperationResult.Failed
            }
        } catch (_: IllegalArgumentException) {
            FileOperationResult.Failed
        }

    // --- Android 7–9: se trabaja directamente con la ruta del archivo -----------------------

    @Suppress("DEPRECATION")
    private fun legacyMove(uri: Uri, destination: (File) -> File): FileOperationResult {
        val path = dataPath(uri) ?: return FileOperationResult.Failed
        val source = File(path)
        val target = destination(source)
        if (target.exists() || !source.renameTo(target)) return FileOperationResult.Failed
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DATA, target.absolutePath)
            put(MediaStore.MediaColumns.DISPLAY_NAME, target.name)
            put(MediaStore.MediaColumns.TITLE, target.nameWithoutExtension)
        }
        return try {
            resolver.update(uri, values, null, null)
            FileOperationResult.Success
        } catch (_: Exception) {
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), null, null)
            FileOperationResult.Success
        }
    }

    private fun legacyDelete(uri: Uri): FileOperationResult {
        dataPath(uri)?.let { File(it).delete() }
        return try {
            resolver.delete(uri, null, null)
            FileOperationResult.Success
        } catch (_: SecurityException) {
            FileOperationResult.Failed
        }
    }

    @Suppress("DEPRECATION")
    private fun dataPath(uri: Uri): String? =
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun displayName(uri: Uri): String? = try {
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (_: SecurityException) {
        null
    }
}
