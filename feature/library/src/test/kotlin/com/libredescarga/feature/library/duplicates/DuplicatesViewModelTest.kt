package com.libredescarga.feature.library.duplicates

import com.libredescarga.core.domain.repository.DuplicateRepository
import com.libredescarga.core.model.DuplicateGroup
import com.libredescarga.core.model.DuplicateScanState
import com.libredescarga.core.model.FileOperationResult
import com.libredescarga.core.model.LibraryMedia
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.testing.FakeLibraryRepository
import com.libredescarga.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DuplicatesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun copy(id: Int, date: Long) = LibraryMedia(
        uri = "content://media/external/video/media/$id",
        kind = MediaKind.VIDEO,
        title = "video",
        displayName = "video$id.mp4",
        folder = "Movies",
        sizeBytes = 1_000,
        durationMs = 0,
        dateAdded = date,
        mimeType = "video/mp4",
    )

    private val group = DuplicateGroup(MediaKind.VIDEO, listOf(copy(1, 1), copy(2, 2), copy(3, 3)))

    private class FakeDuplicates(private val groups: List<DuplicateGroup>) : DuplicateRepository {
        override fun scan(): Flow<DuplicateScanState> = flowOf(DuplicateScanState.Done(groups))
    }

    private fun viewModel(library: FakeLibraryRepository = FakeLibraryRepository()) =
        DuplicatesViewModel(FakeDuplicates(listOf(group)), library)

    @Test
    fun `no marca nada por su cuenta`() {
        assertTrue(viewModel().uiState.value.selected.isEmpty())
    }

    @Test
    fun `siempre deja al menos una copia sin marcar`() {
        val vm = viewModel()
        group.items.forEach { vm.onToggle(it.uri) }

        assertEquals(2, vm.uiState.value.selected.size)
        assertTrue(group.items.last().uri !in vm.uiState.value.selected)
    }

    @Test
    fun `desmarcar libera una copia para poder marcar otra`() {
        val vm = viewModel()
        vm.onToggle(group.items[0].uri)
        vm.onToggle(group.items[1].uri)
        vm.onToggle(group.items[0].uri)
        vm.onToggle(group.items[2].uri)

        assertEquals(setOf(group.items[1].uri, group.items[2].uri), vm.uiState.value.selected)
    }

    @Test
    fun `solo borra las copias marcadas y vuelve a buscar`() {
        val library = FakeLibraryRepository()
        val vm = viewModel(library)
        vm.onToggle(group.items[0].uri)
        vm.onDeleteSelected()

        assertEquals(listOf("delete:${group.items[0].uri}"), library.operations)
        assertTrue(vm.uiState.value.selected.isEmpty())
        assertEquals(DuplicatesMessage.Deleted, vm.uiState.value.message)
    }

    @Test
    fun `sin copias marcadas no borra nada`() {
        val library = FakeLibraryRepository()
        viewModel(library).onDeleteSelected()

        assertTrue(library.operations.isEmpty())
    }

    @Test
    fun `si el sistema pide confirmacion se espera al usuario y no se pierde la seleccion`() {
        val library = FakeLibraryRepository().apply { nextResult = FileOperationResult.NeedsUserConsent(Any()) }
        val vm = viewModel(library)
        vm.onToggle(group.items[0].uri)
        vm.onDeleteSelected()

        assertTrue(vm.uiState.value.pendingConsent != null)
        assertEquals(setOf(group.items[0].uri), vm.uiState.value.selected)
    }
}
