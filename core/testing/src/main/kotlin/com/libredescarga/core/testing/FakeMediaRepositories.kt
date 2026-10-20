package com.libredescarga.core.testing

import com.libredescarga.core.domain.repository.BlocklistRepository
import com.libredescarga.core.domain.repository.BookmarkRepository
import com.libredescarga.core.domain.repository.BrowserHistoryRepository
import com.libredescarga.core.model.Bookmark
import com.libredescarga.core.model.HistoryEntry
import com.libredescarga.core.domain.repository.DownloadRepository
import com.libredescarga.core.domain.repository.IncomingLinkRepository
import com.libredescarga.core.domain.repository.MediaExtractorRepository
import com.libredescarga.core.domain.repository.SearchHistoryRepository
import com.libredescarga.core.domain.repository.SiteRepository
import com.libredescarga.core.domain.repository.StorageRepository
import com.libredescarga.core.model.AnalysisResult
import com.libredescarga.core.model.DownloadRequest
import com.libredescarga.core.model.DownloadStatus
import com.libredescarga.core.model.DownloadTask
import com.libredescarga.core.model.MediaEntry
import com.libredescarga.core.model.SearchProvider
import com.libredescarga.core.model.MediaError
import com.libredescarga.core.model.Site
import com.libredescarga.core.security.UrlValidator
import com.libredescarga.core.security.DomainMatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeMediaExtractorRepository : MediaExtractorRepository {
    var analysisResult: AnalysisResult = AnalysisResult.Single(TestMedia.info)
    var analysisError: Throwable? = null
    var searchResults: List<MediaEntry> = listOf(TestMedia.entry)
    var searchError: Throwable? = null

    /** Si se asigna, el análisis espera hasta que se complete (para probar el estado de carga). */
    var analysisGate: CompletableDeferred<Unit>? = null

    var lastAnalyzedUrl: String? = null
        private set
    var lastSearch: Pair<String, SearchProvider>? = null
        private set

    override suspend fun analyze(url: String): AnalysisResult {
        lastAnalyzedUrl = url
        analysisGate?.await()
        analysisError?.let { throw it }
        return analysisResult
    }

    override suspend fun search(query: String, provider: SearchProvider, limit: Int): List<MediaEntry> {
        lastSearch = query to provider
        searchError?.let { throw it }
        return searchResults.take(limit)
    }
}

class FakeBlocklistRepository(private val domains: Set<String> = emptySet()) : BlocklistRepository {
    override suspend fun blockedDomainFor(host: String): String? = DomainMatcher.matches(host, domains)

    override suspend fun all(): Set<String> = domains
}

class FakeBrowserHistoryRepository : BrowserHistoryRepository {
    private val state = MutableStateFlow<List<HistoryEntry>>(emptyList())
    override val history: Flow<List<HistoryEntry>> = state
    val current: List<HistoryEntry> get() = state.value

    override suspend fun record(url: String, title: String) = state.update { list ->
        listOf(HistoryEntry(url, title, list.size.toLong())) + list.filterNot { it.url == url }
    }

    override suspend fun remove(url: String) = state.update { list -> list.filterNot { it.url == url } }

    override suspend fun clear() {
        state.value = emptyList()
    }
}

class FakeBookmarkRepository : BookmarkRepository {
    private val state = MutableStateFlow<List<Bookmark>>(emptyList())
    override val bookmarks: Flow<List<Bookmark>> = state

    override suspend fun toggle(url: String, title: String) = state.update { list ->
        if (list.any { it.url == url }) list.filterNot { it.url == url } else list + Bookmark(url, title, list.size.toLong())
    }

    override suspend fun remove(url: String) = state.update { list -> list.filterNot { it.url == url } }
}

class FakeSearchHistoryRepository : SearchHistoryRepository {
    private val items = MutableStateFlow<List<String>>(emptyList())

    override fun suggestions(prefix: String, limit: Int): Flow<List<String>> = items.map { list ->
        list.filter { it.startsWith(prefix, ignoreCase = true) }.take(limit)
    }

    override suspend fun add(query: String) {
        if (query.isBlank()) return
        items.update { listOf(query) + (it - query) }
    }

    override suspend fun remove(query: String) = items.update { it - query }

    override suspend fun clear() {
        items.value = emptyList()
    }
}

class FakeSiteRepository(initial: List<Site> = emptyList()) : SiteRepository {
    private val state = MutableStateFlow(initial)
    override val sites: Flow<List<Site>> = state

    override suspend fun addCustomSite(name: String, url: String) {
        // Igual que el repositorio real: solo enlaces http(s) válidos.
        val valid = UrlValidator.validate(url) as? UrlValidator.Result.Valid ?: throw MediaError.InvalidUrl()
        state.update { list -> list + customSite(list, name, valid.url) }
    }

