package com.libredescarga.feature.vault

import app.cash.turbine.test
import com.libredescarga.core.domain.vault.VaultAuthEngine
import com.libredescarga.core.model.CredentialType
import com.libredescarga.core.model.FileOperationResult
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.model.VaultCredential
import com.libredescarga.core.testing.FakePlaybackController
import com.libredescarga.core.testing.FakeLibraryRepository
import com.libredescarga.core.testing.FakeVaultAuthStore
import com.libredescarga.core.testing.FakeVaultRepository
import com.libredescarga.core.testing.FakeVaultSession
import com.libredescarga.core.testing.MainDispatcherRule
import com.libredescarga.core.testing.TestLibrary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class VaultViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private var clock = 5_000_000L
    private val store = FakeVaultAuthStore()
    private val auth = VaultAuthEngine(store, now = { clock }, iterations = 200)
    private val session = FakeVaultSession()
    private val vault = FakeVaultRepository()
    private val library = FakeLibraryRepository()
    private val player = FakePlaybackController()
    private lateinit var viewModel: VaultViewModel

    @Before
    fun setUp() {
        viewModel = VaultViewModel(auth, session, vault, library, player)
    }

    private fun TestScope.collectState() =
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect {} }

    private val state get() = viewModel.uiState.value

    private fun enterPin(pin: String) {
        pin.forEach(viewModel::onPinDigit)
        viewModel.onPinSubmit()
    }

    /** Deja la carpeta configurada con el PIN 4821 y bloqueada. */
    private suspend fun configuredAndLocked() {
        auth.setUp(VaultCredential(CredentialType.PIN, "4821"), "¿Mascota?", "Luna")
        session.lock()
    }

    // --- Primera vez -----------------------------------------------------------------------------------------

    @Test
    fun `la primera vez pide crear el acceso`() = runTest {
        collectState()
        assertEquals(VaultMode.SETUP_CREDENTIAL, state.mode)
    }

    @Test
    fun `crear un PIN se escribe dos veces y luego la pregunta`() = runTest {
        collectState()
        enterPin("4821")
        assertEquals(EntryStep.CONFIRM, state.entry.step)
        assertEquals("", state.entry.pin)

        enterPin("4821")
        assertEquals(VaultMode.SETUP_QUESTION, state.mode)

        viewModel.onQuestionChange("¿Cómo se llamaba tu primera mascota?")
        viewModel.onAnswerChange("Luna")
        viewModel.onFinishSetup()

        assertEquals(VaultMode.UNLOCKED, state.mode)
        assertTrue(state.settings.isSetUp)
        assertTrue(session.unlocked.value)
    }

    @Test
    fun `si la confirmación no coincide se vuelve a empezar`() = runTest {
        collectState()
        enterPin("4821")
        enterPin("4822")
        assertEquals(EntryError.Mismatch, state.entry.error)
        assertEquals(EntryStep.ENTER, state.entry.step)
        assertEquals(VaultMode.SETUP_CREDENTIAL, state.mode)
    }

    @Test
    fun `un PIN muy corto no se acepta y uno simple se avisa`() = runTest {
        collectState()
        "123".forEach(viewModel::onPinDigit)
        viewModel.onPinSubmit()
        assertEquals(EntryError.PinTooShort, state.entry.error)

        viewModel.onPinBackspace()
        viewModel.onPinBackspace()
        viewModel.onPinBackspace()
        enterPin("1234")
        assertEquals(EntryStep.CONFIRM, state.entry.step)
        assertTrue(state.entry.weakPin)
    }

    @Test
    fun `el PIN admite como máximo 12 dígitos y solo números`() = runTest {
        collectState()
        "12345678901234".forEach(viewModel::onPinDigit)
        viewModel.onPinDigit('x')
        assertEquals(12, state.entry.pin.length)
    }

    @Test
    fun `crear un patrón`() = runTest {
        collectState()
        viewModel.onTypeSelected(CredentialType.PATTERN)
        viewModel.onPatternEntered(listOf(0, 1))
        assertEquals(EntryError.PatternTooShort, state.entry.error)

        viewModel.onPatternEntered(listOf(0, 1, 2, 5))
        viewModel.onPatternEntered(listOf(0, 1, 2, 5))
        assertEquals(VaultMode.SETUP_QUESTION, state.mode)

        viewModel.onQuestionChange("Pregunta")
        viewModel.onAnswerChange("respuesta")
        viewModel.onFinishSetup()
        assertEquals(CredentialType.PATTERN, state.settings.credentialType)
    }

    @Test
    fun `sin pregunta o con respuesta corta no se termina`() = runTest {
        collectState()
        enterPin("4821")
        enterPin("4821")

        viewModel.onFinishSetup()
        assertEquals(SetupError.QuestionMissing, state.setupError)

        viewModel.onQuestionChange("Pregunta")
        viewModel.onAnswerChange("a")
        viewModel.onFinishSetup()
        assertEquals(SetupError.AnswerTooShort, state.setupError)
        assertFalse(state.settings.isSetUp)
    }

    @Test
    fun `activar la huella desde la configuración inicial`() = runTest {
        collectState()
        enterPin("4821")
        enterPin("4821")
        viewModel.onQuestionChange("Pregunta")
        viewModel.onAnswerChange("respuesta")
        viewModel.onSetupBiometricChange(true)
        viewModel.onFinishSetup()
        assertTrue(state.settings.biometricEnabled)
    }

    // --- Desbloquear ------------------------------------------------------------------------------------------------

    @Test
    fun `bloqueada no muestra archivos y el PIN correcto abre`() = runTest {
        vault.importFile(java.io.File("x"), MediaKind.VIDEO, "video.mp4", "video/mp4", 1000)
        configuredAndLocked()
        collectState()

        assertEquals(VaultMode.LOCKED, state.mode)
        assertTrue(state.items.isEmpty())

        enterPin("4821")
        assertEquals(VaultMode.UNLOCKED, state.mode)
        assertEquals(1, state.items.size)
    }

    @Test
    fun `un PIN incorrecto avisa los intentos y tras cinco hay que esperar`() = runTest {
        configuredAndLocked()
        collectState()

        enterPin("0000")
        assertEquals(EntryError.Wrong(attemptsBeforeLock = 4), state.entry.error)
        assertEquals("", state.entry.pin)

        repeat(3) { enterPin("0000") }
        enterPin("0000")
        val locked = state.entry.error as EntryError.LockedOut
        assertEquals(clock + 30_000, locked.untilMs)

        // Con el PIN correcto tampoco entra durante la espera.
        enterPin("4821")
        assertTrue(state.entry.error is EntryError.LockedOut)
        assertEquals(VaultMode.LOCKED, state.mode)

        clock += 31_000
        enterPin("4821")
        assertEquals(VaultMode.UNLOCKED, state.mode)
    }

    @Test
    fun `la huella solo abre si el usuario la activó`() = runTest {
        configuredAndLocked()
        collectState()

        viewModel.onBiometricSuccess()
        assertEquals(VaultMode.LOCKED, state.mode)

        auth.setBiometricEnabled(true)
        viewModel.onBiometricSuccess()
        assertEquals(VaultMode.UNLOCKED, state.mode)
    }

    @Test
    fun `bloquear desde dentro vuelve a pedir el acceso`() = runTest {
        configuredAndLocked()
        collectState()
        enterPin("4821")

        viewModel.onLockNow()
        assertEquals(VaultMode.LOCKED, state.mode)
    }

    // --- Recuperación -------------------------------------------------------------------------------------------------

    @Test
    fun `olvidé el PIN, respondo la pregunta y creo uno nuevo`() = runTest {
        configuredAndLocked()
        collectState()

        viewModel.onForgotCredential()
        assertEquals(VaultMode.RECOVERY_ANSWER, state.mode)

        viewModel.onRecoveryAnswerChange("Rocky")
        viewModel.onRecoverySubmit()
        assertEquals(EntryError.Wrong(attemptsBeforeLock = 4), state.recoveryError)

        viewModel.onRecoveryAnswerChange("  LUNA ")
        viewModel.onRecoverySubmit()
        assertEquals(VaultMode.RECOVERY_NEW_CREDENTIAL, state.mode)

        enterPin("7391")
        enterPin("7391")
        assertEquals(VaultMode.UNLOCKED, state.mode)

        viewModel.onLockNow()
        enterPin("4821")
        assertTrue(state.entry.error is EntryError.Wrong)
        enterPin("7391")
        assertEquals(VaultMode.UNLOCKED, state.mode)
    }

    @Test
    fun `cancelar la recuperación vuelve a la pantalla de acceso`() = runTest {
        configuredAndLocked()
        collectState()
        viewModel.onForgotCredential()
        viewModel.onRecoveryCancel()
        assertEquals(VaultMode.LOCKED, state.mode)
    }

    @Test
    fun `borrar todo deja la carpeta como nueva`() = runTest {
        vault.importFile(java.io.File("x"), MediaKind.VIDEO, "video.mp4", "video/mp4", 1000)
        configuredAndLocked()
        collectState()
        viewModel.onForgotCredential()

        viewModel.onEraseEverything()

        assertTrue(vault.erased)
        assertEquals(VaultMode.SETUP_CREDENTIAL, state.mode)
        assertEquals(VaultMessage.Erased, state.message)
    }

    // --- Cambiar el acceso y ajustes ---------------------------------------------------------------------------------

    @Test
    fun `cambiar el PIN con la carpeta abierta`() = runTest {
        configuredAndLocked()
        collectState()
        enterPin("4821")

        viewModel.onStartChangeCredential()
        assertEquals(VaultMode.CHANGE_CREDENTIAL, state.mode)
        enterPin("9137")
        enterPin("9137")
        assertEquals(VaultMode.UNLOCKED, state.mode)
        assertEquals(VaultMessage.CredentialChanged, state.message)

        viewModel.onLockNow()
        enterPin("9137")
        assertEquals(VaultMode.UNLOCKED, state.mode)
    }

    @Test
    fun `cancelar el cambio no toca el PIN`() = runTest {
        configuredAndLocked()
        collectState()
        enterPin("4821")
        viewModel.onStartChangeCredential()
        viewModel.onCancelChangeCredential()
        assertEquals(VaultMode.UNLOCKED, state.mode)
    }

    @Test
    fun `los ajustes de cifrado y descargas se guardan`() = runTest {
        configuredAndLocked()
        collectState()
        enterPin("4821")
        viewModel.onEncryptChange(false)
        viewModel.onDownloadsToVaultChange(true)
        viewModel.onBiometricEnabledChange(true)
        assertFalse(state.settings.encryptNewFiles)
        assertTrue(state.settings.downloadsToVault)
        assertTrue(state.settings.biometricEnabled)
    }

    // --- Archivos ----------------------------------------------------------------------------------------------------------

    private suspend fun unlockedWithFiles() {
        configuredAndLocked()
        vault.importFile(java.io.File("a"), MediaKind.VIDEO, "Mi video.mp4", "video/mp4", 60_000)
        vault.importFile(java.io.File("b"), MediaKind.AUDIO, "Cancion.mp3", "audio/mpeg", 180_000)
        vault.importFile(java.io.File("c"), MediaKind.AUDIO, "Otra.m4a", "audio/mp4", 200_000)
        session.unlock()
    }

    @Test
    fun `un video abre el reproductor y la música suena con la cola de audio`() = runTest {
        unlockedWithFiles()
        collectState()
        val video = state.items.first { it.kind == MediaKind.VIDEO }
        val song = state.items.last { it.kind == MediaKind.AUDIO }

        viewModel.events.test {
            viewModel.onItemClick(video)
            assertEquals(VaultEvent.OpenVideo(video), awaitItem())
        }

        viewModel.onItemClick(song)
        val queue = player.state.value.queue
        assertEquals(2, queue.size)
        assertTrue(queue.all { it.uri.startsWith("vault://") })
        assertEquals(song.uri, player.state.value.current?.uri)
    }

    @Test
    fun `renombrar conserva la extensión`() = runTest {
        unlockedWithFiles()
        collectState()
        val video = state.items.first { it.kind == MediaKind.VIDEO }
        viewModel.onRename(video, "  Vacaciones ")
        assertEquals("Vacaciones.mp4", state.items.first { it.id == video.id }.name)

        viewModel.onRename(video, "   ")
        assertEquals("Vacaciones.mp4", state.items.first { it.id == video.id }.name)
    }

    @Test
    fun `borrar un archivo que suena detiene la reproducción`() = runTest {
        unlockedWithFiles()
        collectState()
        val song = state.items.first { it.kind == MediaKind.AUDIO }
        viewModel.onItemClick(song)
        assertTrue(player.state.value.isPlaying)

        viewModel.onDelete(song)

        assertTrue(player.state.value.queue.isEmpty())
        assertTrue(state.items.none { it.id == song.id })
        assertEquals(VaultMessage.Deleted, state.message)
    }

    @Test
    fun `sacar un archivo lo devuelve a la biblioteca`() = runTest {
        unlockedWithFiles()
        collectState()
        val video = state.items.first { it.kind == MediaKind.VIDEO }
        viewModel.onRestore(video)
        assertEquals(VaultMessage.Restored, state.message)
        assertTrue(state.items.none { it.id == video.id })
        assertEquals(1, library.refreshCount)

        vault.restoreResult = false
        val song = state.items.first()
        viewModel.onRestore(song)
        assertEquals(VaultMessage.RestoreFailed, state.message)
        assertTrue(state.items.any { it.id == song.id })
    }

    // --- Añadir desde la biblioteca -----------------------------------------------------------------------------------

    @Test
    fun `mover desde la biblioteca copia y borra los originales`() = runTest {
        configuredAndLocked()
        session.unlock()
        collectState()

        viewModel.onShowAddFromLibrary()
        val choices = state.libraryChoices!!
        assertEquals(5, choices.size)

        val chosen = choices.take(2)
        viewModel.onMoveFromLibrary(chosen)

        assertNull(state.libraryChoices)
        assertEquals(2, state.items.size)
        assertEquals(VaultMessage.Moved(count = 2, failed = 0), state.message)
        assertEquals(1, library.operations.size)
        assertTrue(library.operations.single().startsWith("delete:"))
    }

    @Test
    fun `si algún archivo no se copia, el resto se mueve y se cuenta el fallo`() = runTest {
        configuredAndLocked()
        session.unlock()
        collectState()
        viewModel.onShowAddFromLibrary()

        vault.importError = java.io.IOException("sin espacio")
        viewModel.onMoveFromLibrary(state.libraryChoices!!.take(2))

        assertTrue(state.items.isEmpty())
        assertEquals(VaultMessage.Moved(count = 0, failed = 2), state.message)
        assertTrue(library.operations.isEmpty()) // Nada que borrar: los originales siguen intactos.
    }

    @Test
    fun `si Android pide confirmación para borrar los originales se muestra`() = runTest {
        configuredAndLocked()
        session.unlock()
        collectState()
        viewModel.onShowAddFromLibrary()

        library.nextResult = FileOperationResult.NeedsUserConsent("intent-sender")
        viewModel.onMoveFromLibrary(state.libraryChoices!!.take(1))
        assertEquals("intent-sender", state.pendingConsent)

        viewModel.onConsentResult(granted = true)
        assertNull(state.pendingConsent)
        assertEquals(1, library.refreshCount)
        assertEquals(VaultMessage.Moved(1, 0), state.message)
    }

    @Test
    fun `si se rechaza la confirmación los originales quedan y se avisa`() = runTest {
        configuredAndLocked()
        session.unlock()
        collectState()
        viewModel.onShowAddFromLibrary()
        library.nextResult = FileOperationResult.NeedsUserConsent("intent-sender")
        viewModel.onMoveFromLibrary(state.libraryChoices!!.take(1))

        viewModel.onConsentResult(granted = false)
        assertEquals(VaultMessage.OriginalsKept, state.message)
        assertEquals(1, state.items.size) // La copia sigue en la carpeta privada.
    }

    @Test
    fun `la lista de la biblioteca incluye videos y música`() = runTest {
        configuredAndLocked()
        session.unlock()
        collectState()
        viewModel.onShowAddFromLibrary()
        val kinds = state.libraryChoices!!.map { it.kind }.toSet()
        assertEquals(setOf(MediaKind.VIDEO, MediaKind.AUDIO), kinds)
        assertEquals(TestLibrary.videos.size + TestLibrary.songs.size, library.videos.first().size + library.audio.first().size)
    }
}
