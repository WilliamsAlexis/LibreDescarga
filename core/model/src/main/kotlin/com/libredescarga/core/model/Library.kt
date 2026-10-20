package com.libredescarga.core.model

/** Un video o audio del teléfono (descargado con la app o encontrado en MediaStore). */
data class LibraryMedia(
    val uri: String,
    val kind: MediaKind,
    val title: String,
    val displayName: String,
    /** Carpeta visible, por ejemplo "Movies/LibreDescarga". */
    val folder: String,
    val sizeBytes: Long,
    val durationMs: Long,
    /** Fecha en milisegundos. */
    val dateAdded: Long,
    val mimeType: String?,
    val artist: String? = null,
    val album: String? = null,
    val width: Int? = null,
    val height: Int? = null,
) {
    val extension: String get() = displayName.substringAfterLast('.', missingDelimiterValue = "")
}

enum class LibrarySort { DATE, NAME, SIZE, DURATION }

/** Carpeta con los archivos que contiene. */
data class MediaFolder(val path: String, val name: String, val items: List<LibraryMedia>)

data class UserPlaylist(
    val id: Long,
    val name: String,
    val items: List<PlaylistItem>,
)

/** Copia de los datos del archivo al añadirlo, para mostrar la lista aunque el archivo cambie. */
data class PlaylistItem(
    val uri: String,
    val title: String,
    val artist: String?,
    val durationMs: Long,
    val kind: MediaKind,
)

/**
 * Resultado de renombrar, mover o borrar. En Android 10+ los archivos que no creó
 * la app necesitan que el usuario lo confirme en un diálogo del sistema.
 */
sealed interface FileOperationResult {
    data object Success : FileOperationResult
    data object Failed : FileOperationResult

    /** [request] es un IntentSender de Android; la interfaz lo lanza y repite la operación. */
    data class NeedsUserConsent(val request: Any) : FileOperationResult
}
