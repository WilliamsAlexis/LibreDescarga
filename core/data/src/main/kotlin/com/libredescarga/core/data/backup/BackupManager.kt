package com.libredescarga.core.data.backup

import com.libredescarga.core.domain.repository.BookmarkRepository
import com.libredescarga.core.domain.repository.BrowserHistoryRepository
import com.libredescarga.core.domain.repository.FavoritesRepository
import com.libredescarga.core.domain.repository.PlaylistRepository
import com.libredescarga.core.domain.repository.SearchHistoryRepository
import com.libredescarga.core.domain.repository.SiteRepository
import com.libredescarga.core.domain.repository.UserPreferencesRepository
import com.libredescarga.core.domain.settings.BackupRepository
import com.libredescarga.core.domain.settings.BackupSummary
import com.libredescarga.core.model.AccentColor
import com.libredescarga.core.model.AudioFormat
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.model.PlaylistItem
import com.libredescarga.core.model.ThemeMode
import com.libredescarga.core.model.VaultItem
import com.libredescarga.core.model.VideoQuality
import com.libredescarga.core.security.DomainRules
import com.libredescarga.core.security.UrlValidator
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Exporta e importa la copia de seguridad. Al restaurar se une con lo que ya hay: nunca se borra nada. */
internal class BackupManager @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val searches: SearchHistoryRepository,
    private val bookmarks: BookmarkRepository,
    private val history: BrowserHistoryRepository,
    private val playlists: PlaylistRepository,
    private val favorites: FavoritesRepository,
    private val sites: SiteRepository,
) : BackupRepository {

    override suspend fun export(): String {
        val settings = preferences.settings.first()
        val file = BackupFile(
            createdAt = System.currentTimeMillis(),
            settings = BackupSettings(
                themeMode = settings.themeMode.name,
                accentColor = settings.accentColor.name,
                useDynamicColor = settings.useDynamicColor,
                clipboardDetection = settings.clipboardDetection,
                wifiOnly = settings.wifiOnly,
                maxConcurrentDownloads = settings.maxConcurrentDownloads,
                concurrentFragments = settings.concurrentFragments,
                speedLimitKbps = settings.speedLimitKbps,
                defaultVideoQuality = settings.defaultVideoQuality.name,
                defaultAudioFormat = settings.defaultAudioFormat.name,
                hideDonationReminders = settings.hideDonationReminders,
            ),
            searches = searches.suggestions("", MAX_SEARCHES).first(),
            bookmarks = bookmarks.bookmarks.first().map { BackupBookmark(it.url, it.title) },
            history = history.history.first().map { BackupHistory(it.url, it.title) },
            // Lo que está en la carpeta privada no sale en la copia, ni siquiera su nombre.
            playlists = playlists.playlists.first().map { playlist ->
                BackupPlaylist(
                    name = playlist.name,
                    items = playlist.items.filterNot { VaultItem.isVaultUri(it.uri) }.map {
                        BackupPlaylistItem(it.uri, it.title, it.artist, it.durationMs, it.kind.name)
                    },
                )
            },
            favorites = favorites.favorites.first().filterNot(VaultItem::isVaultUri),
            sites = sites.sites.first().filter { it.isCustom }.map { BackupSite(it.name, it.url) },
            blockedDomains = settings.userBlockedDomains.sorted(),
        )
        return BackupCodec.encode(file)
    }

    override suspend fun import(json: String): BackupSummary {
        val file = BackupCodec.decode(json)

        val settingsApplied = file.settings?.let { applySettings(it) } ?: false

        val searchList = file.searches.map(String::trim).filter { it.isNotEmpty() && it.length <= MAX_TEXT }.take(MAX_SEARCHES)
        // Se añaden de la más antigua a la más reciente, para conservar el orden.
        searchList.asReversed().forEach { searches.add(it) }

        val existingBookmarks = bookmarks.bookmarks.first().map { it.url }.toSet()
        val newBookmarks = file.bookmarks.mapNotNull { item -> httpsUrl(item.url)?.let { it to item.title } }
            .filter { (url, _) -> url !in existingBookmarks }.distinctBy { it.first }.take(MAX_ITEMS)
        newBookmarks.forEach { (url, title) -> bookmarks.toggle(url, title.take(MAX_TEXT)) }

        val historyList = file.history.mapNotNull { item -> httpsUrl(item.url)?.let { it to item.title } }.take(MAX_ITEMS)
        historyList.asReversed().forEach { (url, title) -> history.record(url, title.take(MAX_TEXT)) }

        val playlistCount = restorePlaylists(file.playlists)

        val existingFavorites = favorites.favorites.first()
        val newFavorites = file.favorites.filter { it.isNotBlank() && !VaultItem.isVaultUri(it) && it !in existingFavorites }
            .distinct().take(MAX_ITEMS)
        newFavorites.forEach { favorites.toggle(it) }

        var siteCount = 0
        file.sites.take(MAX_ITEMS).forEach { site ->
            val url = httpsUrl(site.url) ?: return@forEach
            runCatching { sites.addCustomSite(site.name.take(MAX_TEXT), url) }.onSuccess { siteCount++ }
        }

        val current = preferences.settings.first().userBlockedDomains
        val domains = file.blockedDomains.mapNotNull(DomainRules::normalize).toSet()
        if (domains.isNotEmpty()) preferences.setUserBlockedDomains(current + domains)

        return BackupSummary(
            settings = settingsApplied,
            searches = searchList.size,
            bookmarks = newBookmarks.size,
            history = historyList.size,
            playlists = playlistCount,
            favorites = newFavorites.size,
            sites = siteCount,
            blockedDomains = domains.size,
        )
    }

    private suspend fun applySettings(settings: BackupSettings): Boolean {
        var applied = false
        settings.themeMode?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }?.let { preferences.setThemeMode(it); applied = true }
        settings.accentColor?.let { name -> AccentColor.entries.firstOrNull { it.name == name } }?.let { preferences.setAccentColor(it); applied = true }
        settings.useDynamicColor?.let { preferences.setDynamicColor(it); applied = true }
        settings.clipboardDetection?.let { preferences.setClipboardDetection(it); applied = true }
        settings.wifiOnly?.let { preferences.setWifiOnly(it); applied = true }
        settings.maxConcurrentDownloads?.let { preferences.setMaxConcurrentDownloads(it); applied = true }
        settings.concurrentFragments?.let { preferences.setConcurrentFragments(it); applied = true }
        settings.speedLimitKbps?.let { preferences.setSpeedLimitKbps(it); applied = true }
        settings.defaultVideoQuality?.let { name -> VideoQuality.entries.firstOrNull { it.name == name } }
            ?.let { preferences.setDefaultVideoQuality(it); applied = true }
        settings.defaultAudioFormat?.let { name -> AudioFormat.entries.firstOrNull { it.name == name } }
            ?.let { preferences.setDefaultAudioFormat(it); applied = true }
        settings.hideDonationReminders?.let { preferences.setHideDonationReminders(it); applied = true }
        return applied
    }

    private suspend fun restorePlaylists(backup: List<BackupPlaylist>): Int {
        var count = 0
        backup.take(MAX_PLAYLISTS).forEach { item ->
            val name = item.name.trim().take(MAX_TEXT)
            if (name.isEmpty()) return@forEach
            // Si ya existe una lista con ese nombre, se añaden solo los archivos que le faltan.
            val id = playlists.playlists.first().firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
                ?: playlists.create(name)
            val entries = item.items.filter { it.uri.isNotBlank() && !VaultItem.isVaultUri(it.uri) }.take(MAX_ITEMS).map {
                PlaylistItem(
                    uri = it.uri,
                    title = it.title.take(MAX_TEXT),
                    artist = it.artist?.take(MAX_TEXT),
                    durationMs = it.durationMs.coerceAtLeast(0),
                    kind = MediaKind.entries.firstOrNull { kind -> kind.name == it.kind } ?: MediaKind.AUDIO,
                )
            }
            if (entries.isNotEmpty()) playlists.addItems(id, entries)
            count++
        }
        return count
    }

    /** Solo se restauran direcciones https: nada de file://, content:// ni javascript:. */
    private fun httpsUrl(url: String): String? {
        val valid = UrlValidator.validate(url) as? UrlValidator.Result.Valid ?: return null
        return valid.url
    }

    private companion object {
        const val MAX_SEARCHES = 100
        const val MAX_ITEMS = 2_000
        const val MAX_PLAYLISTS = 200
        const val MAX_TEXT = 200
    }
}

