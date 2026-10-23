package com.libredescarga.feature.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisProgressTest {

    @Test
    fun `empieza en cero y sube de forma continua hasta cerca del 90 por ciento al tiempo esperado`() {
        assertEquals(0f, AnalysisProgress.estimate(0, 10_000), 0f)
        assertEquals(0.45f, AnalysisProgress.estimate(5_000, 10_000), 0.001f)
        assertEquals(0.90f, AnalysisProgress.estimate(10_000, 10_000), 0.001f)
        assertTrue(AnalysisProgress.estimate(2_000, 10_000) < AnalysisProgress.estimate(4_000, 10_000))
    }

    @Test
    fun `si tarda mas de lo esperado se frena antes del 100 por ciento`() {
        val late = AnalysisProgress.estimate(60_000, 10_000)
        assertTrue(late > 0.90f)
        assertTrue(late <= 0.95f)
        assertEquals(0.95f, AnalysisProgress.estimate(10_000_000, 10_000), 0.0001f)
    }

    @Test
    fun `aprende cuanto tarda cada tipo de enlace`() {
        val kind = AnalysisProgress.Kind.OTHER
        val before = AnalysisProgress.expectedMs(kind)
        AnalysisProgress.record(kind, before + 4_000)

        assertEquals(before + 2_000, AnalysisProgress.expectedMs(kind))
    }

    @Test
    fun `el porcentaje se redondea y se limita entre 0 y 100`() {
        assertEquals(46, AnalysisProgress.percent(0.456f))
        assertEquals(0, AnalysisProgress.percent(-1f))
        assertEquals(100, AnalysisProgress.percent(2f))
    }

    @Test
    fun `un analisis servido desde la cache no cambia el tiempo esperado`() {
        val kind = AnalysisProgress.Kind.YOUTUBE
        val before = AnalysisProgress.expectedMs(kind)

        AnalysisProgress.record(kind, 54)

        assertEquals(before, AnalysisProgress.expectedMs(kind))
    }
}
