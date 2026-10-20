package com.libredescarga.core.testing

import com.libredescarga.core.domain.repository.UserPreferencesRepository
import com.libredescarga.core.model.AccentColor
import com.libredescarga.core.model.AudioFormat
import com.libredescarga.core.model.ThemeMode
import com.libredescarga.core.model.UserSettings
import com.libredescarga.core.model.VideoQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Repositorio en memoria para pruebas. */
class FakeUserPreferencesRepository(
    initial: UserSettings = UserSettings(),
) : UserPreferencesRepository {

    private val state = MutableStateFlow(initial)

    val current: UserSettings get() = state.value

    override val settings: Flow<UserSettings> = state

    fun emit(settings: UserSettings) {
        state.value = settings
    }

    override suspend fun setThemeMode(mode: ThemeMode) =
        state.update { it.copy(themeMode = mode) }

    override suspend fun setDynamicColor(enabled: Boolean) =
        state.update { it.copy(useDynamicColor = enabled) }

    override suspend fun setAccentColor(color: AccentColor) =
        state.update { it.copy(accentColor = color) }

    override suspend fun completeOnboarding(termsVersion: Int) =
        state.update { it.copy(onboardingCompleted = true, acceptedTermsVersion = termsVersion) }

    override suspend fun setClipboardDetection(enabled: Boolean) =
        state.update { it.copy(clipboardDetection = enabled) }

    override suspend fun setWifiOnly(enabled: Boolean) =
        state.update { it.copy(wifiOnly = enabled) }

    override suspend fun setMaxConcurrentDownloads(count: Int) =
        state.update {
            it.copy(
                maxConcurrentDownloads = count.coerceIn(
                    UserSettings.MIN_CONCURRENT_DOWNLOADS,
                    UserSettings.MAX_CONCURRENT_DOWNLOADS,
                ),
            )
        }

    override suspend fun setConcurrentFragments(count: Int) =
        state.update {
            it.copy(concurrentFragments = count.coerceIn(UserSettings.MIN_FRAGMENTS, UserSettings.MAX_FRAGMENTS))
        }

    override suspend fun setSpeedLimitKbps(kbps: Int) =
        state.update { it.copy(speedLimitKbps = kbps.coerceAtLeast(0)) }

    override suspend fun setDownloadTreeUri(uri: String?) =
        state.update { it.copy(downloadTreeUri = uri) }

    override suspend fun setDefaultVideoQuality(quality: VideoQuality) =
        state.update { it.copy(defaultVideoQuality = quality) }

    override suspend fun setDefaultAudioFormat(format: AudioFormat) =
        state.update { it.copy(defaultAudioFormat = format) }

    override suspend fun incrementCompletedDownloads(): Int {
        state.update { it.copy(completedDownloadsCount = it.completedDownloadsCount + 1) }
        return state.value.completedDownloadsCount
    }

    override suspend fun setDonationPromptAnswered() =
        state.update { it.copy(donationPromptAnswered = true) }

    override suspend fun setHideDonationReminders(hide: Boolean) =
        state.update { it.copy(hideDonationReminders = hide) }

    override suspend fun setUserBlockedDomains(domains: Set<String>) =
        state.update { it.copy(userBlockedDomains = domains) }
}
