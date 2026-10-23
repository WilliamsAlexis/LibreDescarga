package com.libredescarga.core.domain.usecase

import com.libredescarga.core.model.VideoContainer
import com.libredescarga.core.model.VideoOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityPresetsTest {

    private fun real(id: String, height: Int, container: VideoContainer = VideoContainer.MP4) = VideoOption(
        formatId = id, height = height, fps = 30, isHdr = false, container = container, hasAudio = false, estimatedBytes = 1_000,
    )

    @Test
    fun `ofrece las cinco calidades de mayor a menor`() {
        val options = QualityPresets.forPartial(emptyList())

        assertEquals(listOf(1080, 720, 360, 240, 144), options.map { it.height })
        assertTrue(options.all { it.isPreset && it.estimatedBytes == null })
    }

    @Test
    fun `usa la opcion real cuando el analisis rapido ya la trae`() {
        val options = QualityPresets.forPartial(listOf(real("18", 360), real("x", 27), real("y", 90)))

        val p360 = options.single { it.height == 360 }
        assertFalse(p360.isPreset)
        assertEquals("18", p360.formatId)
        // Las alturas que no están entre las cinco (27p, 90p...) no se muestran.
        assertEquals(5, options.size)
        assertTrue(options.filter { it.height != 360 }.all { it.isPreset })
    }

    @Test
    fun `prefiere mp4 a webm y ignora las predefinidas al buscar una altura`() {
        val options = listOf(real("w", 720, VideoContainer.WEBM), real("m", 720, VideoContainer.MP4))

        assertEquals("m", QualityPresets.sameHeight(options, 720)?.formatId)
        assertNull(QualityPresets.sameHeight(QualityPresets.forPartial(emptyList()), 720))
    }
}
