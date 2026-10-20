package com.libredescarga.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.libredescarga.core.model.AccentColor
import com.libredescarga.core.model.ThemeMode

/** Material You (colores del fondo de pantalla) solo existe desde Android 12. */
val supportsDynamicColor: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun LibreTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    accent: AccentColor = AccentColor.TEAL,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (dynamicColor && supportsDynamicColor) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        libreColorScheme(accent, darkTheme)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LibreTypography,
        shapes = LibreShapes,
        content = content,
    )
}
