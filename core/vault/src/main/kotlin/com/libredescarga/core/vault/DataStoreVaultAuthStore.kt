package com.libredescarga.core.vault

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.libredescarga.core.domain.vault.StoredAuth
import com.libredescarga.core.domain.vault.VaultAuthStore
import com.libredescarga.core.model.CredentialType
import com.libredescarga.core.security.HashedSecret
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Qualifier

/** DataStore propio de la carpeta privada (separado de los ajustes generales). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class VaultDataStore

internal class DataStoreVaultAuthStore @Inject constructor(
    @param:VaultDataStore private val dataStore: DataStore<Preferences>,
) : VaultAuthStore {

    override val data: Flow<StoredAuth> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it.toStored() }

    override suspend fun read(): StoredAuth = data.first()

    override suspend fun update(transform: (StoredAuth) -> StoredAuth) {
        dataStore.edit { prefs -> prefs.write(transform(prefs.toStored())) }
    }

    private fun Preferences.toStored() = StoredAuth(
        credentialType = this[Keys.TYPE]?.let { name -> CredentialType.entries.firstOrNull { it.name == name } },
        credentialHash = hashed(Keys.CRED_SALT, Keys.CRED_HASH, Keys.CRED_ITER),
        securityQuestion = this[Keys.QUESTION],
        answerHash = hashed(Keys.ANS_SALT, Keys.ANS_HASH, Keys.ANS_ITER),
        biometricEnabled = this[Keys.BIOMETRIC] ?: false,
        encryptNewFiles = this[Keys.ENCRYPT] ?: true,
        downloadsToVault = this[Keys.DOWNLOADS] ?: false,
        failedAttempts = this[Keys.FAILED] ?: 0,
        lockedUntilMs = this[Keys.LOCKED_UNTIL] ?: 0L,
    )

    private fun Preferences.hashed(
        salt: Preferences.Key<String>,
        hash: Preferences.Key<String>,
        iterations: Preferences.Key<Int>,
    ): HashedSecret? {
        val saltHex = this[salt] ?: return null
        val hashHex = this[hash] ?: return null
        return HashedSecret(saltHex, hashHex, this[iterations] ?: return null)
    }

    private fun MutablePreferences.write(stored: StoredAuth) {
        putOrRemove(Keys.TYPE, stored.credentialType?.name)
        putOrRemove(Keys.CRED_SALT, stored.credentialHash?.saltHex)
        putOrRemove(Keys.CRED_HASH, stored.credentialHash?.hashHex)
        putOrRemove(Keys.CRED_ITER, stored.credentialHash?.iterations)
        putOrRemove(Keys.QUESTION, stored.securityQuestion)
        putOrRemove(Keys.ANS_SALT, stored.answerHash?.saltHex)
        putOrRemove(Keys.ANS_HASH, stored.answerHash?.hashHex)
        putOrRemove(Keys.ANS_ITER, stored.answerHash?.iterations)
        this[Keys.BIOMETRIC] = stored.biometricEnabled
        this[Keys.ENCRYPT] = stored.encryptNewFiles
        this[Keys.DOWNLOADS] = stored.downloadsToVault
        this[Keys.FAILED] = stored.failedAttempts
        this[Keys.LOCKED_UNTIL] = stored.lockedUntilMs
    }

    private fun <T> MutablePreferences.putOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else this[key] = value
    }

    private object Keys {
        val TYPE = stringPreferencesKey("credential_type")
        val CRED_SALT = stringPreferencesKey("credential_salt")
        val CRED_HASH = stringPreferencesKey("credential_hash")
        val CRED_ITER = intPreferencesKey("credential_iterations")
        val QUESTION = stringPreferencesKey("security_question")
        val ANS_SALT = stringPreferencesKey("answer_salt")
        val ANS_HASH = stringPreferencesKey("answer_hash")
        val ANS_ITER = intPreferencesKey("answer_iterations")
        val BIOMETRIC = booleanPreferencesKey("biometric_enabled")
        val ENCRYPT = booleanPreferencesKey("encrypt_new_files")
        val DOWNLOADS = booleanPreferencesKey("downloads_to_vault")
        val FAILED = intPreferencesKey("failed_attempts")
        val LOCKED_UNTIL = longPreferencesKey("locked_until_ms")
    }
}
