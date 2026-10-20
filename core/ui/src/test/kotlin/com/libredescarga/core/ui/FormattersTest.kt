package com.libredescarga.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormattersTest {

    @Test
    fun `formato de duración`() {
        assertEquals("0:05", formatDuration(5))
        assertEquals("4:05", formatDuration(245))
        assertEquals("1:02:05", formatDuration(3725))
    }

    @Test
    fun `lee tiempos escritos por el usuario`() {
        assertEquals(90L, parseTimeInput("90"))
        assertEquals(90L, parseTimeInput("1:30"))
        assertEquals(3750L, parseTimeInput("1:02:30"))
        assertNull(parseTimeInput(""))
        assertNull(parseTimeInput("1:75"))
        assertNull(parseTimeInput("a:10"))
        assertNull(parseTimeInput("1::2"))
        assertNull(parseTimeInput("1:2:3:4"))
    }
}
