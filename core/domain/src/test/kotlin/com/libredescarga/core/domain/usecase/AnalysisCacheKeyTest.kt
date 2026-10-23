package com.libredescarga.core.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AnalysisCacheKeyTest {

    @Test
    fun `todas las formas de un video de YouTube tienen la misma clave`() {
        val expected = "youtube:jNQXAC9IVRw"
        listOf(
            "https://www.youtube.com/watch?v=jNQXAC9IVRw",
            "https://youtu.be/jNQXAC9IVRw?si=abc123",
            "https://m.youtube.com/watch?v=jNQXAC9IVRw&list=PL123&t=30s",
            "https://music.youtube.com/watch?v=jNQXAC9IVRw&si=zzz",
            "https://www.youtube.com/shorts/jNQXAC9IVRw",
            "https://www.youtube.com/embed/jNQXAC9IVRw",
        ).forEach { assertEquals(it, expected, AnalysisCacheKey.of(it)) }
    }

    @Test
    fun `videos distintos tienen claves distintas`() {
        assertNotEquals(
            AnalysisCacheKey.of("https://youtu.be/aaaaaaaaaaa"),
            AnalysisCacheKey.of("https://youtu.be/bbbbbbbbbbb"),
        )
    }

    @Test
    fun `otros sitios ignoran el rastreo, www y la barra final`() {
        val a = AnalysisCacheKey.of("https://www.tiktok.com/@user/video/123/?is_from_webapp=1&sender_device=pc")
        val b = AnalysisCacheKey.of("https://tiktok.com/@user/video/123")
        assertEquals(a, b)
        assertEquals("tiktok.com/@user/video/123", b)
    }

    @Test
    fun `los parametros que identifican el contenido se conservan`() {
        val a = AnalysisCacheKey.of("https://www.facebook.com/watch/?v=111")
        val b = AnalysisCacheKey.of("https://www.facebook.com/watch/?v=222")
        assertNotEquals(a, b)
        assertEquals(a, AnalysisCacheKey.of("https://facebook.com/watch?v=111&fbclid=xyz"))
    }
}
