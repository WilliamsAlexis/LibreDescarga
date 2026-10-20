package com.libredescarga.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

internal tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}

/**
 * Mientras suena algo de la carpeta privada, la pantalla no se puede capturar ni grabar
 * y la vista previa de apps recientes queda en blanco (FLAG_SECURE).
 */
@Composable
internal fun SecureWindowEffect(enabled: Boolean) {
    val activity = LocalContext.current.findHostActivity()
    DisposableEffect(activity, enabled) {
        val window = activity?.window
        if (enabled) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (enabled) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}
