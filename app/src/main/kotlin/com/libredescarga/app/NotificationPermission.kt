package com.libredescarga.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.edit

/**
 * Prepara la petición del permiso de notificaciones (avance y aviso de descarga terminada), que no es obligatorio.
 * Se llama desde onCreate de la actividad y devuelve la función que lanza el cuadro del sistema cuando toque.
 *
 * Si el permiso ya está concedido, la función no hace nada. Como Android deja de mostrar el cuadro tras
 * varios rechazos, aquí se limita a [MAX_REQUESTS] intentos para no insistir.
 */
internal fun ComponentActivity.notificationPermissionRequester(): () -> Unit {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return {}
    val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    return request@{
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) return@request

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val requests = prefs.getInt(KEY_REQUESTS, 0)
        if (requests >= MAX_REQUESTS) return@request
        prefs.edit { putInt(KEY_REQUESTS, requests + 1) }
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private const val PREFS_NAME = "notification_permission"
private const val KEY_REQUESTS = "requests"
private const val MAX_REQUESTS = 3
