package com.libredescarga.core.domain.vault

import com.libredescarga.core.model.LibraryMedia
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.model.VaultCredential
import com.libredescarga.core.model.VaultItem
import com.libredescarga.core.model.VaultSettings
import com.libredescarga.core.model.VerifyResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** PIN o patrón, pregunta de seguridad y ajustes de la carpeta privada. */
interface VaultAuthRepository {
    val settings: Flow<VaultSettings>

    /** Crea el acceso por primera vez. La respuesta se guarda solo como resumen (hash), nunca en claro. */
    suspend fun setUp(credential: VaultCredential, question: String, answer: String)

    suspend fun verify(credential: VaultCredential): VerifyResult

    /** Recuperación del PIN con la respuesta a la pregunta de seguridad. */
    suspend fun verifyAnswer(answer: String): VerifyResult

    /** Cambia el PIN o el patrón (tras verificar el actual o la respuesta de seguridad). */
    suspend fun changeCredential(credential: VaultCredential)

    suspend fun setBiometricEnabled(enabled: Boolean)

    suspend fun setEncryptNewFiles(enabled: Boolean)

    suspend fun setDownloadsToVault(enabled: Boolean)

    /** Borra el acceso (PIN, pregunta y ajustes). Los archivos se borran aparte con [VaultRepository.eraseAll]. */
    suspend fun reset()
}

/** Estado de bloqueo en memoria: al cerrar la app la carpeta vuelve a quedar bloqueada. */
interface VaultSession {
    val unlocked: StateFlow<Boolean>

    fun unlock()

    fun lock()
}

/** Archivos de la carpeta privada, fuera de la galería y, opcionalmente, cifrados. */
interface VaultRepository {
    val items: Flow<List<VaultItem>>

    /** Copia un archivo de la biblioteca a la carpeta privada (el original no se toca). */
    suspend fun importFromLibrary(media: LibraryMedia): VaultItem

    /** Mueve un archivo descargado a la carpeta privada. Borra [file] al terminar. */
    suspend fun importFile(file: File, kind: MediaKind, name: String, mimeType: String?, durationMs: Long): VaultItem

    /** Devuelve el archivo a Movies/ o Music/ (descifrado) y lo quita de la carpeta privada. */
    suspend fun restore(id: String): Boolean

    suspend fun delete(ids: List<String>)

    suspend fun rename(id: String, name: String)

    /** Borra todos los archivos de la carpeta privada. */
    suspend fun eraseAll()
}
