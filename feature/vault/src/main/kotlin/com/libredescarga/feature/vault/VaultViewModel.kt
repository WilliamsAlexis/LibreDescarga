package com.libredescarga.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.libredescarga.core.domain.player.PlaybackController
import com.libredescarga.core.domain.repository.LibraryRepository
import com.libredescarga.core.domain.vault.VaultAuthRepository
import com.libredescarga.core.domain.vault.VaultRepository
import com.libredescarga.core.domain.vault.VaultSession
import com.libredescarga.core.model.CredentialType
import com.libredescarga.core.model.FileOperationResult
import com.libredescarga.core.model.LibraryMedia
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.model.VaultCredential
import com.libredescarga.core.model.VaultItem
import com.libredescarga.core.model.VaultSettings
import com.libredescarga.core.model.VerifyResult
import com.libredescarga.core.security.CredentialRules
import com.libredescarga.core.security.SecurityAnswer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Lógica de la carpeta privada: crear y verificar el PIN o patrón, recuperarlo con la pregunta de seguridad,
 * y guardar, restaurar, renombrar o borrar archivos. Mientras la bóveda esté bloqueada no entrega la lista de archivos.
 */
@HiltViewModel
class VaultViewModel @Inject constructor(
    private val auth: VaultAuthRepository,
    private val session: VaultSession,
    private val vault: VaultRepository,
    private val library: LibraryRepository,
    private val player: PlaybackController,
) : ViewModel() {

    // Pasos al crear la bóveda: primero el PIN o patrón, luego la pregunta de seguridad.
    private enum class SetupStage { CREDENTIAL, QUESTION }
    // Pasos al recuperar el acceso: responder la pregunta y después crear un PIN nuevo.
    private enum class RecoveryStage { ANSWER, NEW_CREDENTIAL }

    // Estado que solo vive en esta pantalla (lo que se está escribiendo, errores, avisos).
    private data class Local(
        val entry: CredentialEntry = CredentialEntry(),
        val setupStage: SetupStage = SetupStage.CREDENTIAL,
        val setupCredential: VaultCredential? = null,
        val question: String = "",
        val answer: String = "",
        val setupBiometric: Boolean = false,
        val setupError: SetupError? = null,
        val changing: Boolean = false,
        val recoveryStage: RecoveryStage? = null,
        val recoveryError: EntryError? = null,
        val libraryChoices: List<LibraryMedia>? = null,
        val message: VaultMessage? = null,
        val pendingConsent: Any? = null,
    )

    private val local = MutableStateFlow(Local())
    private val _events = Channel<VaultEvent>(Channel.BUFFERED)
    // Eventos de una sola vez, como abrir un video.
    val events: Flow<VaultEvent> = _events.receiveAsFlow()

    // Estado completo que dibuja la pantalla; mezcla los ajustes, si está desbloqueada, los archivos y el estado local.
    val uiState: StateFlow<VaultUiState> = combine(
        auth.settings.map<VaultSettings, VaultSettings?> { it }.onStart { emit(null) },
        session.unlocked,
        vault.items,
        local,
    ) { settings, unlocked, items, local ->
        // Decide qué pantalla toca mostrar: cargando, crear acceso, recuperar, bloqueada o abierta.
        val mode = when {
            settings == null -> VaultMode.LOADING
            !settings.isSetUp ->
                if (local.setupStage == SetupStage.QUESTION) VaultMode.SETUP_QUESTION else VaultMode.SETUP_CREDENTIAL
            local.recoveryStage == RecoveryStage.ANSWER -> VaultMode.RECOVERY_ANSWER
            local.recoveryStage == RecoveryStage.NEW_CREDENTIAL -> VaultMode.RECOVERY_NEW_CREDENTIAL
            !unlocked -> VaultMode.LOCKED
            local.changing -> VaultMode.CHANGE_CREDENTIAL
            else -> VaultMode.UNLOCKED
        }
        VaultUiState(
            mode = mode,
            settings = settings ?: VaultSettings(),
            entry = local.entry,
            question = local.question,
            answer = local.answer,
            setupBiometric = local.setupBiometric,
            setupError = local.setupError,
            recoveryError = local.recoveryError,
            // Sin desbloquear no se expone ni la lista de archivos.
            items = if (unlocked && mode == VaultMode.UNLOCKED) items else emptyList(),
            libraryChoices = local.libraryChoices,
            message = local.message,
            pendingConsent = local.pendingConsent,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaultUiState())

    // Atajo para leer el estado actual.
    private val state get() = uiState.value

    // --- Entrada del PIN o patrón --------------------------------------------------------------------

    // El usuario elige entre PIN y patrón.
    fun onTypeSelected(type: CredentialType) = local.update { it.copy(entry = CredentialEntry(type = type)) }

    // Agrega un dígito al PIN (solo números y hasta el máximo permitido).
    fun onPinDigit(digit: Char) = local.update {
        if (digit !in '0'..'9' || it.entry.pin.length >= CredentialRules.PIN_MAX) it
        else it.copy(entry = it.entry.copy(pin = it.entry.pin + digit, error = null))
    }

    // Borra el último dígito.
    fun onPinBackspace() = local.update { it.copy(entry = it.entry.copy(pin = it.entry.pin.dropLast(1), error = null)) }

    // Confirma el PIN; si es muy corto avisa y no continúa.
    fun onPinSubmit() {
        val pin = local.value.entry.pin
        if (!CredentialRules.isValidPin(pin)) {
            local.update { it.copy(entry = it.entry.copy(error = EntryError.PinTooShort)) }
            return
        }
        submit(VaultCredential(CredentialType.PIN, pin))
    }

    // Confirma el patrón dibujado; si es muy corto avisa y no continúa.
    fun onPatternEntered(nodes: List<Int>) {
        if (!CredentialRules.isValidPattern(nodes)) {
            local.update { it.copy(entry = it.entry.copy(error = EntryError.PatternTooShort)) }
            return
        }
        submit(VaultCredential(CredentialType.PATTERN, CredentialRules.patternToText(nodes)))
    }

    // Según el momento, el PIN o patrón sirve para abrir la bóveda o para crear uno nuevo.
    private fun submit(credential: VaultCredential) {
        viewModelScope.launch {
            when (state.mode) {
                VaultMode.LOCKED -> unlockWith(credential)
                VaultMode.SETUP_CREDENTIAL, VaultMode.CHANGE_CREDENTIAL, VaultMode.RECOVERY_NEW_CREDENTIAL ->
                    createCredential(credential)
                else -> Unit
            }
        }
    }

    // Comprueba el PIN o patrón: si es correcto abre la bóveda; si no, cuenta el intento o bloquea un rato.
    private suspend fun unlockWith(credential: VaultCredential) {
        val error = when (val result = auth.verify(credential)) {
            VerifyResult.Success -> {
                session.unlock()
                local.update { it.copy(entry = CredentialEntry()) }
                return
            }
            is VerifyResult.Wrong -> EntryError.Wrong(result.attemptsBeforeLock)
            is VerifyResult.LockedOut -> EntryError.LockedOut(result.untilMs)
            VerifyResult.NotSetUp -> return
        }
        local.update { it.copy(entry = it.entry.copy(pin = "", error = error)) }
    }

    /** Se pide dos veces: la segunda debe coincidir con la primera. */
    private suspend fun createCredential(credential: VaultCredential) {
        val entry = local.value.entry
        if (entry.step == EntryStep.ENTER) {
            local.update {
                it.copy(
                    setupCredential = credential,
                    entry = entry.copy(
                        step = EntryStep.CONFIRM,
                        pin = "",
                        error = null,
                        weakPin = credential.type == CredentialType.PIN && CredentialRules.isWeakPin(credential.value),
                    ),
                )
            }
            return
        }
        val first = local.value.setupCredential
        if (first != credential) {
            local.update { it.copy(setupCredential = null, entry = CredentialEntry(type = entry.type, error = EntryError.Mismatch)) }
            return
        }
        when (state.mode) {
            VaultMode.SETUP_CREDENTIAL -> local.update {
                it.copy(setupStage = SetupStage.QUESTION, entry = CredentialEntry(type = entry.type))
            }
            VaultMode.CHANGE_CREDENTIAL -> {
                auth.changeCredential(credential)
                local.update { Local(message = VaultMessage.CredentialChanged) }
            }
            VaultMode.RECOVERY_NEW_CREDENTIAL -> {
                auth.changeCredential(credential)
                session.unlock()
                local.update { Local(message = VaultMessage.CredentialChanged) }
            }
            else -> Unit
        }
    }

    /** Volver al primer paso (por ejemplo, para corregir un PIN mal escrito). */
    fun onRestartEntry() = local.update { it.copy(setupCredential = null, entry = CredentialEntry(type = it.entry.type)) }

    // --- Pregunta de seguridad (primera vez) ---------------------------------------------------------------

    // Actualiza el texto de la pregunta de seguridad.
    fun onQuestionChange(question: String) = local.update { it.copy(question = question, setupError = null) }

    // Actualiza el texto de la respuesta.
    fun onAnswerChange(answer: String) = local.update { it.copy(answer = answer, setupError = null) }

    // Activa o desactiva huella/rostro como comodidad al crear la bóveda.
    fun onSetupBiometricChange(enabled: Boolean) = local.update { it.copy(setupBiometric = enabled) }

    // Vuelve al paso del PIN o patrón.
    fun onBackToCredential() = local.update {
        it.copy(setupStage = SetupStage.CREDENTIAL, setupCredential = null, entry = CredentialEntry(type = it.entry.type))
    }

    // Termina la configuración: valida la pregunta y la respuesta, guarda todo y abre la bóveda.
    fun onFinishSetup() {
        val current = local.value
        val credential = current.setupCredential ?: return
        val error = when {
            current.question.isBlank() -> SetupError.QuestionMissing
            !SecurityAnswer.isAcceptable(current.answer) -> SetupError.AnswerTooShort
            else -> null
        }
        if (error != null) {
            local.update { it.copy(setupError = error) }
            return
        }
        viewModelScope.launch {
            auth.setUp(credential, current.question, current.answer)
            if (current.setupBiometric) auth.setBiometricEnabled(true)
            session.unlock()
            local.update { Local() }
        }
    }

    // --- "Olvidé mi PIN" --------------------------------------------------------------------------------------

    // El usuario pulsó "Olvidé mi PIN": empieza la recuperación.
    fun onForgotCredential() = local.update { it.copy(recoveryStage = RecoveryStage.ANSWER, answer = "", recoveryError = null) }

    // Actualiza la respuesta escrita durante la recuperación.
    fun onRecoveryAnswerChange(answer: String) = local.update { it.copy(answer = answer, recoveryError = null) }

    // Comprueba la respuesta de seguridad; si es correcta, deja crear un PIN nuevo.
    fun onRecoverySubmit() {
        val answer = local.value.answer
        viewModelScope.launch {
            when (val result = auth.verifyAnswer(answer)) {
                VerifyResult.Success -> local.update {
                    it.copy(recoveryStage = RecoveryStage.NEW_CREDENTIAL, answer = "", entry = CredentialEntry(), setupCredential = null)
                }
                is VerifyResult.Wrong -> local.update { it.copy(recoveryError = EntryError.Wrong(result.attemptsBeforeLock)) }
                is VerifyResult.LockedOut -> local.update { it.copy(recoveryError = EntryError.LockedOut(result.untilMs)) }
                VerifyResult.NotSetUp -> Unit
            }
        }
    }

    // Cancela la recuperación y vuelve al inicio.
    fun onRecoveryCancel() = local.update { Local() }

    /** Último recurso si se olvidó todo: se borran los archivos y se empieza de cero. */
    fun onEraseEverything() {
        viewModelScope.launch {
            player.stop()
            vault.eraseAll()
            auth.reset()
            session.lock()
            local.update { Local(message = VaultMessage.Erased) }
        }
    }

    // --- Con la carpeta abierta -------------------------------------------------------------------------------------

    /** Huella o rostro correctos: solo si el usuario lo activó antes con su PIN. */
    fun onBiometricSuccess() {
        if (state.settings.biometricEnabled && state.mode == VaultMode.LOCKED) session.unlock()
    }

    // Activa o desactiva la huella/rostro.
    fun onBiometricEnabledChange(enabled: Boolean) {
        viewModelScope.launch { auth.setBiometricEnabled(enabled) }
    }

    // Decide si los archivos nuevos se cifran.
    fun onEncryptChange(enabled: Boolean) {
        viewModelScope.launch { auth.setEncryptNewFiles(enabled) }
    }

    // Decide si las descargas nuevas van directo a la bóveda.
    fun onDownloadsToVaultChange(enabled: Boolean) {
        viewModelScope.launch { auth.setDownloadsToVault(enabled) }
    }

    // Bloquea la bóveda de inmediato.
    fun onLockNow() = session.lock()

    // Empieza el cambio de PIN o patrón.
    fun onStartChangeCredential() = local.update { it.copy(changing = true, entry = CredentialEntry(), setupCredential = null) }

    // Cancela el cambio de PIN o patrón.
    fun onCancelChangeCredential() = local.update { it.copy(changing = false, entry = CredentialEntry(), setupCredential = null) }

    // --- Archivos ----------------------------------------------------------------------------------------------------------

    // Abre un elemento: un video abre el reproductor; un audio empieza a sonar con los demás audios en cola.
    fun onItemClick(item: VaultItem) {
        if (item.kind == MediaKind.VIDEO) {
            viewModelScope.launch { _events.send(VaultEvent.OpenVideo(item)) }
            return
        }
        val audio = state.items.filter { it.kind == MediaKind.AUDIO }
        player.playQueue(audio.map { it.toMedia() }, audio.indexOfFirst { it.id == item.id }.coerceAtLeast(0))
    }

    // Saca el archivo de la bóveda y lo devuelve a la biblioteca.
    fun onRestore(item: VaultItem) {
        viewModelScope.launch {
            local.update { it.copy(message = if (vault.restore(item.id)) VaultMessage.Restored else VaultMessage.RestoreFailed) }
            library.refresh()
        }
    }

    // Borra el archivo para siempre (antes detiene la reproducción si estaba sonando).
    fun onDelete(item: VaultItem) {
        viewModelScope.launch {
            stopIfPlaying(item)
            vault.delete(listOf(item.id))
            local.update { it.copy(message = VaultMessage.Deleted) }
        }
    }

    /** Cambia el nombre conservando la extensión del archivo. */
    fun onRename(item: VaultItem, newBaseName: String) {
        val base = newBaseName.trim()
        if (base.isEmpty()) return
        val extension = item.name.substringAfterLast('.', missingDelimiterValue = "")
        viewModelScope.launch { vault.rename(item.id, if (extension.isEmpty()) base else "$base.$extension") }
    }

    // --- Añadir desde la biblioteca ------------------------------------------------------------------------------

    // Muestra la lista de archivos de la biblioteca para elegir cuáles proteger.
    fun onShowAddFromLibrary() {
        viewModelScope.launch {
            val choices = (library.videos.first() + library.audio.first()).sortedByDescending { it.dateAdded }
            local.update { it.copy(libraryChoices = choices) }
        }
    }

    // Cierra esa lista sin hacer nada.
    fun onDismissAddFromLibrary() = local.update { it.copy(libraryChoices = null) }

    /** Copia cada archivo a la carpeta privada y, después, pide borrar los originales de una sola vez. */
    fun onMoveFromLibrary(selected: List<LibraryMedia>) {
        local.update { it.copy(libraryChoices = null) }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            val imported = mutableListOf<LibraryMedia>()
            var failed = 0
            for (media in selected) {
                try {
                    vault.importFromLibrary(media)
                    imported += media
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    failed++
                }
            }
            if (imported.isEmpty()) {
                local.update { it.copy(message = VaultMessage.Moved(count = 0, failed = failed)) }
                return@launch
            }
            when (val deletion = library.delete(imported.map { it.uri })) {
                FileOperationResult.Success -> local.update { it.copy(message = VaultMessage.Moved(imported.size, failed)) }
                FileOperationResult.Failed -> local.update { it.copy(message = VaultMessage.OriginalsKept) }
                is FileOperationResult.NeedsUserConsent -> local.update {
                    it.copy(pendingConsent = deletion.request, message = VaultMessage.Moved(imported.size, failed))
                }
            }
        }
    }

    /** Respuesta al diálogo del sistema para borrar los originales. */
    fun onConsentResult(granted: Boolean) {
        local.update {
            it.copy(pendingConsent = null, message = if (granted) it.message else VaultMessage.OriginalsKept)
        }
        if (granted) library.refresh()
    }

    // El aviso ya se mostró; se quita para no repetirlo.
    fun onMessageShown() = local.update { it.copy(message = null) }

    // Detiene la reproducción si el archivo que se va a borrar es el que está sonando.
    private fun stopIfPlaying(item: VaultItem) {
        if (player.state.value.current?.uri == item.uri) player.stop()
    }

    // Convierte un elemento de la bóveda al formato que entiende el reproductor.
    private fun VaultItem.toMedia() = LibraryMedia(
        uri = uri,
        kind = kind,
        title = name.substringBeforeLast('.'),
        displayName = name,
        folder = "",
        sizeBytes = sizeBytes,
        durationMs = durationMs,
        dateAdded = addedAt,
        mimeType = mimeType,
    )
}
