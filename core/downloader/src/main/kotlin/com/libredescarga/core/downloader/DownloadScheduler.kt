package com.libredescarga.core.downloader

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Programa las descargas con WorkManager: sobreviven a cortes de red y a
 * reinicios del teléfono, y respetan la opción "Solo por Wi-Fi".
 */
@Singleton
class DownloadScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val workManager get() = WorkManager.getInstance(context)

    /**
     * @param replace true para cambiar las condiciones de un trabajo aún no iniciado
     * (por ejemplo, al activar "Solo por Wi-Fi").
     */
    fun schedule(downloadId: String, wifiOnly: Boolean, replace: Boolean = false) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresStorageNotLow(true)
            .build()

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_DOWNLOAD_ID to downloadId))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG_DOWNLOAD)
            .build()

        workManager.enqueueUniqueWork(
            workName(downloadId),
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(downloadId: String) {
        workManager.cancelUniqueWork(workName(downloadId))
    }

    /** true si el sistema todavía tiene esta descarga pendiente o en marcha. */
    suspend fun isScheduled(downloadId: String): Boolean =
        workManager.getWorkInfosForUniqueWorkFlow(workName(downloadId)).first().any { !it.state.isFinished }

    companion object {
        const val TAG_DOWNLOAD = "libredescarga-download"
        fun workName(downloadId: String) = "download-$downloadId"
    }
}
