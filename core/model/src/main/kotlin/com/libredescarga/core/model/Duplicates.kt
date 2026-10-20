package com.libredescarga.core.model

/** Archivos con exactamente el mismo contenido (mismo tamaño y misma huella SHA-256). */
data class DuplicateGroup(
    val kind: MediaKind,
    /** Al menos dos copias, de la más antigua a la más reciente. */
    val items: List<LibraryMedia>,
) {
    val sizeBytes: Long get() = items.first().sizeBytes

    /** Espacio que se libera si solo se conserva una copia. */
    val reclaimableBytes: Long get() = sizeBytes * (items.size - 1)
}

sealed interface DuplicateScanState {
    /** [total] es 0 mientras se prepara la lista de archivos a comparar. */
    data class Scanning(val done: Int, val total: Int) : DuplicateScanState

    data class Done(val groups: List<DuplicateGroup>) : DuplicateScanState
}
