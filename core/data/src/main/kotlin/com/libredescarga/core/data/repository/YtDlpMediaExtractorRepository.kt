package com.libredescarga.core.data.repository

import com.libredescarga.core.domain.repository.MediaExtractorRepository
import com.libredescarga.core.engine.InfoParser
import com.libredescarga.core.engine.YtDlpEngine
import com.libredescarga.core.model.AnalysisResult
import com.libredescarga.core.model.MediaEntry
import com.libredescarga.core.model.MediaError
import com.libredescarga.core.model.SearchProvider
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

internal class YtDlpMediaExtractorRepository @Inject constructor(
    private val engine: YtDlpEngine,
) : MediaExtractorRepository {

    override suspend fun analyze(url: String): AnalysisResult = guard {
        InfoParser.parse(engine.fetchInfo(url), url)
    }

    override suspend fun search(query: String, provider: SearchProvider, limit: Int): List<MediaEntry> = guard {
        InfoParser.parseEntries(engine.search(provider, query, limit))
    }

    /** Cualquier error inesperado se entrega como [MediaError] para que la interfaz lo explique. */
    private inline fun <T> guard(block: () -> T): T = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: MediaError) {
        throw error
    } catch (error: Exception) {
        android.util.Log.e("MediaExtractor", "Fallo inesperado al analizar", error)
        throw MediaError.Unknown(error.message)
    }
}
