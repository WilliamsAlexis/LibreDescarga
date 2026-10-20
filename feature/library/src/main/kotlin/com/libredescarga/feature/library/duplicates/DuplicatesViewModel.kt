package com.libredescarga.feature.library.duplicates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.libredescarga.core.domain.repository.DuplicateRepository
import com.libredescarga.core.domain.repository.LibraryRepository
import com.libredescarga.core.model.DuplicateGroup
import com.libredescarga.core.model.DuplicateScanState
import com.libredescarga.core.model.FileOperationResult
import com.libredescarga.core.model.MediaKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DuplicatesMessage { Deleted, DeleteFailed, ScanFailed }

data class DuplicatesUiState(
    val scan: DuplicateScanState = DuplicateScanState.Scanning(done = 0, total = 0),
    /** null = todos los tipos. */
    val filter: MediaKind? = null,
    /** Archivos marcados por el usuario para eliminar. Nunca se marca nada automáticamente. */
    val selected: Set<String> = emptySet(),
    /** Diálogo del sistema (Android 10+) que el usuario debe confirmar para borrar archivos de otras apps. */
    val pendingConsent: Any? = null,
    val message: DuplicatesMessage? = null,
) {
    val groups: List<DuplicateGroup>
        get() = (scan as? DuplicateScanState.Done)?.groups.orEmpty().filter { filter == null || it.kind == filter }

    val selectedBytes: Long
        get() = (scan as? DuplicateScanState.Done)?.groups.orEmpty()
            .flatMap { it.items }.filter { it.uri in selected }.sumOf { it.sizeBytes }
}

/**
 * Lógica de la pantalla de duplicados. Esta pantalla solo borra lo que el usuario marca y confirma,
 * y siempre deja al menos una copia de cada grupo.
 */
@HiltViewModel
class DuplicatesViewModel @Inject constructor(
    private val duplicates: DuplicateRepository,
    private val library: LibraryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DuplicatesUiState())
    val uiState: StateFlow<DuplicatesUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    init {
        scan()
    }

    fun scan() {
        scanJob?.cancel()
        _uiState.update { it.copy(scan = DuplicateScanState.Scanning(0, 0), selected = emptySet()) }
        scanJob = viewModelScope.launch {
            try {
                duplicates.scan().collect { state -> _uiState.update { it.copy(scan = state) } }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(scan = DuplicateScanState.Done(emptyList()), message = DuplicatesMessage.ScanFailed)
                }
            }
        }
    }

    fun onFilterSelected(kind: MediaKind?) = _uiState.update { it.copy(filter = kind) }

    /** Marca o desmarca una copia. No deja marcar todas las copias de un grupo: siempre queda una. */
    fun onToggle(uri: String) = _uiState.update { state ->
        val group = (state.scan as? DuplicateScanState.Done)?.groups?.firstOrNull { g -> g.items.any { it.uri == uri } }
            ?: return@update state
        when {
            uri in state.selected -> state.copy(selected = state.selected - uri)
            group.items.count { it.uri !in state.selected } <= 1 -> state // es la última copia que queda
            else -> state.copy(selected = state.selected + uri)
        }
    }

    /** Borra del dispositivo las copias marcadas. La interfaz ya pidió confirmación al usuario. */
    fun onDeleteSelected() {
        val uris = _uiState.value.selected.toList()
        if (uris.isEmpty()) return
        viewModelScope.launch {
            when (val result = library.delete(uris)) {
                FileOperationResult.Success -> afterDelete()
                FileOperationResult.Failed -> showMessage(DuplicatesMessage.DeleteFailed)
                is FileOperationResult.NeedsUserConsent -> _uiState.update { it.copy(pendingConsent = result.request) }
            }
        }
    }

    /** Respuesta al diálogo del sistema; al aceptar, el sistema ya borró los archivos. */
    fun onConsentResult(granted: Boolean) {
        _uiState.update { it.copy(pendingConsent = null) }
        if (granted) {
            library.refresh()
            afterDelete()
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    private fun afterDelete() {
        showMessage(DuplicatesMessage.Deleted)
        scan()
    }

    private fun showMessage(message: DuplicatesMessage) = _uiState.update { it.copy(message = message) }
}
