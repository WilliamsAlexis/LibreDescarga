package com.libredescarga.core.ui

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import java.text.NumberFormat
import java.util.Locale

/** "4:05" o "1:02:05". */
fun formatDuration(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, secs)
    }
}

/** Tamaño legible en el idioma del usuario ("85 MB"). */
@Composable
fun formatBytes(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)

/** "≈ 85 MB" o "Tamaño desconocido". */
@Composable
fun estimatedSizeLabel(bytes: Long?): String =
    if (bytes == null || bytes <= 0) {
        stringResource(R.string.core_ui_size_unknown)
    } else {
        stringResource(R.string.core_ui_size_estimated, formatBytes(bytes))
    }

@Composable
fun viewCountLabel(views: Long): String =
    stringResource(R.string.core_ui_views, NumberFormat.getIntegerInstance().format(views))
