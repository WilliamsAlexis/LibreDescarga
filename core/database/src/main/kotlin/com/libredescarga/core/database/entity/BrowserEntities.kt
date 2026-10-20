package com.libredescarga.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Historial del navegador (no se guarda nada de las pestañas de incógnito). */
@Entity(tableName = "browser_history", indices = [Index("visitedAt")])
data class BrowserHistoryEntity(
    @PrimaryKey val url: String,
    val title: String,
    val visitedAt: Long,
)

@Entity(tableName = "bookmarks", indices = [Index("createdAt")])
data class BookmarkEntity(
    @PrimaryKey val url: String,
    val title: String,
    val createdAt: Long,
)

/** Índice de los archivos de la carpeta privada. El contenido va aparte, en el almacenamiento interno de la app. */
@Entity(tableName = "vault_items", indices = [Index("addedAt")])
data class VaultItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val durationMs: Long,
    val encrypted: Boolean,
    val addedAt: Long,
)
