package com.libredescarga.core.domain.repository

import com.libredescarga.core.model.Bookmark
import com.libredescarga.core.model.HistoryEntry
import kotlinx.coroutines.flow.Flow

interface BrowserHistoryRepository {
    val history: Flow<List<HistoryEntry>>

    /** Registra una visita (la misma dirección solo aparece una vez, con la fecha más reciente). */
    suspend fun record(url: String, title: String)

    suspend fun remove(url: String)

    suspend fun clear()
}

interface BookmarkRepository {
    val bookmarks: Flow<List<Bookmark>>

    /** Añade el marcador, o lo quita si ya existía. */
    suspend fun toggle(url: String, title: String)

    suspend fun remove(url: String)
}
