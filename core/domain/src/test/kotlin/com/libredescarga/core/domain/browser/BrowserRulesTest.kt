package com.libredescarga.core.domain.browser

import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.model.SearchProvider
import com.libredescarga.core.model.webSearchUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserRulesTest {

    private val blocked = setOf("bloqueado.com")

    @Test
    fun `http se carga como https`() {
        val decision = NavigationPolicy.decide("http://example.com/a", blocked) as NavigationDecision.Allow
        assertEquals("https://example.com/a", decision.url)
        assertTrue(decision.upgraded)
    }

    @Test
    fun `https se permite tal cual`() {
        val decision = NavigationPolicy.decide("https://example.com/a", blocked) as NavigationDecision.Allow
        assertFalse(decision.upgraded)
    }

    @Test
    fun `esquemas peligrosos se bloquean`() {
        listOf(
            "file:///sdcard/a.html", "content://x/y", "javascript:alert(1)", "intent://scan#Intent;end",
            "data:text/html,hola", "market://details?id=x", "tel:123",
        ).forEach { assertEquals(it, NavigationDecision.BlockScheme, NavigationPolicy.decide(it, blocked)) }
    }

    @Test
    fun `los dominios de la lista negra y sus subdominios se bloquean`() {
        assertEquals(
            NavigationDecision.BlockDomain("bloqueado.com"),
            NavigationPolicy.decide("https://m.bloqueado.com/video", blocked),
        )
    }

    @Test
    fun `la barra de direcciones distingue enlaces y búsquedas`() {
        assertEquals("https://example.com/a", Omnibox.resolve("http://example.com/a"))
        assertEquals("https://example.com", Omnibox.resolve("example.com"))
        assertEquals("https://duckduckgo.com/?q=gatos+graciosos", Omnibox.resolve("  gatos graciosos "))
        assertEquals("https://duckduckgo.com/?q=%C3%B1and%C3%BA+%26+m%C3%A1s", Omnibox.resolve("ñandú & más"))
        assertNull(Omnibox.resolve("javascript:alert(1)"))
        assertNull(Omnibox.resolve("   "))
    }

    @Test
    fun `reconoce archivos multimedia y descarta fragmentos`() {
        assertTrue(MediaSniffer.isMediaUrl("https://cdn.example.com/v/master.m3u8?token=1"))
        assertTrue(MediaSniffer.isMediaUrl("https://cdn.example.com/a/CANCION.MP3"))
        assertTrue(MediaSniffer.isMediaUrl("https://cdn.example.com/manifest.mpd#t=3"))
        assertFalse(MediaSniffer.isMediaUrl("https://cdn.example.com/seg-1.ts"))
        assertFalse(MediaSniffer.isMediaUrl("https://cdn.example.com/chunk.m4s"))
        assertFalse(MediaSniffer.isMediaUrl("https://example.com/pagina.html"))
        assertFalse(MediaSniffer.isMediaUrl("blob:https://example.com/123"))
    }

    @Test
    fun `reconoce el tipo de archivo por su extension`() {
        assertEquals(MediaKind.VIDEO, MediaSniffer.kindOfUrl("https://cdn.example.com/a.MP4?x=1"))
        assertEquals(MediaKind.VIDEO, MediaSniffer.kindOfUrl("https://cdn.example.com/a.webm"))
        assertEquals(MediaKind.AUDIO, MediaSniffer.kindOfUrl("https://cdn.example.com/a.m4a"))
        assertEquals(MediaKind.AUDIO, MediaSniffer.kindOfUrl("https://cdn.example.com/a.mp3#t=3"))
        assertEquals(MediaKind.IMAGE, MediaSniffer.kindOfUrl("https://cdn.example.com/a.jpg"))
        assertEquals(MediaKind.IMAGE, MediaSniffer.kindOfUrl("https://cdn.example.com/a.PNG"))
        assertEquals(MediaKind.IMAGE, MediaSniffer.kindOfUrl("http://cdn.example.com/a.webp"))
        assertNull(MediaSniffer.kindOfUrl("https://cdn.example.com/a.html"))
        assertNull(MediaSniffer.kindOfUrl("https://cdn.example.com/a"))
        assertNull(MediaSniffer.kindOfUrl("data:image/png;base64,AAAA"))
        assertNull(MediaSniffer.kindOfUrl("javascript:alert(1).jpg"))
    }

    @Test
    fun `añadir no repite ni pasa del máximo`() {
        var found = emptyList<DetectedMedia>()
        found = MediaSniffer.add(found, "https://cdn.example.com/a.mp4")
        found = MediaSniffer.add(found, "https://cdn.example.com/a.mp4")
        found = MediaSniffer.add(found, "https://cdn.example.com/no-media.html")
        assertEquals(listOf(DetectedMedia("https://cdn.example.com/a.mp4", MediaKind.VIDEO)), found)

        repeat(20) { found = MediaSniffer.add(found, "https://cdn.example.com/v$it.mp4") }
        assertEquals(10, found.size)

        // Las imágenes tienen su propio límite, para que un álbum no tape los videos.
        repeat(60) { found = MediaSniffer.add(found, "https://cdn.example.com/i$it.jpg") }
        assertEquals(10, found.count { it.kind == MediaKind.VIDEO })
        assertEquals(40, found.count { it.kind == MediaKind.IMAGE })
    }

    @Test
    fun `una direccion sin extension se acepta si la pagina indica su tipo`() {
        val found = MediaSniffer.add(emptyList(), "https://cdn.example.com/foto?id=7", MediaKind.IMAGE)
        assertEquals(listOf(DetectedMedia("https://cdn.example.com/foto?id=7", MediaKind.IMAGE)), found)
        assertTrue(MediaSniffer.add(emptyList(), "https://cdn.example.com/foto?id=7").isEmpty())
    }

    @Test
    fun `lee el resultado de la página con el tipo de cada archivo`() {
        val inner = """[{"t":"v","u":"https://cdn.example.com/v.mp4"},{"t":"a","u":"http://cdn.example.com/a"},""" +
            """{"t":"i","u":"https://cdn.example.com/foto?id=1"},{"t":"i","u":"blob:https://x/1"},""" +
            """{"t":"v","u":"https://cdn.example.com/v.mp4"},{"t":"x","u":"https://cdn.example.com/b.mp3"}]"""
        val raw = "\"" + inner.replace("\"", "\\\"") + "\""
        assertEquals(
            listOf(
                DetectedMedia("https://cdn.example.com/v.mp4", MediaKind.VIDEO),
                DetectedMedia("https://cdn.example.com/a", MediaKind.AUDIO),
                DetectedMedia("https://cdn.example.com/foto?id=1", MediaKind.IMAGE),
                DetectedMedia("https://cdn.example.com/b.mp3", MediaKind.AUDIO),
            ),
            MediaSniffer.parseDomResult(raw),
        )
    }

    @Test
    fun `lee tambien una lista simple de direcciones y tolera basura`() {
        val raw = "\"[\\\"https://cdn.example.com/v.mp4\\\",\\\"blob:https://x/1\\\",\\\"http://cdn.example.com/a.mp3\\\",\\\"https://x/p.html\\\"]\""
        assertEquals(
            listOf(
                DetectedMedia("https://cdn.example.com/v.mp4", MediaKind.VIDEO),
                DetectedMedia("https://cdn.example.com/a.mp3", MediaKind.AUDIO),
            ),
            MediaSniffer.parseDomResult(raw),
        )
        assertTrue(MediaSniffer.parseDomResult("null").isEmpty())
        assertTrue(MediaSniffer.parseDomResult("\"[]\"").isEmpty())
        assertTrue(MediaSniffer.parseDomResult("basura{").isEmpty())
        assertTrue(MediaSniffer.parseDomResult(null).isEmpty())
    }

    @Test
    fun `no se analizan portadas ni búsquedas`() {
        assertFalse(PageProbePolicy.shouldProbe("https://example.com"))
        assertFalse(PageProbePolicy.shouldProbe("https://example.com/"))
        assertFalse(PageProbePolicy.shouldProbe("https://example.com/search?q=gatos"))
        assertFalse(PageProbePolicy.shouldProbe("https://m.youtube.com/results?search_query=gatos"))
        assertTrue(PageProbePolicy.shouldProbe("https://example.com/watch?v=abc"))
        assertTrue(PageProbePolicy.shouldProbe("https://example.com/video/123"))
        assertFalse(PageProbePolicy.shouldProbe("javascript:alert(1)"))
    }

    @Test
    fun `las categorías buscan en el propio sitio`() {
        assertEquals("https://m.youtube.com/results?search_query=m%C3%BAsica", SearchProvider.YOUTUBE.webSearchUrl("música"))
        assertEquals("https://soundcloud.com/search?q=lofi+beats", SearchProvider.SOUNDCLOUD.webSearchUrl("lofi beats"))
    }
}
