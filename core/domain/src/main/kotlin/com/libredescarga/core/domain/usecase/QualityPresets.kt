package com.libredescarga.core.domain.usecase

import com.libredescarga.core.model.VideoContainer
import com.libredescarga.core.model.VideoOption

/**
 * Calidades que se ofrecen de inmediato en YouTube mientras el análisis completo (lento) sigue buscando: 1080p, 720p, 360p,
 * 240p y 144p. Las que el análisis rápido ya confirmó llevan su formato real; las demás son "predefinidas": al descargar se
 * pide "hasta esa altura" y el motor elige el mejor formato disponible.
 */
object QualityPresets {

    /** De mayor a menor, como se muestran. */
    val HEIGHTS = listOf(1080, 720, 360, 240, 144)

    /** Las cinco calidades predefinidas, usando la opción real cuando [real] ya la incluye. */
    fun forPartial(real: List<VideoOption>): List<VideoOption> = HEIGHTS.map { height ->
        sameHeight(real, height) ?: preset(height)
    }

    /** La mejor opción real de [height] (prefiere MP4), o null si el video no la tiene. */
    fun sameHeight(real: List<VideoOption>, height: Int): VideoOption? =
        real.firstOrNull { !it.isPreset && it.height == height && it.container == VideoContainer.MP4 }
            ?: real.firstOrNull { !it.isPreset && it.height == height }

    private fun preset(height: Int) = VideoOption(
        formatId = "preset:$height",
        height = height,
        fps = null,
        isHdr = false,
        container = VideoContainer.MP4,
        hasAudio = false,
        estimatedBytes = null,
        isPreset = true,
    )
}
