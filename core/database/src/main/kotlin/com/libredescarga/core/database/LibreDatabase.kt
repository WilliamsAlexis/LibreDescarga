package com.libredescarga.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.libredescarga.core.database.dao.BookmarkDao
import com.libredescarga.core.database.dao.BrowserHistoryDao
import com.libredescarga.core.database.dao.DownloadDao
import com.libredescarga.core.database.dao.FavoriteDao
import com.libredescarga.core.database.dao.PlaybackPositionDao
import com.libredescarga.core.database.dao.PlaylistDao
import com.libredescarga.core.database.dao.SearchHistoryDao
import com.libredescarga.core.database.dao.SiteDao
import com.libredescarga.core.database.dao.VaultItemDao
import com.libredescarga.core.database.entity.BookmarkEntity
import com.libredescarga.core.database.entity.BrowserHistoryEntity
import com.libredescarga.core.database.entity.DownloadEntity
import com.libredescarga.core.database.entity.FavoriteEntity
import com.libredescarga.core.database.entity.PlaybackPositionEntity
import com.libredescarga.core.database.entity.PlaylistEntity
import com.libredescarga.core.database.entity.PlaylistItemEntity
import com.libredescarga.core.database.entity.SearchHistoryEntity
import com.libredescarga.core.database.entity.SiteEntity
import com.libredescarga.core.database.entity.VaultItemEntity

@Database(
    entities = [
        SearchHistoryEntity::class,
        SiteEntity::class,
        DownloadEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        FavoriteEntity::class,
        PlaybackPositionEntity::class,
        BrowserHistoryEntity::class,
        BookmarkEntity::class,
        VaultItemEntity::class,
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        // v2: tamaño estimado de cada descarga (aviso de espacio insuficiente).
        AutoMigration(from = 1, to = 2),
        // v3: listas de reproducción, favoritos y posición de reproducción.
        AutoMigration(from = 2, to = 3),
        // v4: historial y marcadores del navegador integrado.
        AutoMigration(from = 3, to = 4),
        // v5: índice de la carpeta privada.
        AutoMigration(from = 4, to = 5),
    ],
)
abstract class LibreDatabase : RoomDatabase() {
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun siteDao(): SiteDao
    abstract fun downloadDao(): DownloadDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playbackPositionDao(): PlaybackPositionDao
    abstract fun browserHistoryDao(): BrowserHistoryDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun vaultItemDao(): VaultItemDao

    companion object {
        const val NAME = "libredescarga.db"
    }
}
