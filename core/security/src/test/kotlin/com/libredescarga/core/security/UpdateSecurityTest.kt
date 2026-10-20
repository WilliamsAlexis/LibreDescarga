package com.libredescarga.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateSecurityTest {

    @Test
    fun `SHA-256 de un contenido conocido`() {
        // SHA-256("abc") — vector estándar de FIPS 180-2.
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            UpdateSecurity.sha256Hex("abc".byteInputStream()),
        )
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            UpdateSecurity.sha256Hex(ByteArray(0).inputStream()),
        )
    }

    @Test
    fun `compara resúmenes sin importar mayúsculas ni espacios`() {
        val hash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        assertTrue(UpdateSecurity.hashMatches(hash.uppercase(), " $hash "))
        assertFalse(UpdateSecurity.hashMatches(hash, hash.replaceRange(0, 1, "0")))
        assertFalse(UpdateSecurity.hashMatches("", hash))
    }

    @Test
    fun `solo se aceptan resúmenes de 64 caracteres hexadecimales`() {
        assertTrue(UpdateSecurity.isValidSha256("a".repeat(64)))
        assertTrue(UpdateSecurity.isValidSha256("A1".repeat(32)))
        assertFalse(UpdateSecurity.isValidSha256("a".repeat(63)))
        assertFalse(UpdateSecurity.isValidSha256("g".repeat(64)))
        assertFalse(UpdateSecurity.isValidSha256(""))
    }

    @Test
    fun `solo se descarga de GitHub por https`() {
        assertTrue(UpdateSecurity.isAllowedDownloadUrl("https://github.com/LibreDescarga/LibreDescarga/releases/download/v1/app.apk"))
        assertTrue(UpdateSecurity.isAllowedDownloadUrl("https://objects.githubusercontent.com/abc/app.apk"))
        assertTrue(UpdateSecurity.isAllowedDownloadUrl("https://raw.githubusercontent.com/o/r/main/update.json"))
        assertFalse(UpdateSecurity.isAllowedDownloadUrl("http://github.com/o/r/releases/download/v1/app.apk"))
        assertFalse(UpdateSecurity.isAllowedDownloadUrl("https://evil.com/github.com/app.apk"))
        assertFalse(UpdateSecurity.isAllowedDownloadUrl("https://github.com.evil.com/app.apk"))
        assertFalse(UpdateSecurity.isAllowedDownloadUrl("https://notgithub.com/app.apk"))
        assertFalse(UpdateSecurity.isAllowedDownloadUrl("file:///sdcard/app.apk"))
        assertFalse(UpdateSecurity.isAllowedDownloadUrl("javascript:alert(1)"))
    }

    @Test
    fun `la firma debe coincidir con la de la app instalada`() {
        assertTrue(UpdateSecurity.sameSigner(setOf("AABB"), setOf("aabb")))
        assertTrue(UpdateSecurity.sameSigner(setOf("old"), setOf("new", "old"))) // Rotación de claves.
        assertFalse(UpdateSecurity.sameSigner(setOf("aabb"), setOf("ccdd")))
        assertFalse(UpdateSecurity.sameSigner(emptySet(), setOf("aabb")))
        assertFalse(UpdateSecurity.sameSigner(setOf("aabb"), emptySet()))
    }

    @Test
    fun `nunca se baja de versión`() {
        assertTrue(UpdateSecurity.isNewer(1, 2))
        assertFalse(UpdateSecurity.isNewer(2, 2))
        assertFalse(UpdateSecurity.isNewer(3, 2))
    }

    @Test
    fun `normaliza dominios de la lista negra`() {
        assertEquals("ejemplo.com", DomainRules.normalize("ejemplo.com"))
        assertEquals("ejemplo.com", DomainRules.normalize("https://www.Ejemplo.com/ruta?x=1"))
        assertEquals("video.ejemplo.org", DomainRules.normalize("http://video.ejemplo.org"))
        assertNull(DomainRules.normalize(""))
        assertNull(DomainRules.normalize("   "))
        assertNull(DomainRules.normalize("palabra"))
        assertNull(DomainRules.normalize("javascript:alert(1)"))
    }
}
