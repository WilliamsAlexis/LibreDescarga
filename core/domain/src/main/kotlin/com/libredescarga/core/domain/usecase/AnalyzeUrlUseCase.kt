package com.libredescarga.core.domain.usecase

import com.libredescarga.core.domain.repository.BlocklistRepository
import com.libredescarga.core.domain.repository.MediaExtractorRepository
import com.libredescarga.core.model.AnalysisResult
import com.libredescarga.core.model.MediaError
import com.libredescarga.core.security.UrlValidator
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
        val valid = when (val result = UrlValidator.validate(rawUrl)) {
            is UrlValidator.Result.Valid -> result
            UrlValidator.Result.ForbiddenScheme -> throw MediaError.ForbiddenScheme()
            UrlValidator.Result.NotAUrl -> throw MediaError.InvalidUrl()
        }
        blocklist.blockedDomainFor(valid.host)?.let { throw MediaError.BlockedSite(it) }
        return extractor.analyze(valid.url)
    }
}
