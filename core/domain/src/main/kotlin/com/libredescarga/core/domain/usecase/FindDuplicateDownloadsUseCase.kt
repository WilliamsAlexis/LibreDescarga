package com.libredescarga.core.domain.usecase

import com.libredescarga.core.domain.repository.DownloadRepository
import com.libredescarga.core.model.DownloadRequest
import com.libredescarga.core.model.DownloadStatus
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Avisa de las descargas que ya se hicieron (o ya están en la cola) antes de repetirlas.
 * Dos descargas son la misma si apuntan al mismo contenido (ver [AnalysisCacheKey]) y son del mismo tipo
 * (el audio de un video no cuenta como duplicado de su versión en video). Las fallidas y canceladas no cuentan.
 */
class FindDuplicateDownloadsUseCase @Inject constructor(
    private val repository: DownloadRepository,
) {
    /** Devuelve las peticiones de [requests] que ya existen en la lista de descargas. */
    suspend operator fun invoke(requests: List<DownloadRequest>): List<DownloadRequest> {
        if (requests.isEmpty()) return emptyList()
        val known = repository.tasks.first()
            .filter { it.status == DownloadStatus.COMPLETED || it.status.isActive || it.status == DownloadStatus.PAUSED }
            .mapTo(HashSet()) { keyOf(it.request) }
        return requests.filter { keyOf(it) in known }
    }

    private fun keyOf(request: DownloadRequest) = "${AnalysisCacheKey.of(request.url)}|${request.kind}"
}
