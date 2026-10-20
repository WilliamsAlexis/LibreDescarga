package com.libredescarga.core.data.repository

import com.libredescarga.core.database.dao.FavoriteDao
import com.libredescarga.core.database.dao.PlaybackPositionDao
import com.libredescarga.core.database.dao.PlaylistDao
import com.libredescarga.core.database.entity.FavoriteEntity
import com.libredescarga.core.database.entity.PlaybackPositionEntity
import com.libredescarga.core.database.entity.PlaylistEntity
import com.libredescarga.core.database.entity.PlaylistItemEntity
import com.libredescarga.core.domain.repository.FavoritesRepository
import com.libredescarga.core.domain.repository.LibraryRepository
import com.libredescarga.core.domain.repository.PlaybackPositionRepository
import com.libredescarga.core.domain.repository.PlaylistRepository
import com.libredescarga.core.domain.usecase.ResumePositionPolicy
import com.libredescarga.core.media.MediaFileOperations
import com.libredescarga.core.media.MediaStoreScanner
import com.libredescarga.core.model.FileOperationResult
import com.libredescarga.core.model.LibraryMedia
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.model.PlaylistItem
import com.libredescarga.core.model.UserPlaylist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Biblioteca de videos y audios: lee los archivos del teléfono y permite renombrar, borrar y mover. */
internal class MediaStoreLibraryRepository @Inject constructor(
    private val scanner: MediaStoreScanner,
    private val operations: MediaFileOperations,
) : LibraryRepository {

    override val videos: Flow<List<LibraryMedia>> = scanner.observe(MediaKind.VIDEO)

    override val audio: Flow<List<LibraryMedia>> = scanner.observe(MediaKind.AUDIO)

    // Vuelve a leer los archivos del teléfono.
    override fun refresh() = scanner.refresh()

    // Cambia el nombre de un archivo y actualiza la lista si salió bien.
    override suspend fun rename(uri: String, newName: String): FileOperationResult =
        operations.rename(uri, newName).also { if (it == FileOperationResult.Success) scanner.refresh() }

    // Borra archivos y actualiza la lista si salió bien.
    override suspend fun delete(uris: List<String>): FileOperationResult =
        operations.delete(uris).also { if (it == FileOperationResult.Success) scanner.refresh() }

    // Mueve un archivo a otra carpeta y actualiza la lista si salió bien.
    override suspend fun move(uri: String, folder: String): FileOperationResult =
        operations.move(uri, folder).also { if (it == FileOperationResult.Success) scanner.refresh() }
}

/** Listas de reproducción creadas por el usuario; se guardan solo en el teléfono. */
internal class OfflinePlaylistRepository @Inject constructor(
    private val dao: PlaylistDao,
) : PlaylistRepository {

    override val playlists: Flow<List<UserPlaylist>> =
        // Junta las listas con sus canciones para entregar cada lista completa.
        combine(dao.observePlaylists(), dao.observeItems()) { playlists, items ->
            val byPlaylist = items.groupBy { it.playlistId }
            playlists.map { playlist ->
                UserPlaylist(
                    id = playlist.id,
                    name = playlist.name,
                    items = byPlaylist[playlist.id].orEmpty().map { item ->
                        PlaylistItem(
                            uri = item.uri,
                            title = item.title,
                            artist = item.artist,
                            durationMs = item.durationMs,
                            kind = MediaKind.entries.firstOrNull { it.name == item.kind } ?: MediaKind.AUDIO,
                        )
                    },
                )
            }
        }

    // Crea una lista nueva y devuelve su número.
    override suspend fun create(name: String): Long =
        dao.insert(PlaylistEntity(name = name.trim(), createdAt = System.currentTimeMillis()))

    override suspend fun rename(id: Long, name: String) = dao.rename(id, name.trim())

    override suspend fun delete(id: Long) = dao.delete(id)

    // Agrega canciones o videos al final de la lista.
    override suspend fun addItems(id: Long, items: List<PlaylistItem>) = dao.append(
        playlistId = id,
        items = items.map { item ->
            PlaylistItemEntity(
                playlistId = id,
                uri = item.uri,
                title = item.title,
                artist = item.artist,
                durationMs = item.durationMs,
                kind = item.kind.name,
                position = 0,
            )
        },
    )

    // Quita un elemento de la lista.
    override suspend fun removeItem(id: Long, uri: String) = dao.removeItem(id, uri)
}

/** Favoritos: guarda qué archivos marcó el usuario con el corazón. */
internal class OfflineFavoritesRepository @Inject constructor(
    private val dao: FavoriteDao,
) : FavoritesRepository {

    override val favorites: Flow<Set<String>> = dao.observeUris().map { it.toSet() }

    // Si ya era favorito lo quita; si no, lo agrega.
    override suspend fun toggle(uri: String) {
        if (dao.isFavorite(uri)) {
            dao.delete(uri)
        } else {
            dao.upsert(FavoriteEntity(uri = uri, addedAt = System.currentTimeMillis()))
        }
    }
}

/** Recuerda en qué minuto se quedó cada video o audio para continuar donde se dejó. */
internal class OfflinePlaybackPositionRepository @Inject constructor(
    private val dao: PlaybackPositionDao,
) : PlaybackPositionRepository {

    override suspend fun get(uri: String): Long? = dao.get(uri)?.positionMs

    // Guarda la posición; si ya casi terminó o apenas empezó, mejor la olvida.
    override suspend fun save(uri: String, positionMs: Long, durationMs: Long) {
        if (ResumePositionPolicy.shouldForget(positionMs, durationMs)) {
            dao.delete(uri)
        } else {
            dao.upsert(PlaybackPositionEntity(uri, positionMs, durationMs, System.currentTimeMillis()))
        }
    }

    override suspend fun clear(uri: String) = dao.delete(uri)
}
