package com.libredescarga.core.engine

import com.libredescarga.core.model.AudioFormat
import com.libredescarga.core.model.DownloadRequest
import com.libredescarga.core.model.MediaError
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.model.SubtitleSelection
import com.libredescarga.core.model.VideoContainer
import com.libredescarga.core.model.VideoSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineHelpersTest {

    private val base = DownloadRequest(
        url = "https://example.com/v",
        title = "Título",
        uploader = null,
        thumbnailUrl = null,
        durationSeconds = 300,
        kind = MediaKind.VIDEO,
        video = VideoSelection(formatId = "137", maxHeight = null, container = VideoContainer.MP4, formatHasAudio = false),
        audioFormat = null,
        subtitle = null,
        fileName = "Mi video 100%",
    )

    private fun List<String>.valueOf(flag: String): String? = indexOf(flag).takeIf { it >= 0 }?.let { this[it + 1] }

    @Test
    fun `video con formato concreto une el mejor audio compatible`() {
        val args = DownloadCommandBuilder.build(base, "/tmp/d1")
        assertEquals(
            "137+ba[ext=m4a]/137+ba/137/bv*[ext=mp4]+ba[ext=m4a]/bv*+ba/b",
            args.valueOf("-f"),
        )
        assertEquals("mp4", args.valueOf("--merge-output-format"))
        assertEquals("/tmp/d1/Mi video 100%%.%(ext)s", args.valueOf("-o"))
        assertTrue("--no-playlist" in args)
        assertEquals("4", args.valueOf("--concurrent-fragments"))
    }

    @Test
    fun `video con audio incluido usa solo su formato`() {
        val request = base.copy(video = base.video!!.copy(formatId = "18", formatHasAudio = true))
        assertEquals("18/bv*+ba/b", DownloadCommandBuilder.build(request, "/tmp").valueOf("-f"))
    }

    @Test
    fun `descarga por calidad limita la altura`() {
        val request = base.copy(video = VideoSelection(null, 720, VideoContainer.WEBM, false))
        val args = DownloadCommandBuilder.build(request, "/tmp")
        assertEquals(
            "bv*[height<=720][ext=webm]+ba[ext=webm]/bv*[height<=720]+ba/b[height<=720]/b",
            args.valueOf("-f"),
        )
        assertEquals("webm", args.valueOf("--merge-output-format"))
    }

    @Test
    fun `una calidad baja que el video no tiene baja al minimo y no al mejor`() {
        val request = base.copy(video = VideoSelection(null, 144, VideoContainer.MP4, false))
        assertEquals(
            "bv*[height<=144][ext=mp4]+ba[ext=m4a]/bv*[height<=144]+ba/b[height<=144]/wv*+wa/w",
            DownloadCommandBuilder.build(request, "/tmp").valueOf("-f"),
        )
    }

    @Test
    fun `audio MP3 320 extrae, convierte e incrusta carátula y metadatos`() {
        val request = base.copy(kind = MediaKind.AUDIO, video = null, audioFormat = AudioFormat.MP3_320)
        val args = DownloadCommandBuilder.build(request, "/tmp")
        assertTrue("--extract-audio" in args)
        assertEquals("ba[ext=m4a]/ba/b", args.valueOf("-f"))
        assertEquals("mp3", args.valueOf("--audio-format"))
        assertEquals("320K", args.valueOf("--audio-quality"))
        assertTrue("--embed-thumbnail" in args)
        assertTrue("--embed-metadata" in args)
    }

    @Test
    fun `audio OPUS usa la mejor calidad y no incrusta carátula`() {
        val request = base.copy(kind = MediaKind.AUDIO, video = null, audioFormat = AudioFormat.OPUS)
        val args = DownloadCommandBuilder.build(request, "/tmp")
        assertEquals("opus", args.valueOf("--audio-format"))
        assertEquals("0", args.valueOf("--audio-quality"))
        assertFalse("--embed-thumbnail" in args)
    }

    @Test
    fun `subtítulos aparte en SRT o incrustados`() {
        val separate = base.copy(subtitle = SubtitleSelection("es", isAutomatic = false, embed = false))
        val args = DownloadCommandBuilder.build(separate, "/tmp")
        assertTrue("--write-subs" in args)
        assertEquals("es", args.valueOf("--sub-langs"))
        assertEquals("srt", args.valueOf("--convert-subs"))

        val embedded = base.copy(subtitle = SubtitleSelection("en-orig", isAutomatic = true, embed = true))
        val embeddedArgs = DownloadCommandBuilder.build(embedded, "/tmp")
        assertTrue("--write-auto-subs" in embeddedArgs)
        assertTrue("--embed-subs" in embeddedArgs)
    }

    @Test
    fun `limite de velocidad y fragmentos en paralelo`() {
        val args = DownloadCommandBuilder.build(base, "/tmp", DownloadTuning(concurrentFragments = 8, rateLimitKbps = 500))
        assertEquals("500K", args.valueOf("--limit-rate"))
        assertEquals("8", args.valueOf("--concurrent-fragments"))
        assertTrue("--download-sections" !in args)
    }

    @Test
    fun `progreso lee tamaño, velocidad y bytes descargados`() {
        val progress = ProgressLineParser.parse(
            percent = 50f,
            etaSeconds = 10,
            line = "[download]  50.0% of ~  10.00MiB at    2.00MiB/s ETA 00:10 (frag 3/20)",
        )
        assertEquals(10L * 1024 * 1024, progress.totalBytes)
        assertEquals(2L * 1024 * 1024, progress.speedBytesPerSecond)
        assertEquals(5L * 1024 * 1024, progress.downloadedBytes)
        assertEquals(10L, progress.etaSeconds)
    }

    @Test
    fun `progreso tolera líneas sin datos`() {
        val progress = ProgressLineParser.parse(-1f, -1, "[Merger] Merging formats")
        assertEquals(0f, progress.percent)
        assertNull(progress.etaSeconds)
        assertNull(progress.totalBytes)
        assertNull(progress.speedBytesPerSecond)
    }

    @Test
    fun `los errores de yt-dlp se traducen a mensajes conocidos`() {
        assertTrue(EngineErrorMapper.map("ERROR: [x] abc: This video is DRM protected") is MediaError.DrmProtected)
        assertTrue(EngineErrorMapper.map("ERROR: Unsupported URL: https://x") is MediaError.Unsupported)
        assertTrue(EngineErrorMapper.map("ERROR: Private video. Sign in") is MediaError.NotPublic)
        assertTrue(
            EngineErrorMapper.map("ERROR: [youtube] 07FYdnEawAQ: Sign in to confirm your age. Use --cookies-from-browser") is
                MediaError.AgeRestricted,
        )
        assertTrue(
            EngineErrorMapper.map("ERROR: [youtube] x: Sign in to confirm you're not a bot. Use --cookies") is MediaError.BotCheck,
        )
        assertTrue(EngineErrorMapper.map("ERROR: Video unavailable") is MediaError.Unavailable)
        assertTrue(
            EngineErrorMapper.map("ERROR: Unable to download webpage: <urlopen error timed out>") is MediaError.Network,
        )
        assertTrue(EngineErrorMapper.map("OSError: [Errno 28] No space left on device") is MediaError.InsufficientStorage)

        val unknown = EngineErrorMapper.map("WARNING: algo\nERROR: fallo raro") as MediaError.Unknown
        assertEquals("fallo raro", unknown.details)
    }
}
