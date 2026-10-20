package com.libredescarga.core.domain.vault

import com.libredescarga.core.model.FileOperationResult
import com.libredescarga.core.model.MoveToVaultResult
import com.libredescarga.core.testing.FakeLibraryRepository
import com.libredescarga.core.testing.FakeVaultRepository
import com.libredescarga.core.testing.FakeVaultSession
import com.libredescarga.core.testing.TestLibrary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveToVaultUseCaseTest {

    private val vault = FakeVaultRepository()
    private val library = FakeLibraryRepository()
    private val session = FakeVaultSession(initiallyUnlocked = true)
    private val moveToVault = MoveToVaultUseCase(vault, library, session)
    private val video = TestLibrary.videos.first()

    @Test
    fun `mover copia a la carpeta privada y borra el original`() = runTest {
        assertEquals(MoveToVaultResult.Moved, moveToVault(video))

        assertEquals(listOf(video.title), vault.current.map { it.name })
        assertTrue(library.videos.first().none { it.uri == video.uri })
    }

    @Test
    fun `con la carpeta bloqueada no se toca nada`() = runTest {
        session.lock()
        assertEquals(MoveToVaultResult.NotUnlocked, moveToVault(video))
        assertTrue(vault.current.isEmpty())
        assertTrue(library.operations.isEmpty())
    }

    @Test
    fun `si la copia falla el original no se borra`() = runTest {
        vault.importError = java.io.IOException("sin espacio")
        assertEquals(MoveToVaultResult.ImportFailed, moveToVault(video))
        assertTrue(library.operations.isEmpty())
        assertTrue(library.videos.first().any { it.uri == video.uri })
    }

    @Test
    fun `si no se puede borrar el original queda copiado y se avisa`() = runTest {
        library.nextResult = FileOperationResult.Failed
        assertEquals(MoveToVaultResult.CopiedOriginalKept, moveToVault(video))
        assertEquals(1, vault.current.size)
    }

    @Test
    fun `si Android pide confirmación se devuelve para mostrarla`() = runTest {
        library.nextResult = FileOperationResult.NeedsUserConsent("intent-sender")
        val result = moveToVault(video) as MoveToVaultResult.NeedsConsentToDeleteOriginal
        assertEquals("intent-sender", result.request)
        assertEquals(1, vault.current.size)
    }
}
