package com.libredescarga.core.downloader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Botones de las notificaciones: pausar, cancelar y reintentar. */
@AndroidEntryPoint
class DownloadActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var controller: DownloadController

    override fun onReceive(context: Context, intent: Intent) {
        val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID) ?: return
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_PAUSE -> controller.pause(downloadId)
                    ACTION_CANCEL -> controller.cancel(downloadId)
                    ACTION_RETRY -> controller.retry(downloadId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.libredescarga.download.PAUSE"
        const val ACTION_CANCEL = "com.libredescarga.download.CANCEL"
        const val ACTION_RETRY = "com.libredescarga.download.RETRY"
        const val EXTRA_DOWNLOAD_ID = "download_id"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
