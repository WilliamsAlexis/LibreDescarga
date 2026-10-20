package com.libredescarga.feature.vault

import com.libredescarga.core.model.CredentialType
import com.libredescarga.core.model.LibraryMedia
import com.libredescarga.core.model.VaultItem
import com.libredescarga.core.model.VaultSettings

/** Qué pantalla de la carpeta privada corresponde mostrar. */
enum class VaultMode {
    LOADING,

    /** Primera vez: elegir PIN o patrón y confirmarlo. */
    SETUP_CREDENTIAL,

    /** Primera vez: pregunta de seguridad para recuperar el acceso. */
    SETUP_QUESTION,

    LOCKED,
    UNLOCKED,

    /** Con la carpeta abierta: elegir un nuevo PIN o patrón. */
    CHANGE_CREDENTIAL,

    /** "Olvidé mi PIN": responder la pregunta de seguridad. */
    RECOVERY_ANSWER,

    /** Respuesta correcta: elegir un nuevo PIN o patrón. */
    RECOVERY_NEW_CREDENTIAL,
}

enum class EntryStep { ENTER, CONFIRM }

sealed interface EntryError {
    /** PIN de menos de 4 dígitos. */
    data object PinTooShort : EntryError

    /** Patrón de menos de 4 puntos. */
    data object PatternTooShort : EntryError

    /** El PIN o patrón de confirmación no coincide con el primero. */
    data object Mismatch : EntryError

    data class Wrong(val attemptsBeforeLock: Int) : EntryError

    /** Hay que esperar hasta [untilMs] (reloj del sistema). */
    data class LockedOut(val untilMs: Long) : EntryError
}

/** Entrada del PIN o patrón: sirve para desbloquear, crear y cambiar. */
data class CredentialEntry(
    val type: CredentialType = CredentialType.PIN,
    val step: EntryStep = EntryStep.ENTER,
    /** Dígitos escritos hasta ahora (solo PIN). */
    val pin: String = "",
    val error: EntryError? = null,
    /** El PIN elegido es muy simple (0000, 1234...): se avisa pero se permite. */
    val weakPin: Boolean = false,
)

data class VaultUiState(
    val mode: VaultMode = VaultMode.LOADING,
    val settings: VaultSettings = VaultSettings(),
    val entry: CredentialEntry = CredentialEntry(),
    val question: String = "",
    val answer: String = "",
    val setupBiometric: Boolean = false,
    val setupError: SetupError? = null,
    val recoveryError: EntryError? = null,
    val items: List<VaultItem> = emptyList(),
    val libraryChoices: List<LibraryMedia>? = null,
    val message: VaultMessage? = null,
    val pendingConsent: Any? = null,
) {
    /** Tipo de entrada que se muestra: al desbloquear manda el que se eligió al crearlo. */
    val activeType: CredentialType
        get() = if (mode == VaultMode.LOCKED) settings.credentialType ?: CredentialType.PIN else entry.type
}

enum class SetupError { QuestionMissing, AnswerTooShort }

sealed interface VaultMessage {
    data object CredentialChanged : VaultMessage
    data object Restored : VaultMessage
    data object RestoreFailed : VaultMessage
    data object Deleted : VaultMessage
    data object Erased : VaultMessage
    data class Moved(val count: Int, val failed: Int) : VaultMessage
    data object OriginalsKept : VaultMessage
}

sealed interface VaultEvent {
    data class OpenVideo(val item: VaultItem) : VaultEvent
}