    private fun customSite(list: List<Site>, name: String, url: String) =
        Site(
            id = (list.maxOfOrNull { it.id } ?: 0) + 1,
            name = name,
            url = url,
            searchProvider = null,
            isCustom = true,
            enabled = true,
            position = list.size,
        )

    override suspend fun removeSite(id: Long) = state.update { list -> list.filterNot { it.id == id } }

    override suspend fun setEnabled(id: Long, enabled: Boolean) =
        state.update { list -> list.map { if (it.id == id) it.copy(enabled = enabled) else it } }

    override suspend fun move(id: Long, up: Boolean) = state.update { list ->
        val index = list.indexOfFirst { it.id == id }
        val target = if (up) index - 1 else index + 1
        if (index < 0 || target !in list.indices) return@update list
        list.toMutableList().apply { add(target, removeAt(index)) }.mapIndexed { position, site -> site.copy(position = position) }
    }
}

/** Buscador de imagen principal de mentira para las pruebas: devuelve la imagen que se le indique (o null). */
class FakePageImageResolver : com.libredescarga.core.domain.repository.PageImageResolver {
    var image: String? = null
    var lastPage: String? = null
        private set

    override suspend fun mainImageOf(pageUrl: String): String? {
        lastPage = pageUrl
        return image
    }
}

/** Consulta de tamaños de mentira para las pruebas: responde con los tamaños que se le indiquen. */
class FakeRemoteFileSizeProbe(var sizes: Map<String, Long> = emptyMap()) :
    com.libredescarga.core.domain.repository.RemoteFileSizeProbe {
    override suspend fun sizeOf(url: String): Long? = sizes[url]
}

class FakeDownloadRepository : DownloadRepository {
    private val state = MutableStateFlow<List<DownloadTask>>(emptyList())
    val enqueued: List<DownloadRequest> get() = state.value.map { it.request }
    val current: List<DownloadTask> get() = state.value

    /** Ids cuyos archivos se pidieron borrar con [remove]. */
    val deletedFiles = mutableListOf<String>()

    override val tasks: Flow<List<DownloadTask>> = state

    fun setTasks(tasks: List<DownloadTask>) {
        state.value = tasks
    }

    private fun updateTask(id: String, transform: (DownloadTask) -> DownloadTask) =
        state.update { list -> list.map { if (it.id == id) transform(it) else it } }

    override suspend fun pause(id: String) = updateTask(id) {
        if (it.status.isActive) it.copy(status = DownloadStatus.PAUSED) else it
    }

    override suspend fun resume(id: String) = updateTask(id) {
        if (it.status == DownloadStatus.PAUSED) it.copy(status = DownloadStatus.QUEUED) else it
    }

    override suspend fun cancel(id: String) = state.update { list -> list.filterNot { it.id == id } }

    override suspend fun retry(id: String) = updateTask(id) {
        if (it.status == DownloadStatus.FAILED) {
            it.copy(status = DownloadStatus.QUEUED, errorMessage = null, progressPercent = 0f)
        } else {
            it
        }
    }

    override suspend fun prioritize(id: String) {
        val top = (state.value.maxOfOrNull { it.priority } ?: 0) + 1
        updateTask(id) { it.copy(priority = top) }
    }

    override suspend fun remove(id: String, deleteFile: Boolean) {
        if (deleteFile) deletedFiles += id
        state.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun pauseAll() = state.value.forEach { pause(it.id) }

    override suspend fun resumeAll() = state.value.forEach { resume(it.id) }

    override suspend fun enqueue(requests: List<DownloadRequest>): List<String> {
        val start = state.value.size
        val created = requests.mapIndexed { index, request ->
            DownloadTask(
                id = "task-${start + index}",
                request = request,
                status = DownloadStatus.QUEUED,
                progressPercent = 0f,
                speedBytesPerSecond = null,
                etaSeconds = null,
                totalBytes = null,
                downloadedBytes = null,
                outputUri = null,
                errorMessage = null,
                priority = 0,
                createdAt = 0,
                completedAt = null,
            )
        }
        state.update { it + created }
        return created.map { it.id }
    }
}

class FakeStorageRepository(
    var available: Long = 10L * 1024 * 1024 * 1024,
    var folderNames: Map<String, String> = emptyMap(),
) : StorageRepository {
    override suspend fun availableBytes(): Long = available

    override suspend fun folderName(treeUri: String): String? = folderNames[treeUri]
}

class FakeIncomingLinkRepository : IncomingLinkRepository {
    private val state = MutableStateFlow<String?>(null)
    override val pendingLink: StateFlow<String?> = state.asStateFlow()

    override fun submit(url: String) {
        state.value = url
    }

    override fun consume() {
        state.value = null
    }
}
