package com.libredescarga.core.data.repository

import com.libredescarga.core.database.dao.BookmarkDao
import com.libredescarga.core.database.dao.BrowserHistoryDao
import com.libredescarga.core.database.entity.BookmarkEntity
import com.libredescarga.core.database.entity.BrowserHistoryEntity
import com.libredescarga.core.domain.repository.BookmarkRepository
import com.libredescarga.core.domain.repository.BrowserHistoryRepository
import com.libredescarga.core.model.Bookmark
import com.libredescarga.core.model.HistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Historial del navegador: guarda las últimas 500 páginas visitadas, solo en el teléfono. */
internal class OfflineBrowserHistoryRepository @Inject constructor(
    private val dao: BrowserHistoryDao,
) : BrowserHistoryRepository {

    // Páginas visitadas, de la más reciente a la más antigua.
    override val history: Flow<List<HistoryEntry>> = dao.observe(MAX_ENTRIES)
        .map { list -> list.map { HistoryEntry(it.url, it.title, it.visitedAt) } }

    // Anota una visita y borra las más viejas si se pasa del límite.
    override suspend fun record(url: String, title: String) {
        dao.upsert(BrowserHistoryEntity(url = url, title = title.ifBlank { url }, visitedAt = System.currentTimeMillis()))
        dao.trim(MAX_ENTRIES)
    }

    // Borra una página del historial.
    override suspend fun remove(url: String) = dao.delete(url)

    // Borra todo el historial.
    override suspend fun clear() = dao.clear()

    private companion object {
        // Máximo de páginas guardadas en el historial.
        const val MAX_ENTRIES = 500
    }
}

/** Marcadores del navegador (páginas guardadas por el usuario). */
internal class OfflineBookmarkRepository @Inject constructor(
    private val dao: BookmarkDao,
) : BookmarkRepository {

    // Lista de marcadores.
    override val bookmarks: Flow<List<Bookmark>> = dao.observe()
        .map { list -> list.map { Bookmark(it.url, it.title, it.createdAt) } }

    // Si la página ya era marcador lo quita; si no, lo agrega.
    override suspend fun toggle(url: String, title: String) {
        if (dao.exists(url)) {
            dao.delete(url)
        } else {
            dao.upsert(BookmarkEntity(url = url, title = title.ifBlank { url }, createdAt = System.currentTimeMillis()))
        }
    }

    // Quita un marcador.
    override suspend fun remove(url: String) = dao.delete(url)
}
