package com.libredescarga.core.downloader

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** Notificaciones de progreso (con pausar y cancelar) y de descargas terminadas (abrir y compartir). */
@Singleton
class DownloadNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannels() {
        manager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CHANNEL_PROGRESS, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(context.getString(R.string.downloader_channel_progress))
                    .setDescription(context.getString(R.string.downloader_channel_progress_description))
                    .setShowBadge(false)
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_DONE, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.downloader_channel_done))
                    .setDescription(context.getString(R.string.downloader_channel_done_description))
                    .build(),
            ),
        )
    }

    /**
     * @param percent null mientras se prepara (barra indeterminada).
     * @param speedBytesPerSecond velocidad actual, si se conoce.
     */
    fun foregroundInfo(
        downloadId: String,
        title: String,
        percent: Float?,
        speedBytesPerSecond: Long? = null,
    ): ForegroundInfo {
        val text = when {
            percent == null -> context.getString(R.string.downloader_preparing)
            speedBytesPerSecond != null && speedBytesPerSecond > 0 -> context.getString(
                R.string.downloader_progress_speed,
                percent.roundToInt(),
                Formatter.formatShortFileSize(context, speedBytesPerSecond),
            )
            else -> context.getString(R.string.downloader_progress_percent, percent.roundToInt())
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(100, percent?.roundToInt() ?: 0, percent == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openAppIntent())
            .addAction(0, context.getString(R.string.downloader_action_pause), actionIntent(downloadId, DownloadActionReceiver.ACTION_PAUSE))
            .addAction(0, context.getString(R.string.downloader_action_cancel), actionIntent(downloadId, DownloadActionReceiver.ACTION_CANCEL))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        val id = progressId(downloadId)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
    }

    fun showCompleted(downloadId: String, title: String, uri: Uri, mimeType: String) {
        val open = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val share = Intent(Intent.ACTION_SEND)
            .setType(mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        notify(
            downloadId,
            NotificationCompat.Builder(context, CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_stat_download)
                .setContentTitle(context.getString(R.string.downloader_completed))
                .setContentText(title)
                .setAutoCancel(true)
                .setContentIntent(activityIntent(downloadId, REQUEST_OPEN, Intent.createChooser(open, title)))
                .addAction(0, context.getString(R.string.downloader_action_open), activityIntent(downloadId, REQUEST_OPEN, Intent.createChooser(open, title)))
                .addAction(0, context.getString(R.string.downloader_action_share), activityIntent(downloadId, REQUEST_SHARE, Intent.createChooser(share, title)))
                .build(),
        )
    }

    /** Descarga guardada en la carpeta privada: el aviso no ofrece abrir ni compartir fuera de la app. */
    fun showCompletedPrivate(downloadId: String, title: String) {
        notify(
            downloadId,
            NotificationCompat.Builder(context, CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_stat_download)
                .setContentTitle(context.getString(R.string.downloader_completed_private))
                // El título del archivo no se muestra en la pantalla de bloqueo.
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setContentText(title)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent())
                .build(),
        )
    }

    fun showFailed(downloadId: String, title: String) {
        notify(
            downloadId,
            NotificationCompat.Builder(context, CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_stat_download)
                .setContentTitle(context.getString(R.string.downloader_failed))
                .setContentText(title)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent())
                .addAction(0, context.getString(R.string.downloader_action_retry), actionIntent(downloadId, DownloadActionReceiver.ACTION_RETRY))
                .build(),
        )
    }

    /** Quita los avisos de una descarga (al cancelarla, reintentarla o quitarla de la lista). */
    fun dismiss(downloadId: String) {
        manager.cancel(progressId(downloadId))
        manager.cancel(resultId(downloadId))
    }

    private fun notify(downloadId: String, notification: Notification) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        try {
            manager.notify(resultId(downloadId), notification)
        } catch (_: SecurityException) {
            // El usuario retiró el permiso mientras tanto: no es un error de la descarga.
        }
    }

    private fun actionIntent(downloadId: String, action: String): PendingIntent {
        val intent = Intent(context, DownloadActionReceiver::class.java)
            .setAction(action)
            .putExtra(DownloadActionReceiver.EXTRA_DOWNLOAD_ID, downloadId)
        return PendingIntent.getBroadcast(
            context,
            (downloadId + action).hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun activityIntent(downloadId: String, request: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            context,
            resultId(downloadId) + request,
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun openAppIntent(): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val CHANNEL_PROGRESS = "downloads_progress"
        const val CHANNEL_DONE = "downloads_done"
        private const val REQUEST_OPEN = 0
        private const val REQUEST_SHARE = 1

        private fun baseId(downloadId: String): Int = (downloadId.hashCode() and 0x0FFFFFFF) * 4
        fun progressId(downloadId: String): Int = baseId(downloadId)
        fun resultId(downloadId: String): Int = baseId(downloadId) + 2
    }
}
