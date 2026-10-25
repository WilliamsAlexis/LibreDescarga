package com.libredescarga.app.update

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.libredescarga.app.MainActivity
import com.libredescarga.app.R
import com.libredescarga.core.domain.settings.AppUpdateRepository
import com.libredescarga.core.domain.settings.UpdateReminder
import com.libredescarga.core.model.UpdateCheck
import dagger.Binds
import dagger.Module
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Extra del aviso: al tocarlo, la app abre la pantalla "Actualizaciones". */
const val EXTRA_OPEN_UPDATES = "com.libredescarga.app.OPEN_UPDATES"

private const val PREFS = "update_reminder"
private const val KEY_ENABLED = "enabled"
private const val KEY_LAST_NOTIFIED = "last_notified_code"
private const val WORK_NAME = "update_check"
private const val CHANNEL_UPDATES = "app_updates"
private const val NOTIFICATION_ID = 7001

/** Guarda si el aviso está activo y programa (o cancela) la revisión periódica en GitHub. */
@Singleton
class AppUpdateReminder @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : UpdateReminder {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))

    override val enabled: StateFlow<Boolean> = state

    override fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        state.value = value
        apply()
    }

    /** Se llama al abrir la app: deja la revisión programada según la opción elegida. */
    fun apply() {
        val workManager = WorkManager.getInstance(context)
        if (!state.value) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

/** Consulta update.json en GitHub y, si hay una versión nueva que no se avisó antes, muestra la notificación. */
@HiltWorker
internal class UpdateCheckWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val updater: AppUpdateRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val check = try {
            updater.check()
        } catch (_: Exception) {
            return Result.retry()
        }
        if (check !is UpdateCheck.Available) return Result.success()

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val update = check.update
        if (prefs.getLong(KEY_LAST_NOTIFIED, 0L) >= update.versionCode) return Result.success()
        if (notifyAvailable(update.versionName)) prefs.edit().putLong(KEY_LAST_NOTIFIED, update.versionCode).apply()
        return Result.success()
    }

    /** Devuelve false si no hay permiso de notificaciones (se volverá a intentar en la próxima revisión). */
    private fun notifyAvailable(versionName: String): Boolean {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return false

        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_UPDATES, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.update_channel_name))
                .setDescription(context.getString(R.string.update_channel_description))
                .build(),
        )
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_UPDATES, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle(context.getString(R.string.update_notification_title, versionName))
            .setContentText(context.getString(R.string.update_notification_text))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        return try {
            manager.notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class UpdateReminderModule {
    @Binds
    abstract fun bindReminder(impl: AppUpdateReminder): UpdateReminder
}
