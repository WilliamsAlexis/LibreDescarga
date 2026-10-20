package com.libredescarga.core.data.di

import com.libredescarga.core.data.backup.BackupManager
import com.libredescarga.core.data.repository.AssetBlocklistRepository
import com.libredescarga.core.data.repository.DeviceCacheRepository
import com.libredescarga.core.data.repository.YtDlpEngineRepository
import com.libredescarga.core.data.repository.DeviceStorageRepository
import com.libredescarga.core.data.repository.InMemoryIncomingLinkRepository
import com.libredescarga.core.data.repository.MediaStoreLibraryRepository
import com.libredescarga.core.data.repository.OfflineBookmarkRepository
import com.libredescarga.core.data.repository.OfflineBrowserHistoryRepository
import com.libredescarga.core.data.repository.OfflineFavoritesRepository
import com.libredescarga.core.data.repository.OfflinePlaybackPositionRepository
import com.libredescarga.core.data.repository.OfflinePlaylistRepository
import com.libredescarga.core.domain.repository.BookmarkRepository
import com.libredescarga.core.domain.settings.BackupRepository
import com.libredescarga.core.domain.settings.CacheRepository
import com.libredescarga.core.domain.settings.EngineRepository
import com.libredescarga.core.domain.repository.BrowserHistoryRepository
import com.libredescarga.core.domain.repository.PageImageResolver
import com.libredescarga.core.domain.repository.RemoteFileSizeProbe
import com.libredescarga.core.downloader.ImageFetcher
import com.libredescarga.core.domain.repository.FavoritesRepository
import com.libredescarga.core.domain.repository.LibraryRepository
import com.libredescarga.core.domain.repository.PlaybackPositionRepository
import com.libredescarga.core.domain.repository.PlaylistRepository
import com.libredescarga.core.data.repository.OfflineSearchHistoryRepository
import com.libredescarga.core.data.repository.OfflineSiteRepository
import com.libredescarga.core.data.repository.OfflineUserPreferencesRepository
import com.libredescarga.core.data.repository.WorkDownloadRepository
import com.libredescarga.core.data.repository.YtDlpMediaExtractorRepository
import com.libredescarga.core.domain.repository.BlocklistRepository
import com.libredescarga.core.domain.repository.DownloadRepository
import com.libredescarga.core.domain.repository.IncomingLinkRepository
import com.libredescarga.core.domain.repository.MediaExtractorRepository
import com.libredescarga.core.domain.repository.SearchHistoryRepository
import com.libredescarga.core.domain.repository.SiteRepository
import com.libredescarga.core.domain.repository.StorageRepository
import com.libredescarga.core.domain.repository.UserPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Indica a Hilt (el sistema que entrega las piezas de la app) qué clase real usar para cada contrato.
 * Por ejemplo: cuando alguien pide "UserPreferencesRepository", se le da "OfflineUserPreferencesRepository".
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {

    // Preferencias del usuario (tema, calidad, etc.).
    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(
        impl: OfflineUserPreferencesRepository,
    ): UserPreferencesRepository

    // Analiza un enlace y obtiene sus videos y audios disponibles.
    @Binds
    abstract fun bindMediaExtractorRepository(
        impl: YtDlpMediaExtractorRepository,
    ): MediaExtractorRepository

    // Historial de búsquedas.
    @Binds
    abstract fun bindSearchHistoryRepository(
        impl: OfflineSearchHistoryRepository,
    ): SearchHistoryRepository

    // Sitios favoritos de la pantalla de inicio.
    @Binds
    @Singleton
    abstract fun bindSiteRepository(impl: OfflineSiteRepository): SiteRepository

    // Lista de sitios bloqueados.
    @Binds
    abstract fun bindBlocklistRepository(impl: AssetBlocklistRepository): BlocklistRepository

    // Cola y estado de las descargas.
    @Binds
    abstract fun bindDownloadRepository(impl: WorkDownloadRepository): DownloadRepository

    // Carpeta de descargas y espacio disponible.
    @Binds
    abstract fun bindStorageRepository(impl: DeviceStorageRepository): StorageRepository

    // Copia de seguridad y restauración.
    @Binds
    abstract fun bindBackupRepository(impl: BackupManager): BackupRepository

    // Limpieza de la memoria temporal.
    @Binds
    abstract fun bindCacheRepository(impl: DeviceCacheRepository): CacheRepository

    // Motor de descarga (yt-dlp) y su actualización.
    @Binds
    abstract fun bindEngineRepository(impl: YtDlpEngineRepository): EngineRepository

    // Biblioteca de videos y audios del teléfono.
    @Binds
    @Singleton
    abstract fun bindLibraryRepository(impl: MediaStoreLibraryRepository): LibraryRepository

    // Listas de reproducción.
    @Binds
    abstract fun bindPlaylistRepository(impl: OfflinePlaylistRepository): PlaylistRepository

    // Favoritos.
    @Binds
    abstract fun bindFavoritesRepository(impl: OfflineFavoritesRepository): FavoritesRepository

    // Historial del navegador.
    @Binds
    abstract fun bindBrowserHistoryRepository(impl: OfflineBrowserHistoryRepository): BrowserHistoryRepository

    // Marcadores del navegador.
    @Binds
    abstract fun bindBookmarkRepository(impl: OfflineBookmarkRepository): BookmarkRepository

    // Posición donde quedó cada reproducción.
    @Binds
    abstract fun bindPlaybackPositionRepository(
        impl: OfflinePlaybackPositionRepository,
    ): PlaybackPositionRepository

    // Tamaño de los archivos que se detectan en una página.
    @Binds
    abstract fun bindRemoteFileSizeProbe(impl: ImageFetcher): RemoteFileSizeProbe

    // Imagen principal de una página web (por ejemplo, un pin de Pinterest).
    @Binds
    abstract fun bindPageImageResolver(impl: ImageFetcher): PageImageResolver

    // Enlaces recibidos desde otras apps (Compartir).
    @Binds
    abstract fun bindIncomingLinkRepository(
        impl: InMemoryIncomingLinkRepository,
    ): IncomingLinkRepository
}
