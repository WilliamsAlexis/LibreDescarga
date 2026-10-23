package com.libredescarga.core.domain.repository

/**
 * Análisis reciente de un enlace (el JSON con las direcciones directas de cada formato), para que la descarga lo reutilice
 * en lugar de volver a extraer el video, que en YouTube cuesta ~15 s antes de empezar a bajar.
 */
interface AnalysisInfoSource {
    /** El JSON del análisis de [url] si tiene menos de [maxAgeMs]; null si no hay o es más viejo. */
    suspend fun infoJsonFor(url: String, maxAgeMs: Long): String?
}
