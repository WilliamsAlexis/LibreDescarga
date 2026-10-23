package com.libredescarga.feature.onboarding.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** Permisos que LibreDescarga necesita para funcionar. */
enum class AppPermission(val required: Boolean = true) {
    /** Archivos: guardar y mostrar los videos, la música y las imágenes descargadas. */
    STORAGE,

    /** Avisos de progreso de las descargas (Android 13+). Opcional. */
    NOTIFICATIONS(required = false),

    /** Mostrar la ventana de descarga sobre otras apps. Opcional: Android puede bloquearlo en apps instaladas desde un APK. */
    OVERLAY(required = false),
}

internal object AppPermissions {

    /**
     * Permisos de ejecución del grupo "archivos" según la versión de Android. En Android 14+ se pide también el acceso
     * parcial (el usuario puede elegir "Seleccionar fotos y videos" en lugar de dar acceso a todo).
     */
    fun storagePermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_IMAGES,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        else -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
    }

    /**
     * Android 14+: con acceso parcial a fotos y videos ("Seleccionar fotos y videos") solo queda concedido
     * READ_MEDIA_VISUAL_USER_SELECTED; eso basta para usar la app (las descargas se guardan sin permiso con MediaStore).
     * Sin esta regla, quien eligiera acceso parcial vería la pantalla de permisos para siempre.
     */
    private fun storageGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val audio = context.hasRuntime(Manifest.permission.READ_MEDIA_AUDIO)
            val full = context.hasRuntime(Manifest.permission.READ_MEDIA_VIDEO) &&
                context.hasRuntime(Manifest.permission.READ_MEDIA_IMAGES)
            val partial = context.hasRuntime(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            return audio && (full || partial)
        }
        return storagePermissions().all { context.hasRuntime(it) }
    }

    fun isGranted(context: Context, permission: AppPermission): Boolean = when (permission) {
        AppPermission.STORAGE -> storageGranted(context)
        AppPermission.NOTIFICATIONS ->
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.hasRuntime(Manifest.permission.POST_NOTIFICATIONS)
        AppPermission.OVERLAY ->
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }

    fun missing(context: Context): List<AppPermission> =
        AppPermission.entries.filterNot { isGranted(context, it) }

    /** Pantalla de Android donde se activa "Mostrar sobre otras apps" para esta app. */
    fun overlaySettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

    fun appSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    /**
     * true si el sistema ya no mostrará el cuadro de permiso (el usuario marcó "No volver a preguntar"
     * o lo rechazó varias veces): solo se puede conceder desde los ajustes de Android.
     */
    fun needsSettings(context: Context, permission: AppPermission): Boolean {
        val activity = context.findActivity() ?: return false
        val runtime = when (permission) {
            AppPermission.STORAGE -> storagePermissions().toList()
            AppPermission.NOTIFICATIONS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    listOf(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    emptyList()
                }
            AppPermission.OVERLAY -> emptyList()
        }
        val denied = runtime.filterNot { context.hasRuntime(it) }
        return denied.isNotEmpty() && denied.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
    }

    private fun Context.hasRuntime(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun Context.findActivity(): Activity? {
        var current: Context? = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
}
