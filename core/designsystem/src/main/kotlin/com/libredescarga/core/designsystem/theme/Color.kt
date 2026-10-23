package com.libredescarga.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.libredescarga.core.model.AccentColor

/*
 * Paleta propia de LibreDescarga: verde azulado ("libre", aire, movimiento)
 * con un ámbar cálido como acento terciario. Todos los pares de texto/fondo
 * cumplen contraste AA (≥ 4,5:1).
 */

private val LightBase = lightColorScheme(
    primary = Color(0xFF006A60),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF74F8E5),
    onPrimaryContainer = Color(0xFF00201C),
    inversePrimary = Color(0xFF53DBC9),
    secondary = Color(0xFF4A635F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E2),
    onSecondaryContainer = Color(0xFF05201C),
    tertiary = Color(0xFF7A5900),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDEA3),
    onTertiaryContainer = Color(0xFF261900),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6FBF9),
    onBackground = Color(0xFF171D1C),
    surface = Color(0xFFF6FBF9),
    onSurface = Color(0xFF171D1C),
    surfaceVariant = Color(0xFFDAE5E1),
    onSurfaceVariant = Color(0xFF3F4947),
    surfaceTint = Color(0xFF006A60),
    inverseSurface = Color(0xFF2B3230),
    inverseOnSurface = Color(0xFFECF2EF),
    outline = Color(0xFF6F7977),
    outlineVariant = Color(0xFFBEC9C6),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF6FBF9),
    surfaceDim = Color(0xFFD5DBD9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5F3),
    surfaceContainer = Color(0xFFE9EFED),
    surfaceContainerHigh = Color(0xFFE4EAE7),
    surfaceContainerHighest = Color(0xFFDEE4E1),
)

private val DarkBase = darkColorScheme(
    primary = Color(0xFF53DBC9),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Color(0xFF74F8E5),
    inversePrimary = Color(0xFF006A60),
    secondary = Color(0xFFB1CCC6),
    onSecondary = Color(0xFF1C3531),
    secondaryContainer = Color(0xFF334B47),
    onSecondaryContainer = Color(0xFFCCE8E2),
    tertiary = Color(0xFFF5BF48),
    onTertiary = Color(0xFF402D00),
    tertiaryContainer = Color(0xFF5C4300),
    onTertiaryContainer = Color(0xFFFFDEA3),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1514),
    onBackground = Color(0xFFDDE4E1),
    surface = Color(0xFF0E1514),
    onSurface = Color(0xFFDDE4E1),
    surfaceVariant = Color(0xFF3F4947),
    onSurfaceVariant = Color(0xFFBEC9C6),
    surfaceTint = Color(0xFF53DBC9),
    inverseSurface = Color(0xFFDDE4E1),
    inverseOnSurface = Color(0xFF2B3230),
    outline = Color(0xFF899390),
    outlineVariant = Color(0xFF3F4947),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF343B39),
    surfaceDim = Color(0xFF0E1514),
    surfaceContainerLowest = Color(0xFF090F0E),
    surfaceContainerLow = Color(0xFF171D1C),
    surfaceContainer = Color(0xFF1B2120),
    surfaceContainerHigh = Color(0xFF252B2A),
    surfaceContainerHighest = Color(0xFF303635),
)

/** Tonos del color principal para un acento, en modo claro y oscuro. */
private data class AccentTones(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
)

private data class AccentPalette(val light: AccentTones, val dark: AccentTones)

private val TealPalette = AccentPalette(
    light = AccentTones(Color(0xFF006A60), Color.White, Color(0xFF74F8E5), Color(0xFF00201C)),
    dark = AccentTones(Color(0xFF53DBC9), Color(0xFF003731), Color(0xFF005048), Color(0xFF74F8E5)),
)

private val CoralPalette = AccentPalette(
    light = AccentTones(Color(0xFFED6749), Color(0xFF180A07), Color(0xFFFBE1DB), Color(0xFF2B130D)),
    dark = AccentTones(Color(0xFFFF6F4F), Color(0xFF1F0D09), Color(0xFF612A1E), Color(0xFFFFDFD8)),
)

private val IndigoPalette = AccentPalette(
    light = AccentTones(Color(0xFF3F51B5), Color.White, Color(0xFFD9DCF0), Color(0xFF0B0F21)),
    dark = AccentTones(Color(0xFF717EC8), Color(0xFF0E0F18), Color(0xFF2B304C), Color(0xFFE0E3F3)),
)

private val AmberPalette = AccentPalette(
    light = AccentTones(Color(0xFFBF8600), Color(0xFF130D00), Color(0xFFF2E7CC), Color(0xFF221800)),
    dark = AccentTones(Color(0xFFFFB300), Color(0xFF1F1500), Color(0xFF614400), Color(0xFFFFEEC7)),
)

