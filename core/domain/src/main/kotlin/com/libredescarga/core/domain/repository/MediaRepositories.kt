package com.libredescarga.core.domain.repository

import com.libredescarga.core.model.AnalysisResult
import com.libredescarga.core.model.AnalysisUpdate
import com.libredescarga.core.model.DownloadRequest
import com.libredescarga.core.model.DownloadTask
import com.libredescarga.core.model.MediaEntry
import com.libredescarga.core.model.SearchProvider
import com.libredescarga.core.model.Site
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow

/** Motor de extracción (yt-dlp). Lanza [com.libredescarga.core.model.MediaError] si falla. */
interface MediaExtractorRepository {
    suspend fun analyze(url: String): AnalysisResult

    /**
     * Analiza mostrando avances: algunos sitios entregan primero un resultado rápido (parcial) y luego el completo.
     * Por defecto solo emite el resultado completo.
     */
    fun analyzeProgressive(url: String): Flow<AnalysisUpdate> = flow { emit(AnalysisUpdate(analyze(url), isComplete = true)) }

    suspend fun search(query: String, provider: SearchProvider, limit: Int): List<MediaEntry>
}

interface SearchHistoryRepository {
    /** Búsquedas anteriores que empiezan por [prefix] (todas si está vacío), las más recientes primero. */
    fun suggestions(prefix: String, limit: Int): Flow<List<String>>

    suspend fun add(query: String)

    suspend fun remove(query: String)

    suspend fun clear()
}

interface SiteRepository {
    /** Todos los sitios ordenados por posición (predefinidos y marcadores propios). */
    val sites: Flow<List<Site>>

    suspend fun addCustomSite(name: String, url: String)

    suspend fun removeSite(id: Long)

    suspend fun setEnabled(id: Long, enabled: Boolean)

    /** Sube o baja un sitio una posición en la cuadrícula de Inicio. */
    suspend fun move(id: Long, up: Boolean)
}

/** Lista negra de dominios incluida en la app (y actualizable desde GitHub). */
interface BlocklistRepository {
    /** Devuelve el dominio bloqueado que coincide con [host], o null si está permitido. */
    suspend fun blockedDomainFor(host: String): String?

    /** Todos los dominios bloqueados: los incluidos en la app, los de GitHub y los del usuario. */
    suspend fun all(): Set<String>
}

interface DownloadRepository {
    val tasks: Flow<List<DownloadTask>>

    /** Guarda las descargas en la cola y las programa. Devuelve sus identificadores. */
    suspend fun enqueue(requests: List<DownloadRequest>): List<String>

    suspend fun pause(id: String)

    suspend fun resume(id: String)

    /** Detiene la descarga, borra los archivos parciales y la quita de la lista. */
    suspend fun cancel(id: String)

    suspend fun retry(id: String)

    /** Mueve la descarga al principio de la cola. */
    suspend fun prioritize(id: String)

    /** Quita una descarga terminada de la lista y, si se pide, también borra su archivo. */
    suspend fun remove(id: String, deleteFile: Boolean)

    suspend fun pauseAll()

    suspend fun resumeAll()
}

/** Espacio disponible y carpeta de destino de las descargas. */
interface StorageRepository {
    /** Bytes libres donde trabaja el motor (las descargas pasan primero por ahí). */
    suspend fun availableBytes(): Long

    /** Nombre visible de una carpeta elegida con el selector del sistema, o null si ya no es accesible. */
    suspend fun folderName(treeUri: String): String?
}

/** Enlaces recibidos desde otras apps ("Compartir con LibreDescarga"). */
interface IncomingLinkRepository {
    val pendingLink: StateFlow<String?>

    fun submit(url: String)

    fun consume()
}
