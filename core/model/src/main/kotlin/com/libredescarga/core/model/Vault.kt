package com.libredescarga.core.model

enum class CredentialType { PIN, PATTERN }

/** PIN (solo dígitos) o patrón (números de los puntos del 0 al 8 en el orden en que se tocan). */
data class VaultCredential(val type: CredentialType, val value: String)

/** Un archivo guardado en la carpeta privada. Se reproduce con la dirección [uri]. */
data class VaultItem(
    val id: String,
    val name: String,
    val kind: MediaKind,
    val mimeType: String?,
    val sizeBytes: Long,
    val durationMs: Long,
    val encrypted: Boolean,
    val addedAt: Long,
) {
    val uri: String get() = "$URI_SCHEME://$id"

    companion object {
        const val URI_SCHEME = "vault"

        fun isVaultUri(uri: String?): Boolean = uri?.startsWith("$URI_SCHEME://") == true

        fun idOf(uri: String): String? = uri.takeIf(::isVaultUri)?.removePrefix("$URI_SCHEME://")?.takeIf { it.isNotEmpty() }
    }
}

/** Configuración de la carpeta privada. Todo queda en el teléfono; no hay cuenta ni servidor. */
data class VaultSettings(
    val credentialType: CredentialType? = null,
    val biometricEnabled: Boolean = false,
    /** Los archivos nuevos se cifran con AES-256-GCM (recomendado). */
    val encryptNewFiles: Boolean = true,
    /** Las descargas nuevas van directamente a la carpeta privada. */
    val downloadsToVault: Boolean = false,
    val securityQuestion: String? = null,
) {
    val isSetUp: Boolean get() = credentialType != null
}

sealed interface VerifyResult {
    data object Success : VerifyResult

    /** [attemptsBeforeLock] intentos antes de la próxima espera obligatoria. */
    data class Wrong(val attemptsBeforeLock: Int) : VerifyResult

    /** Demasiados intentos: hay que esperar hasta [untilMs] (milisegundos de reloj). */
    data class LockedOut(val untilMs: Long) : VerifyResult

    data object NotSetUp : VerifyResult
}

/** Resultado de mover un archivo de la biblioteca a la carpeta privada. */
sealed interface MoveToVaultResult {
    data object Moved : MoveToVaultResult

    /** La carpeta privada está bloqueada o sin configurar. */
    data object NotUnlocked : MoveToVaultResult

    /** No se pudo copiar a la carpeta privada; el original no se tocó. */
    data object ImportFailed : MoveToVaultResult

    /** Ya está copiado, pero el original sigue en la galería porque no se pudo borrar. */
    data object CopiedOriginalKept : MoveToVaultResult

    /** Ya está copiado; falta que el usuario confirme el borrado del original. [request] es un IntentSender. */
    data class NeedsConsentToDeleteOriginal(val request: Any) : MoveToVaultResult
}