private val RosePalette = AccentPalette(
    light = AccentTones(Color(0xFFCB7793), Color(0xFF140C0F), Color(0xFFF5E4E9), Color(0xFF25151A)),
    dark = AccentTones(Color(0xFFF48FB1), Color(0xFF1D1115), Color(0xFF5D3643), Color(0xFFFDE6EE)),
)

private val GreenPalette = AccentPalette(
    light = AccentTones(Color(0xFF43A047), Color(0xFF071007), Color(0xFFD9ECDA), Color(0xFF0C1D0D)),
    dark = AccentTones(Color(0xFF43A047), Color(0xFF081309), Color(0xFF193D1B), Color(0xFFD6EAD7)),
)

private val BluePalette = AccentPalette(
    light = AccentTones(Color(0xFF1A76C7), Color.White, Color(0xFFD1E4F4), Color(0xFF051524)),
    dark = AccentTones(Color(0xFF1E88E5), Color(0xFF04101B), Color(0xFF0B3457), Color(0xFFCEE5F9)),
)

private val PurplePalette = AccentPalette(
    light = AccentTones(Color(0xFF8E24AA), Color.White, Color(0xFFE8D3EE), Color(0xFF1A061F)),
    dark = AccentTones(Color(0xFFB066C4), Color(0xFF150C18), Color(0xFF43274A), Color(0xFFEEDDF2)),
)

private val RedPalette = AccentPalette(
    light = AccentTones(Color(0xFFDA3632), Color.White, Color(0xFFF8D7D6), Color(0xFF270A09)),
    dark = AccentTones(Color(0xFFE74B47), Color(0xFF1C0909), Color(0xFF581C1B), Color(0xFFFAD7D7)),
)

private val OrangePalette = AccentPalette(
    light = AccentTones(Color(0xFFD87800), Color(0xFF160C00), Color(0xFFF7E4CC), Color(0xFF271600)),
    dark = AccentTones(Color(0xFFFB8C00), Color(0xFF1E1100), Color(0xFF5F3500), Color(0xFFFEE6C7)),
)

private val CyanPalette = AccentPalette(
    light = AccentTones(Color(0xFF00A0B3), Color(0xFF001012), Color(0xFFCCECF0), Color(0xFF001D20)),
    dark = AccentTones(Color(0xFF00ACC1), Color(0xFF001517), Color(0xFF004149), Color(0xFFC7EDF1)),
)

private val PinkPalette = AccentPalette(
    light = AccentTones(Color(0xFFDB1C84), Color.White, Color(0xFFF8D2E6), Color(0xFF270518)),
    dark = AccentTones(Color(0xFFEB3498), Color(0xFF1C0612), Color(0xFF59143A), Color(0xFFFBD2E8)),
)

private val BrownPalette = AccentPalette(
    light = AccentTones(Color(0xFF795548), Color.White, Color(0xFFE4DDDA), Color(0xFF160F0D)),
    dark = AccentTones(Color(0xFF987C72), Color(0xFF120F0E), Color(0xFF3A2F2B), Color(0xFFE8E2E0)),
)

private val SlatePalette = AccentPalette(
    light = AccentTones(Color(0xFF5D7987), Color.White, Color(0xFFDFE4E7), Color(0xFF111618)),
    dark = AccentTones(Color(0xFF6B8693), Color(0xFF0D1012), Color(0xFF293338), Color(0xFFDEE4E7)),
)

private val YellowPalette = AccentPalette(
    light = AccentTones(Color(0xFFA78F23), Color(0xFF110E04), Color(0xFFEDE9D3), Color(0xFF1E1A06)),
    dark = AccentTones(Color(0xFFFDD835), Color(0xFF1E1A06), Color(0xFF605214), Color(0xFFFFF6D3)),
)

private val LimePalette = AccentPalette(
    light = AccentTones(Color(0xFF779B4D), Color(0xFF0C1008), Color(0xFFE4EBDB), Color(0xFF151C0E)),
    dark = AccentTones(Color(0xFF9CCC65), Color(0xFF13180C), Color(0xFF3B4E26), Color(0xFFE9F4DD)),
)

private val SkyPalette = AccentPalette(
    light = AccentTones(Color(0xFF3E9AC3), Color(0xFF060F14), Color(0xFFD8EBF3), Color(0xFF0B1C23)),
    dark = AccentTones(Color(0xFF4FC3F7), Color(0xFF09171E), Color(0xFF1E4A5E), Color(0xFFD8F2FD)),
)

