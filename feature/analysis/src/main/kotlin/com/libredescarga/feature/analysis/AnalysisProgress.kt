package com.libredescarga.feature.analysis

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Progreso ESTIMADO del análisis. yt-dlp no informa un avance real mientras extrae los formatos, así que la barra se calcula
 * con el tiempo transcurrido frente a lo que tardó el análisis anterior de ese tipo de enlace (se aprende en cada análisis).
 * Sube de forma continua, se frena cerca del 95 % si tarda más de lo esperado y solo llega al 100 % cuando termina de verdad.
 */
internal object AnalysisProgress {

    /** Tipos de enlace con tiempos distintos. */
    enum class Kind(val defaultMs: Long) {
        /** Video concreto de YouTube: el análisis completo tarda ~15–18 s en un teléfono de gama media. */
        YOUTUBE(18_000),
        OTHER(6_000),
    }

    private const val PLATEAU = 0.95f
    private const val MAIN_PART = 0.90f
    private const val MIN_LEARNABLE_MS = 1_500L

    private val learnedMs = ConcurrentHashMap<Kind, Long>()

    fun expectedMs(kind: Kind): Long = learnedMs[kind] ?: kind.defaultMs

    /**
     * Guarda lo que tardó un análisis completo; el siguiente usa el promedio entre lo aprendido y lo nuevo.
     * Los que duran menos de [MIN_LEARNABLE_MS] (resultado de la caché) no enseñan nada y se ignoran.
     */
    fun record(kind: Kind, elapsedMs: Long) {
        if (elapsedMs < MIN_LEARNABLE_MS) return
        learnedMs[kind] = (expectedMs(kind) + elapsedMs) / 2
    }

    /** Avance de 0 a 1 tras [elapsedMs] de análisis, con [expectedMs] como duración esperada. */
    fun estimate(elapsedMs: Long, expectedMs: Long): Float {
        if (elapsedMs <= 0 || expectedMs <= 0) return 0f
        val ratio = elapsedMs.toFloat() / expectedMs
        if (ratio <= 1f) return ratio * MAIN_PART
        // Tardó más de lo esperado: se acerca despacio a 95 % sin llegar nunca a 100 %.
        val extra = ratio - 1f
        return min(PLATEAU, MAIN_PART + (PLATEAU - MAIN_PART) * (1f - exp(-extra)))
    }

    fun percent(progress: Float): Int = (progress.coerceIn(0f, 1f) * 100).roundToInt()

}
