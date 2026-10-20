package com.libredescarga.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val lastUsedAt: Long,
)

@Entity(tableName = "sites", indices = [Index(value = ["url"], unique = true)])
data class SiteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    /** Nombre de [com.libredescarga.core.model.SearchProvider], o null si el sitio no tiene búsqueda. */
    val searchProvider: String?,
    val isCustom: Boolean,
    val enabled: Boolean,
    val position: Int,
)

@Entity(tableName = "downloads", indices = [Index("status"), Index("createdAt")])
data class DownloadEntity(
    @PrimaryKey val id: String,
    // Origen
    val url: String,
    val title: String,
    val uploader: String?,
    val thumbnailUrl: String?,
    val durationSeconds: Long?,
    // Opciones elegidas
    val kind: String,
    val videoFormatId: String?,
    val videoMaxHeight: Int?,
    val videoContainer: String?,
    val videoFormatHasAudio: Boolean,
    val audioFormat: String?,
    val subtitleLanguage: String?,
    val subtitleAutomatic: Boolean,
    val subtitleEmbed: Boolean,
    val trimStartSeconds: Long?,
    val trimEndSeconds: Long?,
    val fileName: String,
    /** Añadido en la versión 2 de la base de datos. */
    @ColumnInfo(defaultValue = "NULL")
    val estimatedBytes: Long? = null,
    // Estado
    val status: String,
    val progressPercent: Float,
    val speedBytesPerSecond: Long?,
    val etaSeconds: Long?,
    val totalBytes: Long?,
    val downloadedBytes: Long?,
    val outputUri: String?,
    val errorMessage: String?,
    val priority: Int,
    val createdAt: Long,
    val completedAt: Long?,
)
