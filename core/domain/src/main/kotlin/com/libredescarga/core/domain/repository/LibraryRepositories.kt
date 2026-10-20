package com.libredescarga.core.domain.repository

import com.libredescarga.core.model.DuplicateScanState
import com.libredescarga.core.model.FileOperationResult
import com.libredescarga.core.model.LibraryMedia
import com.libredescarga.core.model.PlaylistItem
import com.libredescarga.core.model.UserPlaylist
import kotlinx.coroutines.flow.Flow

/** Videos y audio del teléfono (MediaStore). Se actualiza solo cuando cambian los archivos. */
interface LibraryRepository {
    val videos: Flow<List<LibraryMedia>>
    val audio: Flow<List<LibraryMedia>>

    /** Vuelve a leer MediaStore (por ejemplo, tras conceder el permiso de archivos). */
    fun refresh()

    suspend fun rename(uri: String, newName: String): FileOperationResult

    suspend fun delete(uris: List<String>): FileOperationResult

    /** Mueve a otra carpeta pública, por ejemplo "Music/LibreDescarga". */
    suspend fun move(uri: String, folder: String): FileOperationResult
}

/** Busca archivos repetidos en el teléfono. Solo lee; borrar es decisión del usuario (ver [LibraryRepository.delete]). */
interface DuplicateRepository {
    fun scan(): Flow<DuplicateScanState>
}

interface PlaylistRepository {
    val playlists: Flow<List<UserPlaylist>>

    suspend fun create(name: String): Long

    suspend fun rename(id: Long, name: String)

    suspend fun delete(id: Long)

    suspend fun addItems(id: Long, items: List<PlaylistItem>)

    suspend fun removeItem(id: Long, uri: String)
}

interface FavoritesRepository {
    val favorites: Flow<Set<String>>

    suspend fun toggle(uri: String)
}

/** Posición donde se dejó cada archivo, para reanudar desde ahí. */
interface PlaybackPositionRepository {
    suspend fun get(uri: String): Long?

    suspend fun save(uri: String, positionMs: Long, durationMs: Long)

    suspend fun clear(uri: String)
}
