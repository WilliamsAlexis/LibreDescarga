package com.libredescarga.core.data.repository

import com.libredescarga.core.datastore.UserPreferencesDataSource
import com.libredescarga.core.domain.repository.UserPreferencesRepository
import com.libredescarga.core.model.AccentColor
import com.libredescarga.core.model.AudioFormat
import com.libredescarga.core.model.ThemeMode
import com.libredescarga.core.model.UserSettings
import com.libredescarga.core.model.VideoQuality
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Las preferencias viven solo en el teléfono: no hay servidor ni cuenta. */
internal class OfflineUserPreferencesRepository @Inject constructor(
    private val dataSource: UserPreferencesDataSource,
) : UserPreferencesRepository {

    override val settings: Flow<UserSettings> = dataSource.settings

    override suspend fun setThemeMode(mode: ThemeMode) = dataSource.setThemeMode(mode)

    override suspend fun setDynamicColor(enabled: Boolean) = dataSource.setDynamicColor(enabled)

    override suspend fun setAccentColor(color: AccentColor) = dataSource.setAccentColor(color)

    override suspend fun completeOnboarding(termsVersion: Int) =
        dataSource.completeOnboarding(termsVersion)

    override suspend fun setClipboardDetection(enabled: Boolean) =
        dataSource.setClipboardDetection(enabled)

    override suspend fun setWifiOnly(enabled: Boolean) = dataSource.setWifiOnly(enabled)

    override suspend fun setMaxConcurrentDownloads(count: Int) =
        dataSource.setMaxConcurrentDownloads(count)

    override suspend fun setConcurrentFragments(count: Int) = dataSource.setConcurrentFragments(count)

    override suspend fun setSpeedLimitKbps(kbps: Int) = dataSource.setSpeedLimitKbps(kbps)

    override suspend fun setDownloadTreeUri(uri: String?) = dataSource.setDownloadTreeUri(uri)

    override suspend fun setDefaultVideoQuality(quality: VideoQuality) =
        dataSource.setDefaultVideoQuality(quality)

    override suspend fun setDefaultAudioFormat(format: AudioFormat) =
        dataSource.setDefaultAudioFormat(format)

    override suspend fun incrementCompletedDownloads(): Int =
        dataSource.incrementCompletedDownloads()

    override suspend fun setDonationPromptAnswered() = dataSource.setDonationPromptAnswered()

    override suspend fun setHideDonationReminders(hide: Boolean) =
        dataSource.setHideDonationReminders(hide)

    override suspend fun setUserBlockedDomains(domains: Set<String>) =
        dataSource.setUserBlockedDomains(domains)
}
