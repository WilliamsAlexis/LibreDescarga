package com.libredescarga.core.engine

import com.libredescarga.core.model.AnalysisResult
import com.libredescarga.core.model.AudioFormat
import com.libredescarga.core.model.MediaError
import com.libredescarga.core.model.VideoContainer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InfoParserTest {

    private fun parse(text: String, url: String = "https://example.com/v") =
        InfoParser.parse(Json.parseToJsonElement(text).jsonObject, url)

    private val videoJson = """
        {
          "id": "abc", "title": "Video de prueba", "uploader": "Canal",
          "duration": 100, "view_count": 42, "webpage_url": "https://example.com/watch/abc",
          "extractor_key": "Example", "language": "es",
          "thumbnails": [{"url": "https://img/1.jpg"}, {"url": "https://img/2.jpg"}],
          "formats": [
            {"format_id": "sb0", "ext": "mhtml", "vcodec": "none", "acodec": "none"},
            {"format_id": "140", "ext": "m4a", "vcodec": "none", "acodec": "mp4a.40.2", "abr": 129, "filesize": 1600000},
            {"format_id": "251", "ext": "webm", "vcodec": "none", "acodec": "opus", "abr": 135, "filesize": 1500000},
            {"format_id": "136", "ext": "mp4", "vcodec": "avc1", "acodec": "none", "height": 720, "fps": 30, "tbr": 1200, "filesize": 10000000},
            {"format_id": "298", "ext": "mp4", "vcodec": "avc1", "acodec": "none", "height": 720, "fps": 60, "tbr": 1800, "filesize": 15000000},
            {"format_id": "247", "ext": "webm", "vcodec": "vp9", "acodec": "none", "height": 720, "fps": 30, "tbr": 900},
            {"format_id": "137", "ext": "mp4", "vcodec": "avc1", "acodec": "none", "height": 1080, "fps": 30, "tbr": 2500, "filesize_approx": 30000000},
            {"format_id": "137lo", "ext": "mp4", "vcodec": "avc1", "acodec": "none", "height": 1080, "fps": 30, "tbr": 1000},
            {"format_id": "337", "ext": "webm", "vcodec": "vp9.2", "acodec": "none", "height": 2160, "fps": 60, "dynamic_range": "HDR10", "tbr": 20000},
            {"format_id": "18", "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a", "height": 360, "fps": 30, "filesize": 5000000}
          ],
          "subtitles": {"es": [{"name": "Español", "ext": "vtt"}], "live_chat": [{}]},
          "automatic_captions": {"es": [{}], "en": [{}], "fr": [{}], "de-orig": [{}]}
        }
    """.trimIndent()

    @Test
    fun `un video produce información completa`() {
        val info = (parse(videoJson) as AnalysisResult.Single).info
        assertEquals("abc", info.id)
        assertEquals("Video de prueba", info.title)
        assertEquals("Canal", info.uploader)
        assertEquals(100L, info.durationSeconds)
        assertEquals(42L, info.viewCount)
        assertEquals("https://img/2.jpg", info.thumbnailUrl)
        assertEquals("https://example.com/watch/abc", info.url)
    }

    @Test
    fun `agrupa calidades y ordena de mayor a menor`() {
        val options = (parse(videoJson) as AnalysisResult.Single).info.videoOptions
        val labels = options.map { "${it.height}p${it.fps}-${it.container}-${it.isHdr}" }
        assertEquals(
            listOf(
                "2160p60-WEBM-true",
                "1080p30-MP4-false",
                "720p60-MP4-false",
                "720p30-MP4-false",
                "720p30-WEBM-false",
                "360p30-MP4-false",
            ),
            labels,
        )
        // Entre dos 1080p MP4 se elige la de mayor tasa de bits.
        assertEquals("137", options.first { it.height == 1080 }.formatId)
    }

    @Test
    fun `el tamaño de un video sin audio incluye el mejor audio`() {
        val options = (parse(videoJson) as AnalysisResult.Single).info.videoOptions
        val p720 = options.first { it.formatId == "136" }
        assertFalse(p720.hasAudio)
        // 10 MB de video + 1,5 MB del mejor audio (opus, abr 135)
        assertEquals(11_500_000L, p720.estimatedBytes)

        val p360 = options.first { it.formatId == "18" }
        assertTrue(p360.hasAudio)
        assertEquals(5_000_000L, p360.estimatedBytes)
    }

    @Test
    fun `ofrece MP3, M4A y OPUS con tamaños estimados`() {
        val audio = (parse(videoJson) as AnalysisResult.Single).info.audioOptions
        assertEquals(
            listOf(AudioFormat.MP3_320, AudioFormat.MP3_192, AudioFormat.MP3_128, AudioFormat.M4A, AudioFormat.OPUS),
            audio.map { it.format },
        )
        assertEquals(100L * 320 * 125, audio.first { it.format == AudioFormat.MP3_320 }.estimatedBytes)
        assertEquals(1_600_000L, audio.first { it.format == AudioFormat.M4A }.estimatedBytes)
        assertEquals(1_500_000L, audio.first { it.format == AudioFormat.OPUS }.estimatedBytes)
    }

    @Test
    fun `subtítulos manuales y automáticos del idioma original`() {
        val subs = (parse(videoJson) as AnalysisResult.Single).info.subtitles
        assertEquals(listOf("es" to false, "de-orig" to true), subs.map { it.languageCode to it.isAutomatic })
        assertEquals("Español", subs.first().name)
    }

    @Test
    fun `una lista de reproducción devuelve sus elementos`() {
        val json = """
            {"_type": "playlist", "id": "PL1", "title": "Mi lista", "channel": "Canal",
             "webpage_url": "https://example.com/list/PL1",
             "entries": [
               {"id": "a", "title": "Uno", "url": "https://example.com/watch/a", "duration": 61.5},
               {"id": "b", "title": "Dos", "url": "https://example.com/watch/b",
                "thumbnails": [{"url": "https://img/b.jpg"}]},
               {"id": "c", "title": "Sin enlace", "url": "c"}
             ]}
        """.trimIndent()
        val playlist = (parse(json) as AnalysisResult.Playlist).playlist
        assertEquals("Mi lista", playlist.title)
        assertEquals("Canal", playlist.uploader)
        assertEquals(listOf("a", "b"), playlist.entries.map { it.id })
        assertEquals(61L, playlist.entries[0].durationSeconds)
        assertEquals("https://img/b.jpg", playlist.entries[1].thumbnailUrl)
    }

    @Test(expected = MediaError.DrmProtected::class)
    fun `contenido con DRM se rechaza`() {
        parse(
            """{"id": "x", "title": "Protegido", "formats": [
                {"format_id": "1", "ext": "mp4", "vcodec": "avc1", "height": 720, "has_drm": true}]}""",
        )
    }

    @Test(expected = MediaError.LiveStream::class)
    fun `las transmisiones en vivo se rechazan`() {
        parse("""{"id": "x", "title": "Directo", "is_live": true, "formats": []}""")
    }

    @Test
    fun `un enlace directo sin resolución ofrece calidad original`() {
        val info = (
            parse(
                """{"id": "f", "title": "archivo", "url": "https://cdn.example.com/f.webm", "ext": "webm", "format_id": "0"}""",
            ) as AnalysisResult.Single
            ).info
        val option = info.videoOptions.single()
        assertEquals(0, option.height)
        assertEquals(VideoContainer.WEBM, option.container)
        assertTrue(option.hasAudio)
    }
}
