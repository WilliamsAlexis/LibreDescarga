package com.libredescarga.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun `formato de duración`() {
        assertEquals("0:05", formatDuration(5))
        assertEquals("4:05", formatDuration(245))
        assertEquals("1:02:05", formatDuration(3725))
    }
}
