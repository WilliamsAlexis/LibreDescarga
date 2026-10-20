package com.libredescarga.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Tabla "playlists": una fila por cada lista de reproducción (nombre y fecha de creación). */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

/**
 * Tabla "playlist_items": los elementos de cada lista, en orden.
 * Si se borra la lista, sus elementos se borran solos (CASCADE).
 */
@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "uri"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId")],
)
data class PlaylistItemEntity(
    val playlistId: Long,
    val uri: String,
    val title: String,
    val artist: String?,
    val durationMs: Long,
    val kind: String,
    val position: Int,
)

/** Tabla "favorites": archivos marcados como favoritos. */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val uri: String,
    val addedAt: Long,
)

/** Tabla "playback_positions": minuto donde quedó cada video o audio, para continuar después. */
@Entity(tableName = "playback_positions")
data class PlaybackPositionEntity(
    @PrimaryKey val uri: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)
