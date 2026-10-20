package com.libredescarga.core.data.repository

import com.libredescarga.core.domain.repository.IncomingLinkRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Guarda en memoria un enlace recibido desde otra app (por ejemplo con "Compartir")
 * hasta que la pantalla de inicio lo recoge. No se escribe en disco.
 */
@Singleton
internal class InMemoryIncomingLinkRepository @Inject constructor() : IncomingLinkRepository {

    // Enlace pendiente; null significa que no hay ninguno.
    private val state = MutableStateFlow<String?>(null)

    // Versión de solo lectura para que las pantallas lo observen.
    override val pendingLink: StateFlow<String?> = state.asStateFlow()

    // Guarda un enlace nuevo para que la pantalla de inicio lo analice.
    override fun submit(url: String) {
        state.value = url
    }

    // Marca el enlace como ya atendido para no procesarlo dos veces.
    override fun consume() {
        state.value = null
    }
}
