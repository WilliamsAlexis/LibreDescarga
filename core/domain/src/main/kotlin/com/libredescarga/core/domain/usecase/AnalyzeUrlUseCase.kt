package com.libredescarga.core.domain.usecase

import com.libredescarga.core.domain.repository.BlocklistRepository
import com.libredescarga.core.domain.repository.MediaExtractorRepository
import com.libredescarga.core.model.AnalisisPerf
import com.libredescarga.core.model.AnalysisResult
import com.libredescarga.core.model.AnalysisUpdate
import com.libredescarga.core.model.MediaError
import com.libredescarga.core.security.UrlValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Valida el enlace, comprueba la lista negra y lo analiza con el motor.
 * Lanza [MediaError] con el motivo si no se puede descargar.
 */
class AnalyzeUrlUseCase @Inject constructor(
    private val blocklist: BlocklistRepository,
    private val extractor: MediaExtractorRepository,
) {
    suspend operator fun invoke(rawUrl: String): AnalysisResult {
        val valid = AnalisisPerf.measure("plataforma: validar enlace y lista negra", AnalisisPerf.hostOf(rawUrl)) {
            validate(rawUrl)
        }
        return AnalisisPerf.measure("extractor: analizar (total yt-dlp + parseo)", valid.host) {
            extractor.analyze(valid.url)
        }
    }

    /** Como [invoke], pero con avances: primero un resultado rápido (si el sitio lo permite) y luego el completo. */
    fun stream(rawUrl: String): Flow<AnalysisUpdate> = flow {
        val valid = AnalisisPerf.measure("plataforma: validar enlace y lista negra", AnalisisPerf.hostOf(rawUrl)) {
            validate(rawUrl)
        }
        emitAll(extractor.analyzeProgressive(valid.url))
    }

    private suspend fun validate(rawUrl: String): UrlValidator.Result.Valid {
        val valid = when (val result = UrlValidator.validate(rawUrl)) {
            is UrlValidator.Result.Valid -> result
            UrlValidator.Result.ForbiddenScheme -> throw MediaError.ForbiddenScheme()
            UrlValidator.Result.NotAUrl -> throw MediaError.InvalidUrl()
        }
        blocklist.blockedDomainFor(valid.host)?.let { throw MediaError.BlockedSite(it) }
        return valid
    }
}
