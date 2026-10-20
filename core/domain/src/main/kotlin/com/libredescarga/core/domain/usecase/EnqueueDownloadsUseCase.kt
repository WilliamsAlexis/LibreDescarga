package com.libredescarga.core.domain.usecase

import com.libredescarga.core.domain.repository.DownloadRepository
import com.libredescarga.core.model.DownloadRequest
import com.libredescarga.core.security.FileNameSanitizer
import javax.inject.Inject

/** Añade descargas a la cola. Los nombres de archivo se sanean siempre aquí, por seguridad. */
class EnqueueDownloadsUseCase @Inject constructor(
    private val repository: DownloadRepository,
) {
    suspend operator fun invoke(requests: List<DownloadRequest>): Int {
        if (requests.isEmpty()) return 0
        val safe = requests.map { request ->
            request.copy(
                fileName = FileNameSanitizer.sanitize(
                    name = request.fileName,
                    fallback = FileNameSanitizer.sanitize(request.title),
                ),
            )
        }
        return repository.enqueue(safe).size
    }
}
