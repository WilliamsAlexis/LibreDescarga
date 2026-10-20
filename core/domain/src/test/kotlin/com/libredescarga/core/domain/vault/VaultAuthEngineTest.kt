package com.libredescarga.core.domain.vault

import com.libredescarga.core.model.CredentialType
import com.libredescarga.core.model.VaultCredential
import com.libredescarga.core.model.VerifyResult
import com.libredescarga.core.testing.FakeVaultAuthStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class VaultAuthEngineTest {

    private var clock = 1_000_000L
    private val store = FakeVaultAuthStore()
    private val engine = VaultAuthEngine(store, now = { clock }, iterations = 200)

    private val pin = VaultCredential(CredentialType.PIN, "4821")
    private val wrongPin = VaultCredential(CredentialType.PIN, "0000")
    private val pattern = VaultCredential(CredentialType.PATTERN, "01258")

    private suspend fun setUpWithPin() = engine.setUp(pin, "¿Cómo se llama tu primera mascota?", "Luna")

    @Test
    fun `sin configurar no hay acceso`() = runTest {
        assertFalse(engine.settings.first().isSetUp)
        assertEquals(VerifyResult.NotSetUp, engine.verify(pin))
        assertEquals(VerifyResult.NotSetUp, engine.verifyAnswer("Luna"))
    }

    @Test
    fun `configurar guarda solo resúmenes, nunca el PIN ni la respuesta`() = runTest {
        setUpWithPin()
        val stored = store.current
        assertEquals(CredentialType.PIN, stored.credentialType)
        assertNotNull(stored.credentialHash)
        assertNotNull(stored.answerHash)
        assertFalse(stored.credentialHash!!.hashHex.contains("4821"))
        assertEquals("¿Cómo se llama tu primera mascota?", engine.settings.first().securityQuestion)
        assertTrue(engine.settings.first().isSetUp)
    }

    @Test
    fun `el PIN correcto abre y el incorrecto no`() = runTest {
        setUpWithPin()
        assertEquals(VerifyResult.Success, engine.verify(pin))
        assertEquals(VerifyResult.Wrong(attemptsBeforeLock = 4), engine.verify(wrongPin))
        assertEquals(VerifyResult.Wrong(attemptsBeforeLock = 3), engine.verify(wrongPin))
        // Acertar reinicia el contador.
        assertEquals(VerifyResult.Success, engine.verify(pin))
        assertEquals(VerifyResult.Wrong(attemptsBeforeLock = 4), engine.verify(wrongPin))
    }

    @Test
    fun `un patrón no se confunde con un PIN con los mismos números`() = runTest {
        engine.setUp(pattern, "Pregunta", "respuesta")
        assertEquals(VerifyResult.Success, engine.verify(pattern))
        assertTrue(engine.verify(VaultCredential(CredentialType.PIN, "1258")) is VerifyResult.Wrong)
        assertEquals(CredentialType.PATTERN, engine.settings.first().credentialType)
    }

    @Test
    fun `tras cinco fallos hay que esperar, aunque se acierte`() = runTest {
        setUpWithPin()
        repeat(4) { engine.verify(wrongPin) }
        val locked = engine.verify(wrongPin) as VerifyResult.LockedOut
        assertEquals(clock + 30_000, locked.untilMs)

        // Durante la espera ni el PIN correcto entra.
        clock += 10_000
        assertEquals(VerifyResult.LockedOut(locked.untilMs), engine.verify(pin))

        // Pasada la espera ya se puede intentar.
        clock += 20_001
        assertEquals(VerifyResult.Success, engine.verify(pin))
    }

    @Test
    fun `fallar otra vez tras la espera alarga la siguiente`() = runTest {
        setUpWithPin()
        repeat(5) { engine.verify(wrongPin) }
        clock += 30_001
        val second = engine.verify(wrongPin) as VerifyResult.LockedOut
        assertEquals(clock + 60_000, second.untilMs)
    }

    @Test
    fun `la pregunta de seguridad ignora mayúsculas y tildes`() = runTest {
        engine.setUp(pin, "Apellido de tu abuela", "Pérez")
        assertEquals(VerifyResult.Success, engine.verifyAnswer("  PEREZ "))
        assertTrue(engine.verifyAnswer("González") is VerifyResult.Wrong)
    }

    @Test
    fun `las respuestas erróneas comparten el contador con el PIN`() = runTest {
        setUpWithPin()
        repeat(3) { engine.verifyAnswer("nada") }
        repeat(1) { engine.verify(wrongPin) }
        assertTrue(engine.verify(wrongPin) is VerifyResult.LockedOut)
        assertTrue(engine.verifyAnswer("Luna") is VerifyResult.LockedOut)
    }

    @Test
    fun `cambiar el PIN reinicia los fallos y exige el nuevo`() = runTest {
        setUpWithPin()
        repeat(5) { engine.verify(wrongPin) }

        val newPin = VaultCredential(CredentialType.PIN, "7391")
        engine.changeCredential(newPin)

        assertEquals(0, store.current.failedAttempts)
        assertEquals(VerifyResult.Success, engine.verify(newPin))
        assertTrue(engine.verify(pin) is VerifyResult.Wrong)
    }

    @Test
    fun `cambiar de PIN a patrón`() = runTest {
        setUpWithPin()
        engine.changeCredential(pattern)
        assertEquals(CredentialType.PATTERN, engine.settings.first().credentialType)
        assertEquals(VerifyResult.Success, engine.verify(pattern))
    }

    @Test
    fun `no se aceptan PIN, patrones ni respuestas inválidos`() = runTest {
        listOf(
            VaultCredential(CredentialType.PIN, "12"),
            VaultCredential(CredentialType.PIN, "12ab"),
            VaultCredential(CredentialType.PATTERN, "012"),
            VaultCredential(CredentialType.PATTERN, "0112"),
            VaultCredential(CredentialType.PATTERN, "01239"),
        ).forEach { invalid ->
            try {
                engine.setUp(invalid, "Pregunta", "respuesta")
                fail("Debía rechazar $invalid")
            } catch (_: IllegalArgumentException) {
            }
        }
        try {
            engine.setUp(pin, "Pregunta", " a ")
            fail("Debía rechazar la respuesta corta")
        } catch (_: IllegalArgumentException) {
        }
        try {
            engine.setUp(pin, "  ", "respuesta")
            fail("Debía rechazar la pregunta vacía")
        } catch (_: IllegalArgumentException) {
        }
        assertFalse(engine.settings.first().isSetUp)
    }

    @Test
    fun `los ajustes se guardan y reiniciar lo borra todo`() = runTest {
        setUpWithPin()
        engine.setBiometricEnabled(true)
        engine.setEncryptNewFiles(false)
        engine.setDownloadsToVault(true)
        val settings = engine.settings.first()
        assertTrue(settings.biometricEnabled)
        assertFalse(settings.encryptNewFiles)
        assertTrue(settings.downloadsToVault)

        engine.reset()
        val reset = engine.settings.first()
        assertFalse(reset.isSetUp)
        assertNull(reset.securityQuestion)
        assertFalse(reset.biometricEnabled)
        assertTrue(reset.encryptNewFiles)
    }
}
