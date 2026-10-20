package com.libredescarga.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.libredescarga.core.downloader.DownloadQueueManager
import com.libredescarga.core.engine.YtDlpEngine
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LibreDescargaApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var engine: YtDlpEngine

    @Inject
    lateinit var downloadQueue: DownloadQueueManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Prepara yt-dlp, Python y FFmpeg en segundo plano para que el primer análisis sea rápido.
        appScope.launch {
            runCatching { engine.initialize() }
            // Mantiene el motor al día (como máximo una vez por semana) para evitar errores como el 403 de YouTube.
            runCatching { engine.updateIfStale() }
        }
        // Retoma la cola de descargas (tras cerrar la app o reiniciar el teléfono).
        downloadQueue.start(appScope)
    }
}
