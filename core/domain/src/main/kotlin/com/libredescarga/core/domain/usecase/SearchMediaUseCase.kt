package com.libredescarga.core.domain.usecase

import com.libredescarga.core.domain.repository.MediaExtractorRepository
import com.libredescarga.core.domain.repository.SearchHistoryRepository
import com.libredescarga.core.model.MediaEntry
import com.libredescarga.core.model.SearchProvider
import javax.inject.Inject

/** Busca por palabras clave en el sitio elegido y guarda la búsqueda en el historial local. */
class SearchMediaUseCase @Inject constructor(
    private val extractor: MediaExtractorRepository,
    private val history: SearchHistoryRepository,
) {
    suspend operator fun invoke(
        query: String,
        provider: SearchProvider,
        limit: Int = DEFAULT_LIMIT,
    ): List<MediaEntry> {
        val text = query.trim()
        history.add(text)
        return extractor.search(text, provider, limit)
    }

    companion object {
        const val DEFAULT_LIMIT = 20
    }
}
