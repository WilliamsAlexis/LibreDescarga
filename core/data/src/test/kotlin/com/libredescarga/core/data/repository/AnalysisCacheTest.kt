package com.libredescarga.core.data.repository

import com.libredescarga.core.model.AnalysisResult
import com.libredescarga.core.testing.TestMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnalysisCacheTest {

    private var now = 1_000L
    private val result = AnalysisResult.Single(TestMedia.info)

    private fun cache(ttlMs: Long = 600_000, max: Int = 30) = AnalysisCache(ttlMs, max) { now }

    @Test
    fun `reutiliza el resultado de otra forma del mismo enlace`() {
        val cache = cache()
        cache.put("https://youtu.be/jNQXAC9IVRw?si=abc", result)

        assertEquals(result, cache.get("https://www.youtube.com/watch?v=jNQXAC9IVRw"))
        assertNull(cache.get("https://youtu.be/otroVideo123"))
    }

    @Test
    fun `el resultado caduca pasado el tiempo`() {
        val cache = cache(ttlMs = 600_000)
        cache.put("https://example.com/a", result)

        now += 599_000
        assertEquals(result, cache.get("https://example.com/a"))

        now += 2_000
        assertNull(cache.get("https://example.com/a"))
    }

    @Test
    fun `descarta el menos usado al pasar el limite`() {
        val cache = cache(max = 2)
        cache.put("https://example.com/1", result)
        cache.put("https://example.com/2", result)
        cache.get("https://example.com/1") // lo usa: el 2 queda como el menos reciente
        cache.put("https://example.com/3", result)

        assertEquals(result, cache.get("https://example.com/1"))
        assertNull(cache.get("https://example.com/2"))
        assertEquals(result, cache.get("https://example.com/3"))
    }
}
