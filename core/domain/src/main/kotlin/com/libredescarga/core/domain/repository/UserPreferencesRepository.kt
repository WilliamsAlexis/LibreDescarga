package com.libredescarga.core.domain.repository

import com.libredescarga.core.model.AccentColor
import com.libredescarga.core.model.AudioFormat
import com.libredescarga.core.model.ThemeMode
import com.libredescarga.core.model.UserSettings
import com.libredescarga.core.model.VideoQuality
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {

    val settings: Flow<UserSettings>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDynamicColor(enabled: Boolean)

    suspend fun setAccentColor(color: AccentColor)

    suspend fun completeOnboarding(termsVersion: Int)

    suspend fun setClipboardDetection(enabled: Boolean)

    suspend fun setWifiOnly(enabled: Boolean)

    suspend fun setMaxConcurrentDownloads(count: Int)

    suspend fun setConcurrentFragments(count: Int)

    suspend fun setSpeedLimitKbps(kbps: Int)

    /** null = carpetas del sistema (Movies/ y Music/). */
    suspend fun setDownloadTreeUri(uri: String?)

    suspend fun setDefaultVideoQuality(quality: VideoQuality)

    suspend fun setDefaultAudioFormat(format: AudioFormat)

    /** Suma una descarga completada y devuelve el nuevo total. */
    suspend fun incrementCompletedDownloads(): Int

    suspend fun setDonationPromptAnswered()

    suspend fun setHideDonationReminders(hide: Boolean)

    suspend fun setUserBlockedDomains(domains: Set<String>)
}