private val LavenderPalette = AccentPalette(
    light = AccentTones(Color(0xFF9A87BC), Color(0xFF0F0E13), Color(0xFFEBE7F2), Color(0xFF1C1822)),
    dark = AccentTones(Color(0xFFB39DDB), Color(0xFF15131A), Color(0xFF443C53), Color(0xFFEEE9F7)),
)

private val GraphitePalette = AccentPalette(
    light = AccentTones(Color(0xFF616161), Color.White, Color(0xFFDFDFDF), Color(0xFF111111)),
    dark = AccentTones(Color(0xFF828282), Color(0xFF101010), Color(0xFF313131), Color(0xFFE4E4E4)),
)

private fun AccentColor.palette(): AccentPalette = when (this) {
    AccentColor.TEAL -> TealPalette
    AccentColor.CORAL -> CoralPalette
    AccentColor.INDIGO -> IndigoPalette
    AccentColor.AMBER -> AmberPalette
    AccentColor.ROSE -> RosePalette
    AccentColor.GREEN -> GreenPalette
    AccentColor.BLUE -> BluePalette
    AccentColor.PURPLE -> PurplePalette
    AccentColor.RED -> RedPalette
    AccentColor.ORANGE -> OrangePalette
    AccentColor.CYAN -> CyanPalette
    AccentColor.PINK -> PinkPalette
    AccentColor.BROWN -> BrownPalette
    AccentColor.SLATE -> SlatePalette
    AccentColor.YELLOW -> YellowPalette
    AccentColor.LIME -> LimePalette
    AccentColor.SKY -> SkyPalette
    AccentColor.LAVENDER -> LavenderPalette
    AccentColor.GRAPHITE -> GraphitePalette
}

/**
 * Color vivo que representa a cada acento (el que la gente espera ver con ese nombre).
 * Es el mismo en modo claro y oscuro; dentro de la app se usan tonos ajustados para que el texto se lea bien.
 */
private fun AccentColor.vivid(): Color = when (this) {
    AccentColor.TEAL -> Color(0xFF009688)
AccentColor.CORAL -> Color(0xFFFF6F4F)
    AccentColor.INDIGO -> Color(0xFF3F51B5)
    AccentColor.AMBER -> Color(0xFFFFB300)
    AccentColor.ROSE -> Color(0xFFF48FB1)
    AccentColor.GREEN -> Color(0xFF43A047)
    AccentColor.BLUE -> Color(0xFF1E88E5)
    AccentColor.PURPLE -> Color(0xFF8E24AA)
    AccentColor.RED -> Color(0xFFE53935)
    AccentColor.ORANGE -> Color(0xFFFB8C00)
    AccentColor.CYAN -> Color(0xFF00ACC1)
    AccentColor.PINK -> Color(0xFFE91E8C)
    AccentColor.BROWN -> Color(0xFF795548)
    AccentColor.SLATE -> Color(0xFF607D8B)
    AccentColor.YELLOW -> Color(0xFFFDD835)
    AccentColor.LIME -> Color(0xFF9CCC65)
    AccentColor.SKY -> Color(0xFF4FC3F7)
    AccentColor.LAVENDER -> Color(0xFFB39DDB)
    AccentColor.GRAPHITE -> Color(0xFF616161)
}

/** Color de muestra para el selector de acento en Ajustes: siempre el color vivo, sin importar el modo. */
@Suppress("UNUSED_PARAMETER")
fun AccentColor.swatch(darkTheme: Boolean): Color = vivid()

/** Esquema de color de LibreDescarga para el acento y el modo indicados. */
fun libreColorScheme(accent: AccentColor, darkTheme: Boolean): ColorScheme {
    val palette = accent.palette()
    val tones = if (darkTheme) palette.dark else palette.light
    val inverse = if (darkTheme) palette.light.primary else palette.dark.primary
    val base = if (darkTheme) DarkBase else LightBase

    val withAccent = base.copy(
        primary = tones.primary,
        onPrimary = tones.onPrimary,
        primaryContainer = tones.primaryContainer,
        onPrimaryContainer = tones.onPrimaryContainer,
        inversePrimary = inverse,
        surfaceTint = tones.primary,
    )

    // Con acento ámbar, el terciario pasa a verde azulado para no repetir color.
    if (accent != AccentColor.AMBER) return withAccent
    val teal = if (darkTheme) TealPalette.dark else TealPalette.light
    return withAccent.copy(
        tertiary = teal.primary,
        onTertiary = teal.onPrimary,
        tertiaryContainer = teal.primaryContainer,
        onTertiaryContainer = teal.onPrimaryContainer,
    )
}
