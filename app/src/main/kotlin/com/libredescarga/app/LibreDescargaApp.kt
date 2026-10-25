package com.libredescarga.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.libredescarga.app.update.AppUpdateReminder
import com.libredescarga.core.downloader.DownloadQueueManager
import com.libredescarga.core.engine.YtDlpEngine
import dagger.hilt.android.HiltAndroidApp
import android.util.Log
import com.libredescarga.core.model.AnalisisPerf
import kotlinx.coroutines.CoroutineExceptionHandler
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

    @Inject
    lateinit var updateReminder: AppUpdateReminder

    // Una excepción suelta en segundo plano (cola de descargas, motor...) no debe cerrar la app.
    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            Log.e(TAG, "Error en segundo plano", error)
        },
    )

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Diagnóstico de tiempos del análisis: se ve en logcat con la etiqueta "AnalisisPerf".
        AnalisisPerf.sink = { Log.i(PERF_TAG, it) }
        // Prepara yt-dlp, Python y FFmpeg en segundo plano para que el primer análisis sea rápido.
        appScope.launch {
            runCatching { engine.initialize() }
            // Arranca el proceso persistente de yt-dlp: los análisis ya no pagan arrancar Python (~2,5 s).
            runCatching { engine.warmUp() }
            // Mantiene el motor al día (como máximo una vez por semana) para evitar errores como el 403 de YouTube.
            runCatching { engine.updateIfStale() }
        }
        // Deja programada la revisión periódica de versiones nuevas en GitHub (si el usuario no la desactivó).
        updateReminder.apply()
        // Retoma la cola de descargas (tras cerrar la app o reiniciar el teléfono).
        downloadQueue.start(appScope)
    }

    private companion object {
        const val TAG = "LibreDescargaApp"
        const val PERF_TAG = "AnalisisPerf"
    }
}
