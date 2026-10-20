package com.libredescarga.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.libredescarga.core.database.entity.FavoriteEntity
import com.libredescarga.core.database.entity.PlaybackPositionEntity
import com.libredescarga.core.database.entity.PlaylistEntity
import com.libredescarga.core.database.entity.PlaylistItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlist_items ORDER BY position ASC")
    fun observeItems(): Flow<List<PlaylistItemEntity>>

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItems(items: List<PlaylistItemEntity>)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND uri = :uri")
    suspend fun removeItem(playlistId: Long, uri: String)

    /** Añade al final, sin repetir archivos que ya estén en la lista. */
    @Transaction
    suspend fun append(playlistId: Long, items: List<PlaylistItemEntity>) {
        val start = maxPosition(playlistId) + 1
        insertItems(items.mapIndexed { index, item -> item.copy(playlistId = playlistId, position = start + index) })
    }
}

@Dao
interface FavoriteDao {

    @Query("SELECT uri FROM favorites ORDER BY addedAt DESC")
    fun observeUris(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE uri = :uri)")
    suspend fun isFavorite(uri: String): Boolean

    @Upsert
    suspend fun upsert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE uri = :uri")
    suspend fun delete(uri: String)
}

@Dao
interface PlaybackPositionDao {

    @Query("SELECT * FROM playback_positions WHERE uri = :uri")
    suspend fun get(uri: String): PlaybackPositionEntity?

    @Upsert
    suspend fun upsert(position: PlaybackPositionEntity)

    @Query("DELETE FROM playback_positions WHERE uri = :uri")
    suspend fun delete(uri: String)
}
