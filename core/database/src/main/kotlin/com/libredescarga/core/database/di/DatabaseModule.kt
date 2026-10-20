package com.libredescarga.core.database.di

import android.content.Context
import androidx.room.Room
import com.libredescarga.core.database.LibreDatabase
import com.libredescarga.core.database.dao.BookmarkDao
import com.libredescarga.core.database.dao.BrowserHistoryDao
import com.libredescarga.core.database.dao.DownloadDao
import com.libredescarga.core.database.dao.FavoriteDao
import com.libredescarga.core.database.dao.PlaybackPositionDao
import com.libredescarga.core.database.dao.PlaylistDao
import com.libredescarga.core.database.dao.SearchHistoryDao
import com.libredescarga.core.database.dao.SiteDao
import com.libredescarga.core.database.dao.VaultItemDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Crea la base de datos local de la app (una sola para toda la app) y entrega cada una de sus tablas de acceso. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // La base de datos principal; se crea una sola vez y se guarda en el teléfono.
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LibreDatabase =
        Room.databaseBuilder(context, LibreDatabase::class.java, LibreDatabase.NAME).build()

    // Acceso al historial de búsquedas.
    @Provides
    fun provideSearchHistoryDao(database: LibreDatabase): SearchHistoryDao = database.searchHistoryDao()

    // Acceso a los sitios de la pantalla de inicio.
    @Provides
    fun provideSiteDao(database: LibreDatabase): SiteDao = database.siteDao()

    // Acceso a las descargas.
    @Provides
    fun provideDownloadDao(database: LibreDatabase): DownloadDao = database.downloadDao()

    // Acceso a las listas de reproducción.
    @Provides
    fun providePlaylistDao(database: LibreDatabase): PlaylistDao = database.playlistDao()

    // Acceso a los favoritos.
    @Provides
    fun provideFavoriteDao(database: LibreDatabase): FavoriteDao = database.favoriteDao()

    // Acceso a las posiciones guardadas de reproducción.
    @Provides
    fun providePlaybackPositionDao(database: LibreDatabase): PlaybackPositionDao = database.playbackPositionDao()

    // Acceso al historial del navegador.
    @Provides
    fun provideBrowserHistoryDao(database: LibreDatabase): BrowserHistoryDao = database.browserHistoryDao()

    // Acceso a los marcadores del navegador.
    @Provides
    fun provideBookmarkDao(database: LibreDatabase): BookmarkDao = database.bookmarkDao()

    // Acceso a la lista de elementos de la bóveda.
    @Provides
    fun provideVaultItemDao(database: LibreDatabase): VaultItemDao = database.vaultItemDao()
}
