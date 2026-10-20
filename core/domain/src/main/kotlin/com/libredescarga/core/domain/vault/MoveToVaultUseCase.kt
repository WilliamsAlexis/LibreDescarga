package com.libredescarga.core.domain.vault

import com.libredescarga.core.domain.repository.LibraryRepository
import com.libredescarga.core.model.FileOperationResult
import com.libredescarga.core.model.LibraryMedia
import com.libredescarga.core.model.MoveToVaultResult
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * Mueve un archivo de la biblioteca a la carpeta privada: primero lo copia (cifrado si está activado)
 * y solo si la copia salió bien borra el original de la galería. Nunca se pierde un archivo.
 */
class MoveToVaultUseCase @Inject constructor(
    private val vault: VaultRepository,
    private val library: LibraryRepository,
    private val session: VaultSession,
) {
    suspend operator fun invoke(media: LibraryMedia): MoveToVaultResult {
        if (!session.unlocked.value) return MoveToVaultResult.NotUnlocked

        try {
            vault.importFromLibrary(media)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return MoveToVaultResult.ImportFailed
        }

        return when (val deletion = library.delete(listOf(media.uri))) {
            FileOperationResult.Success -> MoveToVaultResult.Moved
            FileOperationResult.Failed -> MoveToVaultResult.CopiedOriginalKept
            is FileOperationResult.NeedsUserConsent -> MoveToVaultResult.NeedsConsentToDeleteOriginal(deletion.request)
        }
    }
}
