package com.libredescarga.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultSecurityTest {

    @Test
    fun `PBKDF2 coincide con el vector de prueba RFC 7914`() {
        // PBKDF2-HMAC-SHA256("passwd", "salt", c = 1, dkLen = 32) — vector de la RFC 7914 §11.
        val result = SecretHasher.pbkdf2("passwd", "salt".toByteArray(), iterations = 1)
        assertEquals("55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc", Hex.encode(result))
    }

    @Test
    fun `PBKDF2 coincide con el vector de varias iteraciones`() {
        // PBKDF2-HMAC-SHA256("password", "salt", c = 4096, dkLen = 32) — vector conocido.
        val result = SecretHasher.pbkdf2("password", "salt".toByteArray(), iterations = 4096)
        assertEquals("c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a", Hex.encode(result))
    }

    @Test
    fun `verifica el secreto correcto y rechaza los demás`() {
        val stored = SecretHasher.hash("pin:4821", iterations = 1_000)
        assertTrue(SecretHasher.verify("pin:4821", stored))
        assertFalse(SecretHasher.verify("pin:4822", stored))
        assertFalse(SecretHasher.verify("", stored))
    }

    @Test
    fun `cada hash usa una sal distinta y no contiene el secreto`() {
        val a = SecretHasher.hash("pin:1357", iterations = 1_000)
        val b = SecretHasher.hash("pin:1357", iterations = 1_000)
        assertNotEquals(a.saltHex, b.saltHex)
        assertNotEquals(a.hashHex, b.hashHex)
        assertFalse(a.hashHex.contains("1357"))
    }

    @Test
    fun `un registro dañado nunca valida`() {
        assertFalse(SecretHasher.verify("x", HashedSecret("zz", "00", 10)))
        assertFalse(SecretHasher.verify("x", HashedSecret("abc", "00", 10)))
    }

    @Test
    fun `hexadecimal ida y vuelta`() {
        val bytes = byteArrayOf(0, 1, 127, -128, -1, 42)
        assertArrayEquals(bytes, Hex.decode(Hex.encode(bytes)))
    }

    @Test
    fun `la espera crece tras cinco fallos y se detiene en quince minutos`() {
        assertEquals(0, LockoutPolicy.delayMs(0))
        assertEquals(0, LockoutPolicy.delayMs(4))
        assertEquals(30_000, LockoutPolicy.delayMs(5))
        assertEquals(60_000, LockoutPolicy.delayMs(6))
        assertEquals(120_000, LockoutPolicy.delayMs(7))
        assertEquals(15 * 60_000L, LockoutPolicy.delayMs(30))
        assertEquals(15 * 60_000L, LockoutPolicy.delayMs(1_000))
    }

    @Test
    fun `intentos restantes antes de la espera`() {
        assertEquals(5, LockoutPolicy.attemptsBeforeLock(0))
        assertEquals(1, LockoutPolicy.attemptsBeforeLock(4))
        assertEquals(0, LockoutPolicy.attemptsBeforeLock(9))
    }

    @Test
    fun `la respuesta de seguridad ignora mayúsculas, tildes y espacios`() {
        val reference = SecurityAnswer.normalize("Pérez")
        assertEquals(reference, SecurityAnswer.normalize("  PEREZ "))
        assertEquals("san jose de la montana", SecurityAnswer.normalize("San  José   de la Montaña"))
        assertTrue(SecurityAnswer.isAcceptable("Luna"))
        assertFalse(SecurityAnswer.isAcceptable(" a "))
    }

    @Test
    fun `reglas del PIN`() {
        assertTrue(CredentialRules.isValidPin("4821"))
        assertTrue(CredentialRules.isValidPin("123456789012"))
        assertFalse(CredentialRules.isValidPin("123"))
        assertFalse(CredentialRules.isValidPin("1234567890123"))
        assertFalse(CredentialRules.isValidPin("12a4"))
        assertTrue(CredentialRules.isWeakPin("0000"))
        assertTrue(CredentialRules.isWeakPin("1234"))
        assertTrue(CredentialRules.isWeakPin("9876"))
        assertFalse(CredentialRules.isWeakPin("4821"))
    }

    @Test
    fun `reglas del patrón`() {
        assertTrue(CredentialRules.isValidPattern(listOf(0, 1, 2, 5)))
        assertFalse(CredentialRules.isValidPattern(listOf(0, 1, 2)))
        assertFalse(CredentialRules.isValidPattern(listOf(0, 1, 1, 2)))
        assertFalse(CredentialRules.isValidPattern(listOf(0, 1, 2, 9)))
        assertEquals("0125", CredentialRules.patternToText(listOf(0, 1, 2, 5)))
    }
}
