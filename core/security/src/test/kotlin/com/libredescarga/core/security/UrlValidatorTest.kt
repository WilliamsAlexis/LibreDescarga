package com.libredescarga.core.security

import com.libredescarga.core.security.UrlValidator.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlValidatorTest {

    private fun valid(input: String) = UrlValidator.validate(input) as Result.Valid

    @Test
    fun `acepta https tal cual`() {
        val result = valid("https://example.com/watch?v=abc")
        assertEquals("https://example.com/watch?v=abc", result.url)
        assertEquals("example.com", result.host)
    }

    @Test
    fun `convierte http en https`() {
        assertEquals("https://example.com/a", valid("http://example.com/a").url)
        assertEquals("https://example.com/a", valid("HTTP://example.com/a").url)
    }

    @Test
    fun `completa dominios sin esquema`() {
        assertEquals("https://www.example.com/v/1", valid("www.example.com/v/1").url)
        assertEquals("https://youtu.be/xyz", valid("  youtu.be/xyz  ").url)
        assertEquals("https://example.com:8443/a", valid("example.com:8443/a").url)
    }

    @Test
    fun `bloquea esquemas peligrosos`() {
        listOf(
            "file:///sdcard/secret.txt",
            "content://com.android.contacts/data",
            "javascript:alert(1)",
            "JavaScript:alert(1)",
            "data:text/html,<script>",
            "intent://scan/#Intent;end",
            "ftp://example.com/file",
        ).forEach { assertEquals(it, Result.ForbiddenScheme, UrlValidator.validate(it)) }
    }

    @Test
    fun `las palabras clave no son enlaces`() {
        listOf("música relajante", "gatos", "", "   ", "hola.").forEach {
            assertEquals(it, Result.NotAUrl, UrlValidator.validate(it))
        }
    }

    @Test
    fun `rechaza credenciales incrustadas y hosts inválidos`() {
        assertEquals(Result.NotAUrl, UrlValidator.validate("https://user:pass@evil.com/"))
        assertEquals(Result.NotAUrl, UrlValidator.validate("https://localhost/"))
        assertEquals(Result.NotAUrl, UrlValidator.validate("https://-bad-.com/"))
        assertEquals(Result.NotAUrl, UrlValidator.validate("https://example.com:99999/"))
    }

    @Test
    fun `extrae la primera url de un texto compartido`() {
        assertEquals(
            "https://example.com/v/1",
            UrlValidator.extractFirstUrl("Mira este video: https://example.com/v/1. ¡Genial!"),
        )
        assertEquals(
            "https://example.com/v?id=2",
            UrlValidator.extractFirstUrl("(https://example.com/v?id=2)"),
        )
        assertNull(UrlValidator.extractFirstUrl("sin enlaces aquí"))
    }
}
