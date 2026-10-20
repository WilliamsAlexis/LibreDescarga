package com.libredescarga.core.domain.vault

import com.libredescarga.core.model.CredentialType
import com.libredescarga.core.model.VaultCredential
import com.libredescarga.core.model.VaultSettings
import com.libredescarga.core.model.VerifyResult
import com.libredescarga.core.security.CredentialRules
import com.libredescarga.core.security.HashedSecret
import com.libredescarga.core.security.LockoutPolicy
import com.libredescarga.core.security.SecretHasher
import com.libredescarga.core.security.SecurityAnswer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Lo que se guarda en el teléfono sobre el acceso. Nunca contiene el PIN ni la respuesta en claro. */
data class StoredAuth(
    val credentialType: CredentialType? = null,
    val credentialHash: HashedSecret? = null,
    val securityQuestion: String? = null,
    val answerHash: HashedSecret? = null,
    val biometricEnabled: Boolean = false,
    val encryptNewFiles: Boolean = true,
    val downloadsToVault: Boolean = false,
    val failedAttempts: Int = 0,
    val lockedUntilMs: Long = 0,
)

/** Almacenamiento del acceso (DataStore en el teléfono; en memoria en las pruebas). */
interface VaultAuthStore {
    val data: Flow<StoredAuth>

    suspend fun read(): StoredAuth

    suspend fun update(transform: (StoredAuth) -> StoredAuth)
}

/**
 * Lógica de acceso a la carpeta privada: verificación con espera tras varios fallos,
 * recuperación con pregunta de seguridad y ajustes. Sin Android, así que se prueba entera.
 */
class VaultAuthEngine(
    private val store: VaultAuthStore,
    private val now: () -> Long,
    private val iterations: Int = SecretHasher.DEFAULT_ITERATIONS,
) : VaultAuthRepository {

    @Inject
    constructor(store: VaultAuthStore) : this(store, { System.currentTimeMillis() }, SecretHasher.DEFAULT_ITERATIONS)

    override val settings: Flow<VaultSettings> = store.data.map { stored ->
        VaultSettings(
            credentialType = stored.credentialType.takeIf { stored.credentialHash != null },
            biometricEnabled = stored.biometricEnabled,
            encryptNewFiles = stored.encryptNewFiles,
            downloadsToVault = stored.downloadsToVault,
            securityQuestion = stored.securityQuestion,
        )
    }

    override suspend fun setUp(credential: VaultCredential, question: String, answer: String) {
        require(isValid(credential)) { "PIN o patrón no válido" }
        require(question.isNotBlank()) { "Falta la pregunta de seguridad" }
        require(SecurityAnswer.isAcceptable(answer)) { "La respuesta es demasiado corta" }
        store.update {
            it.copy(
                credentialType = credential.type,
                credentialHash = SecretHasher.hash(secretOf(credential), iterations),
                securityQuestion = question.trim(),
                answerHash = SecretHasher.hash(SecurityAnswer.normalize(answer), iterations),
                failedAttempts = 0,
                lockedUntilMs = 0,
            )
        }
    }

    override suspend fun verify(credential: VaultCredential): VerifyResult {
        val stored = store.read()
        val hash = stored.credentialHash ?: return VerifyResult.NotSetUp
        if (stored.credentialType != credential.type) return registerResult(stored, correct = false)
        return registerResult(stored, correct = SecretHasher.verify(secretOf(credential), hash))
    }

    override suspend fun verifyAnswer(answer: String): VerifyResult {
        val stored = store.read()
        val hash = stored.answerHash ?: return VerifyResult.NotSetUp
        return registerResult(stored, correct = SecretHasher.verify(SecurityAnswer.normalize(answer), hash))
    }

    /** Las respuestas incorrectas (PIN o pregunta) comparten el mismo contador, para que no se pueda adivinar por la pregunta. */
    private suspend fun registerResult(stored: StoredAuth, correct: Boolean): VerifyResult {
        val time = now()
        if (time < stored.lockedUntilMs) return VerifyResult.LockedOut(stored.lockedUntilMs)

        if (correct) {
            store.update { it.copy(failedAttempts = 0, lockedUntilMs = 0) }
            return VerifyResult.Success
        }
        val failures = stored.failedAttempts + 1
        val delay = LockoutPolicy.delayMs(failures)
        store.update { it.copy(failedAttempts = failures, lockedUntilMs = if (delay > 0) time + delay else 0) }
        return if (delay > 0) {
            VerifyResult.LockedOut(time + delay)
        } else {
            VerifyResult.Wrong(LockoutPolicy.attemptsBeforeLock(failures))
        }
    }

    override suspend fun changeCredential(credential: VaultCredential) {
        require(isValid(credential)) { "PIN o patrón no válido" }
        store.update {
            check(it.credentialHash != null) { "La carpeta privada no está configurada" }
            it.copy(
                credentialType = credential.type,
                credentialHash = SecretHasher.hash(secretOf(credential), iterations),
                failedAttempts = 0,
                lockedUntilMs = 0,
            )
        }
    }

    override suspend fun setBiometricEnabled(enabled: Boolean) = store.update { it.copy(biometricEnabled = enabled) }

    override suspend fun setEncryptNewFiles(enabled: Boolean) = store.update { it.copy(encryptNewFiles = enabled) }

    override suspend fun setDownloadsToVault(enabled: Boolean) = store.update { it.copy(downloadsToVault = enabled) }

    override suspend fun reset() = store.update { StoredAuth() }

    companion object {
        /** Texto que se resume: distingue PIN de patrón para que "1234" y el patrón 1-2-3-4 no se confundan. */
        fun secretOf(credential: VaultCredential): String = "${credential.type.name.lowercase()}:${credential.value}"

        fun isValid(credential: VaultCredential): Boolean = when (credential.type) {
            CredentialType.PIN -> CredentialRules.isValidPin(credential.value)
            CredentialType.PATTERN -> credential.value.all { it in '0'..'8' } &&
                CredentialRules.isValidPattern(credential.value.map { it - '0' })
        }
    }
}
