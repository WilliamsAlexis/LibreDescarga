package com.libredescarga.core.data.repository

import com.libredescarga.core.domain.settings.EngineRepository
import com.libredescarga.core.engine.EngineUpdateResult
import com.libredescarga.core.engine.YtDlpEngine
import com.libredescarga.core.model.EngineUpdateOutcome
import javax.inject.Inject

/** Puente entre las pantallas y el motor de descarga (yt-dlp): consulta su versión y lo actualiza. */
internal class YtDlpEngineRepository @Inject constructor(
    private val engine: YtDlpEngine,
) : EngineRepository {

    // Versión del motor; devuelve null si no se pudo leer.
    override suspend fun version(): String? = runCatching { engine.version() }.getOrNull()

    // Actualiza el motor y avisa si hubo cambio o ya estaba al día.
    override suspend fun update(): EngineUpdateOutcome = when (engine.update()) {
        EngineUpdateResult.UPDATED -> EngineUpdateOutcome.UPDATED
        EngineUpdateResult.ALREADY_UP_TO_DATE -> EngineUpdateOutcome.ALREADY_UP_TO_DATE
    }
}
