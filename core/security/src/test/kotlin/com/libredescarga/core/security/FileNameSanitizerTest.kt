package com.libredescarga.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNameSanitizerTest {

    @Test
    fun `elimina separadores de ruta (path traversal)`() {
        val result = FileNameSanitizer.sanitize("../../data/data/app/secreto")
        assertFalse(result.contains('/'))
        assertFalse(result.startsWith("."))
        assertEquals("data_data_app_secreto", result)
    }

    @Test
    fun `reemplaza caracteres prohibidos`() {
        assertEquals("a_b_c_d_e_f_g_h_i", FileNameSanitizer.sanitize("a\\b:c*d?e\"f<g>h|i"))
        assertEquals("linea_nueva", FileNameSanitizer.sanitize("linea\u0000nueva"))
    }

    @Test
    fun `colapsa espacios y recorta`() {
        assertEquals("Mi video genial", FileNameSanitizer.sanitize("  Mi   video\tgenial  "))
    }

    @Test
    fun `limita la longitud`() {
        val result = FileNameSanitizer.sanitize("x".repeat(500))
        assertEquals(FileNameSanitizer.MAX_LENGTH, result.length)
    }

    @Test
    fun `usa el nombre por defecto si queda vacío`() {
        assertEquals("LibreDescarga", FileNameSanitizer.sanitize("///"))
        assertEquals("LibreDescarga", FileNameSanitizer.sanitize("..."))
        assertEquals("audio", FileNameSanitizer.sanitize("", fallback = "audio"))
    }

    @Test
    fun `protege nombres reservados`() {
        assertEquals("_CON", FileNameSanitizer.sanitize("CON"))
    }

    @Test
    fun `conserva acentos y emojis`() {
        assertEquals("Canción ñandú 🎵", FileNameSanitizer.sanitize("Canción ñandú 🎵"))
    }

    @Test
    fun `DomainMatcher detecta subdominios`() {
        val list = listOf("ejemplo.com")
        assertEquals("ejemplo.com", DomainMatcher.matches("www.ejemplo.com", list))
        assertEquals("ejemplo.com", DomainMatcher.matches("m.video.ejemplo.com", list))
        assertNull(DomainMatcher.matches("noejemplo.com", list))
        assertTrue(DomainMatcher.matches("EJEMPLO.COM", list) != null)
    }
}
