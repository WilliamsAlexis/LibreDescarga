package com.libredescarga.core.data.backup

import com.libredescarga.core.model.AccentColor
import com.libredescarga.core.model.AudioFormat
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.model.PlaylistItem
import com.libredescarga.core.model.ThemeMode
import com.libredescarga.core.model.VideoQuality
import com.libredescarga.core.testing.FakeBookmarkRepository
import com.libredescarga.core.testing.FakeBrowserHistoryRepository
import com.libredescarga.core.testing.FakeFavoritesRepository
import com.libredescarga.core.testing.FakePlaylistRepository
import com.libredescarga.core.testing.FakeSearchHistoryRepository
import com.libredescarga.core.testing.FakeSiteRepository
import com.libredescarga.core.testing.FakeUserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupManagerTest {

    /** Un juego completo de repositorios en memoria: simula un teléfono. */
    private class Phone {
        val preferences = FakeUserPreferencesRepository()
        val searches = FakeSearchHistoryRepository()
        val bookmarks = FakeBookmarkRepository()
        val history = FakeBrowserHistoryRepository()
        val playlists = FakePlaylistRepository()
        val favorites = FakeFavoritesRepository()
        val sites = FakeSiteRepository()
        val manager = BackupManager(preferences, searches, bookmarks, history, playlists, favorites, sites)
    }

    private suspend fun Phone.fillWithData() {
        preferences.setThemeMode(ThemeMode.DARK)
        preferences.setAccentColor(AccentColor.CORAL)
        preferences.setDefaultVideoQuality(VideoQuality.P720)
        preferences.setDefaultAudioFormat(AudioFormat.MP3_320)
        preferences.setWifiOnly(true)
        preferences.setUserBlockedDomains(setOf("bloqueado.com"))
        searches.add("gatos")
        searches.add("lofi")
        bookmarks.toggle("https://example.com/a", "Página A")
        history.record("https://example.com/h", "Historial")
        val playlistId = playlists.create("Viaje")
        playlists.addItems(
            playlistId,
            listOf(
                PlaylistItem("content://media/audio/1", "Canción", "Artista", 1000, MediaKind.AUDIO),
                PlaylistItem("vault://secreto-1", "Secreto", null, 1000, MediaKind.AUDIO),
            ),
        )
        favorites.toggle("content://media/audio/1")
        favorites.toggle("vault://secreto-2")
        sites.addCustomSite("Mi sitio", "https://sitio.example.org")
    }

    @Test
    fun `exportar e importar en otro teléfono recupera los datos`() = runTest {
        val original = Phone().also { it.fillWithData() }
        val json = original.manager.export()

        val other = Phone()
        val summary = other.manager.import(json)

        assertTrue(summary.settings)
        val settings = other.preferences.settings.first()
        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertEquals(AccentColor.CORAL, settings.accentColor)
        assertEquals(VideoQuality.P720, settings.defaultVideoQuality)
        assertEquals(AudioFormat.MP3_320, settings.defaultAudioFormat)
        assertTrue(settings.wifiOnly)
        assertEquals(setOf("bloqueado.com"), settings.userBlockedDomains)

        assertEquals(listOf("lofi", "gatos"), other.searches.suggestions("", 10).first()) // Mismo orden.
        assertEquals(listOf("https://example.com/a"), other.bookmarks.bookmarks.first().map { it.url })
        assertEquals(listOf("https://example.com/h"), other.history.history.first().map { it.url })
        assertEquals(listOf("Mi sitio"), other.sites.sites.first().map { it.name })
        assertEquals(1, summary.playlists)
        assertEquals("Viaje", other.playlists.current.single().name)
    }

    @Test
    fun `nada de la carpeta privada sale en la copia`() = runTest {
        val phone = Phone().also { it.fillWithData() }
        val json = phone.manager.export()

        assertFalse(json.contains("vault://"))
        assertFalse(json.contains("secreto"))
        assertFalse(json.contains("Secreto"))
        // Lo demás de la lista de reproducción sí va.
        assertTrue(json.contains("content://media/audio/1"))
    }

    @Test
    fun `restaurar une con lo existente y no borra nada ni duplica`() = runTest {
        val phone = Phone().also { it.fillWithData() }
        phone.bookmarks.toggle("https://example.com/extra", "Extra")
        val json = phone.manager.export()

        phone.manager.import(json)
        phone.manager.import(json)

        assertEquals(2, phone.bookmarks.bookmarks.first().size)
        assertEquals(1, phone.playlists.current.size)
        // El teléfono ya tenía 2 archivos (uno de la carpeta privada): restaurar dos veces no añade ninguno.
        assertEquals(2, phone.playlists.current.single().items.size)
        assertEquals(1, phone.favorites.favorites.first().count { it == "content://media/audio/1" })
    }

    @Test
    fun `los dominios bloqueados se suman a los que ya había`() = runTest {
        val phone = Phone()
        phone.preferences.setUserBlockedDomains(setOf("uno.com"))
        val json = BackupCodec.encode(BackupFile(blockedDomains = listOf("Dos.com", "https://www.tres.org/x", "basura")))

        val summary = phone.manager.import(json)

        assertEquals(setOf("uno.com", "dos.com", "tres.org"), phone.preferences.settings.first().userBlockedDomains)
        assertEquals(2, summary.blockedDomains)
    }

    @Test
    fun `una copia manipulada no puede colar enlaces peligrosos ni valores raros`() = runTest {
        val phone = Phone()
        val json = BackupCodec.encode(
            BackupFile(
                settings = BackupSettings(themeMode = "NO_EXISTE", maxConcurrentDownloads = 999, defaultVideoQuality = "RARO"),
                bookmarks = listOf(
                    BackupBookmark("javascript:alert(1)", "Malo"),
                    BackupBookmark("file:///sdcard/x", "Malo 2"),
                    BackupBookmark("http://example.com/ok", "Bueno"),
                ),
                history = listOf(BackupHistory("content://contactos/1", "Malo")),
                sites = listOf(BackupSite("Malo", "javascript:alert(1)"), BackupSite("Bueno", "https://sitio.example.org")),
                favorites = listOf("vault://x"),
            ),
        )

        phone.manager.import(json)

        val settings = phone.preferences.settings.first()
        assertEquals(ThemeMode.SYSTEM, settings.themeMode) // Valor desconocido: se ignora.
        assertEquals(5, settings.maxConcurrentDownloads) // Se acota al máximo permitido.
        assertEquals(VideoQuality.ASK, settings.defaultVideoQuality)
        assertEquals(listOf("https://example.com/ok"), phone.bookmarks.bookmarks.first().map { it.url })
        assertTrue(phone.history.history.first().isEmpty())
        assertEquals(listOf("Bueno"), phone.sites.sites.first().map { it.name })
        assertTrue(phone.favorites.favorites.first().isEmpty())
    }

    @Test
    fun `un archivo que no es una copia válida se rechaza`() = runTest {
        val phone = Phone()
        listOf(
            "esto no es json",
            "{}",
            """{"app":"OtraApp","format":1}""",
            """{"app":"LibreDescarga","format":99}""",
            "x".repeat(BackupCodec.MAX_CHARS + 1),
        ).forEach { bad ->
            try {
                phone.manager.import(bad)
                fail("Debía rechazar el archivo")
            } catch (_: IllegalArgumentException) {
            }
        }
    }
}
