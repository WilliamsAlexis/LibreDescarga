package com.libredescarga.core.model

/** Preferencias del usuario. Todo se guarda solo en el teléfono. */
data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = false,
    val accentColor: AccentColor = AccentColor.TEAL,
    val onboardingCompleted: Boolean = false,
    val acceptedTermsVersion: Int = 0,
    val clipboardDetection: Boolean = true,
    val wifiOnly: Boolean = false,
    val maxConcurrentDownloads: Int = DEFAULT_CONCURRENT_DOWNLOADS,
    /** Fragmentos descargados en paralelo dentro de cada descarga (multihilo). */
    val concurrentFragments: Int = DEFAULT_FRAGMENTS,
    /** Límite de velocidad por descarga en KB/s; 0 = sin límite. */
    val speedLimitKbps: Int = 0,
    /** Carpeta elegida con el Storage Access Framework (puede estar en la tarjeta SD), o null = Movies/ y Music/. */
    val downloadTreeUri: String? = null,
    val defaultVideoQuality: VideoQuality = VideoQuality.ASK,
    val defaultAudioFormat: AudioFormat = AudioFormat.ASK,
    val completedDownloadsCount: Int = 0,
    val donationPromptAnswered: Boolean = false,
    val hideDonationReminders: Boolean = false,
    /** Sitios que el usuario decidió bloquear (se suman a la lista de derechos de autor). */
    val userBlockedDomains: Set<String> = emptySet(),
) {
    companion object {
        const val MIN_CONCURRENT_DOWNLOADS = 1
        const val MAX_CONCURRENT_DOWNLOADS = 5
        const val DEFAULT_CONCURRENT_DOWNLOADS = 2
        const val MIN_FRAGMENTS = 1
        const val MAX_FRAGMENTS = 8
        const val DEFAULT_FRAGMENTS = 4
        val SPEED_LIMIT_OPTIONS_KBPS = listOf(0, 500, 1_000, 2_000, 5_000, 10_000)
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Colores de acento propios de LibreDescarga. */
/** Colores de acento disponibles. Los nombres se guardan en los ajustes y en las copias de seguridad: no cambiarlos. */
enum class AccentColor { TEAL, CORAL, INDIGO, AMBER, ROSE, GREEN, BLUE, PURPLE, RED, ORANGE, CYAN, PINK, BROWN, SLATE, YELLOW, LIME, SKY, LAVENDER, GRAPHITE }

/** Calidad de video para la descarga rápida. [ASK] muestra siempre la hoja de formatos. */
enum class VideoQuality(val maxHeight: Int?) {
    ASK(null),
    P360(360),
    P480(480),
    P720(720),
    P1080(1080),
    P1440(1440),
    P2160(2160),
    BEST(null),
}

/** Formato de audio para la descarga rápida. [ASK] muestra siempre la hoja de formatos. */
enum class AudioFormat(val extension: String?, val bitrateKbps: Int?) {
    ASK(null, null),
    MP3_128("mp3", 128),
    MP3_192("mp3", 192),
    MP3_320("mp3", 320),
    M4A("m4a", null),
    OPUS("opus", null),
}
