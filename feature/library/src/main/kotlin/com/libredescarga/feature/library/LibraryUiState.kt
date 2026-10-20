package com.libredescarga.feature.library

import com.libredescarga.core.config.AppConfig
import com.libredescarga.core.model.LibraryMedia
import com.libredescarga.core.model.LibrarySort
import com.libredescarga.core.model.MediaFolder
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.model.UserPlaylist

enum class LibraryTab { VIDEOS, MUSIC, FOLDERS, PLAYLISTS }

/** Colección abierta dentro de una pestaña (carpeta, favoritos o una lista propia). */
sealed interface OpenCollection {
    data class Folder(val path: String) : OpenCollection
    data object Favorites : OpenCollection
    data class Playlist(val id: Long) : OpenCollection
}

data class LibraryUiState(
    val loaded: Boolean = false,
    val tab: LibraryTab = LibraryTab.VIDEOS,
    val query: String = "",
    val searchActive: Boolean = false,
    val sort: LibrarySort = LibrarySort.DATE,
    val gridView: Boolean = false,
    val videos: List<LibraryMedia> = emptyList(),
    val music: List<LibraryMedia> = emptyList(),
    val folders: List<MediaFolder> = emptyList(),
    val playlists: List<UserPlaylist> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val open: OpenCollection? = null,
    /** Título de la colección abierta (nombre de la carpeta o de la lista). */
    val openTitle: String? = null,
    val openItems: List<LibraryMedia> = emptyList(),
    val message: LibraryMessage? = null,
    val pendingConsent: PendingConsent? = null,
)

/** Operación que necesita confirmación del usuario en un diálogo del sistema (Android 10+). */
sealed interface FileOperation {
    data class Rename(val media: LibraryMedia, val newName: String) : FileOperation
    data class Move(val media: LibraryMedia, val folder: String) : FileOperation
    data class Delete(val media: List<LibraryMedia>) : FileOperation
}

/** [movedToVault]: el borrado es el último paso de "mover a la carpeta privada" (el archivo ya está copiado). */
data class PendingConsent(val request: Any, val operation: FileOperation, val movedToVault: Boolean = false)

sealed interface LibraryMessage {
    data object Renamed : LibraryMessage
    data object Moved : LibraryMessage
    data object Deleted : LibraryMessage
    data object OperationFailed : LibraryMessage
    data object ConversionQueued : LibraryMessage
    data class AddedToPlaylist(val playlistName: String) : LibraryMessage
    data object MovedToVault : LibraryMessage
    data object VaultLocked : LibraryMessage
    data object VaultImportFailed : LibraryMessage
    data object VaultOriginalKept : LibraryMessage
}

sealed interface LibraryEvent {
    data class OpenVideo(val media: LibraryMedia) : LibraryEvent
}

/** Carpetas públicas a las que se puede mover cada tipo de archivo (reglas de MediaStore). */
fun moveTargets(kind: MediaKind): List<String> = when (kind) {
    MediaKind.VIDEO -> listOf("Movies/${AppConfig.DOWNLOAD_FOLDER_NAME}", "Movies", "DCIM")
    MediaKind.AUDIO -> listOf("Music/${AppConfig.DOWNLOAD_FOLDER_NAME}", "Music", "Podcasts")
    MediaKind.IMAGE -> listOf("Pictures/${AppConfig.DOWNLOAD_FOLDER_NAME}", "Pictures", "DCIM")
}
