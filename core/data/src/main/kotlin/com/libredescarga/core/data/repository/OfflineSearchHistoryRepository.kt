package com.libredescarga.core.data.repository

import com.libredescarga.core.database.dao.SearchHistoryDao
import com.libredescarga.core.database.entity.SearchHistoryEntity
import com.libredescarga.core.domain.repository.SearchHistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Historial de búsquedas guardado solo en el teléfono (se puede borrar). */
internal class OfflineSearchHistoryRepository @Inject constructor(
    private val dao: SearchHistoryDao,
) : SearchHistoryRepository {

    override fun suggestions(prefix: String, limit: Int): Flow<List<String>> =
        dao.observeMatching(escapeLike(prefix.trim()), limit)

    override suspend fun add(query: String) {
        val text = query.trim()
        if (text.isEmpty()) return
        dao.upsert(SearchHistoryEntity(query = text, lastUsedAt = System.currentTimeMillis()))
        dao.trim(MAX_ENTRIES)
    }

    override suspend fun remove(query: String) = dao.delete(query)

    override suspend fun clear() = dao.clear()

    private fun escapeLike(text: String): String =
        text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private companion object {
        const val MAX_ENTRIES = 100
    }
}
