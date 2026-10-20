package com.libredescarga.feature.browser

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Una pestaña del navegador. [url] vacío = página de inicio. */
data class BrowserTab(
    val id: String,
    val url: String,
    val title: String,
    val incognito: Boolean,
)

data class TabsState(
    val tabs: List<BrowserTab> = emptyList(),
    val selectedId: String? = null,
) {
    val selected: BrowserTab? get() = tabs.firstOrNull { it.id == selectedId }
}

/**
 * Pestañas abiertas. Viven mientras la app está abierta, así que al volver al
 * navegador siguen ahí. Las pestañas de incógnito se cierran al salir del navegador.
 */
@Singleton
class BrowserTabsStore @Inject constructor() {

    private val _state = MutableStateFlow(TabsState())
    val state: StateFlow<TabsState> = _state.asStateFlow()

    /** Abre [url] en una pestaña nueva; si ya hay una normal con esa dirección, la selecciona. */
    fun open(url: String, incognito: Boolean = false): String {
        val existing = _state.value.tabs.firstOrNull { !it.incognito && !incognito && url.isNotEmpty() && it.url == url }
        if (existing != null) {
            select(existing.id)
            return existing.id
        }
        val tab = BrowserTab(id = UUID.randomUUID().toString(), url = url, title = "", incognito = incognito)
        _state.update { it.copy(tabs = it.tabs + tab, selectedId = tab.id) }
        return tab.id
    }

    /** Garantiza que siempre haya al menos una pestaña. */
    fun ensureTab() {
        if (_state.value.tabs.isEmpty()) open("")
    }

    fun select(id: String) = _state.update { if (it.tabs.any { tab -> tab.id == id }) it.copy(selectedId = id) else it }

    fun update(id: String, transform: (BrowserTab) -> BrowserTab) = _state.update { state ->
        state.copy(tabs = state.tabs.map { if (it.id == id) transform(it) else it })
    }

    /** Cierra la pestaña y selecciona la vecina. Si era la última, queda una pestaña en blanco. */
    fun close(id: String) {
        _state.update { state ->
            val index = state.tabs.indexOfFirst { it.id == id }
            if (index < 0) return@update state
            val remaining = state.tabs.filterNot { it.id == id }
            val selected = if (state.selectedId == id) {
                remaining.getOrNull(index)?.id ?: remaining.lastOrNull()?.id
            } else {
                state.selectedId
            }
            state.copy(tabs = remaining, selectedId = selected)
        }
        ensureTab()
    }

    /** Al salir del navegador: la navegación de incógnito no se conserva. */
    fun closeIncognito() {
        _state.update { state ->
            val remaining = state.tabs.filterNot { it.incognito }
            val selected = state.selectedId?.takeIf { id -> remaining.any { it.id == id } } ?: remaining.lastOrNull()?.id
            state.copy(tabs = remaining, selectedId = selected)
        }
    }

    val hasIncognito: Boolean get() = _state.value.tabs.any { it.incognito }
}
