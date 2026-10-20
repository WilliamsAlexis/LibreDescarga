package com.libredescarga.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.libredescarga.core.model.AccentColor
import com.libredescarga.core.model.AudioFormat
import com.libredescarga.core.model.ThemeMode
import com.libredescarga.core.model.UserSettings
import com.libredescarga.core.model.VideoQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

/** Lee y escribe las preferencias del usuario en un DataStore local. */
class UserPreferencesDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val settings: Flow<UserSettings> = dataStore.data
        .catch { error ->
            // Si el archivo está dañado, se usan los valores por defecto.
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { it.toUserSettings() }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME_MODE] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) = edit { it[Keys.DYNAMIC_COLOR] = enabled }

    suspend fun setAccentColor(color: AccentColor) = edit { it[Keys.ACCENT_COLOR] = color.name }

    suspend fun completeOnboarding(termsVersion: Int) = edit {
        it[Keys.ONBOARDING_COMPLETED] = true
        it[Keys.ACCEPTED_TERMS_VERSION] = termsVersion
    }

    suspend fun setClipboardDetection(enabled: Boolean) =
        edit { it[Keys.CLIPBOARD_DETECTION] = enabled }

    suspend fun setWifiOnly(enabled: Boolean) = edit { it[Keys.WIFI_ONLY] = enabled }

    suspend fun setMaxConcurrentDownloads(count: Int) = edit {
        it[Keys.MAX_CONCURRENT_DOWNLOADS] = count.coerceConcurrent()
    }

    suspend fun setConcurrentFragments(count: Int) = edit {
        it[Keys.CONCURRENT_FRAGMENTS] = count.coerceIn(UserSettings.MIN_FRAGMENTS, UserSettings.MAX_FRAGMENTS)
    }

    suspend fun setSpeedLimitKbps(kbps: Int) = edit { it[Keys.SPEED_LIMIT_KBPS] = kbps.coerceAtLeast(0) }

    suspend fun setDownloadTreeUri(uri: String?) = edit {
        if (uri == null) it.remove(Keys.DOWNLOAD_TREE_URI) else it[Keys.DOWNLOAD_TREE_URI] = uri
    }

    suspend fun setDefaultVideoQuality(quality: VideoQuality) =
        edit { it[Keys.DEFAULT_VIDEO_QUALITY] = quality.name }

    suspend fun setDefaultAudioFormat(format: AudioFormat) =
        edit { it[Keys.DEFAULT_AUDIO_FORMAT] = format.name }

    suspend fun incrementCompletedDownloads(): Int {
        var total = 0
        dataStore.edit {
            total = (it[Keys.COMPLETED_DOWNLOADS] ?: 0) + 1
            it[Keys.COMPLETED_DOWNLOADS] = total
        }
        return total
    }

    suspend fun setDonationPromptAnswered() = edit { it[Keys.DONATION_PROMPT_ANSWERED] = true }

    suspend fun setHideDonationReminders(hide: Boolean) =
        edit { it[Keys.HIDE_DONATION_REMINDERS] = hide }

    suspend fun setUserBlockedDomains(domains: Set<String>) = edit { it[Keys.USER_BLOCKED_DOMAINS] = domains }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        dataStore.edit { block(it) }
    }

    private fun Preferences.toUserSettings(): UserSettings {
        val defaults = UserSettings()
        return UserSettings(
            themeMode = enumOrDefault(this[Keys.THEME_MODE], defaults.themeMode),
            useDynamicColor = this[Keys.DYNAMIC_COLOR] ?: defaults.useDynamicColor,
            accentColor = enumOrDefault(this[Keys.ACCENT_COLOR], defaults.accentColor),
            onboardingCompleted = this[Keys.ONBOARDING_COMPLETED] ?: defaults.onboardingCompleted,
            acceptedTermsVersion = this[Keys.ACCEPTED_TERMS_VERSION] ?: defaults.acceptedTermsVersion,
            clipboardDetection = this[Keys.CLIPBOARD_DETECTION] ?: defaults.clipboardDetection,
            wifiOnly = this[Keys.WIFI_ONLY] ?: defaults.wifiOnly,
            maxConcurrentDownloads = (this[Keys.MAX_CONCURRENT_DOWNLOADS]
                ?: defaults.maxConcurrentDownloads).coerceConcurrent(),
            concurrentFragments = (this[Keys.CONCURRENT_FRAGMENTS] ?: defaults.concurrentFragments)
                .coerceIn(UserSettings.MIN_FRAGMENTS, UserSettings.MAX_FRAGMENTS),
            speedLimitKbps = (this[Keys.SPEED_LIMIT_KBPS] ?: defaults.speedLimitKbps).coerceAtLeast(0),
            downloadTreeUri = this[Keys.DOWNLOAD_TREE_URI],
            defaultVideoQuality = enumOrDefault(
                this[Keys.DEFAULT_VIDEO_QUALITY],
                defaults.defaultVideoQuality,
            ),
            defaultAudioFormat = enumOrDefault(
                this[Keys.DEFAULT_AUDIO_FORMAT],
                defaults.defaultAudioFormat,
            ),
            completedDownloadsCount = this[Keys.COMPLETED_DOWNLOADS]
                ?: defaults.completedDownloadsCount,
            donationPromptAnswered = this[Keys.DONATION_PROMPT_ANSWERED]
                ?: defaults.donationPromptAnswered,
            hideDonationReminders = this[Keys.HIDE_DONATION_REMINDERS]
                ?: defaults.hideDonationReminders,
            userBlockedDomains = this[Keys.USER_BLOCKED_DOMAINS].orEmpty(),
        )
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private fun Int.coerceConcurrent(): Int = coerceIn(
        UserSettings.MIN_CONCURRENT_DOWNLOADS,
        UserSettings.MAX_CONCURRENT_DOWNLOADS,
    )

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val ACCEPTED_TERMS_VERSION = intPreferencesKey("accepted_terms_version")
        val CLIPBOARD_DETECTION = booleanPreferencesKey("clipboard_detection")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val MAX_CONCURRENT_DOWNLOADS = intPreferencesKey("max_concurrent_downloads")
        val CONCURRENT_FRAGMENTS = intPreferencesKey("concurrent_fragments")
        val SPEED_LIMIT_KBPS = intPreferencesKey("speed_limit_kbps")
        val DOWNLOAD_TREE_URI = stringPreferencesKey("download_tree_uri")
        val DEFAULT_VIDEO_QUALITY = stringPreferencesKey("default_video_quality")
        val DEFAULT_AUDIO_FORMAT = stringPreferencesKey("default_audio_format")
        val COMPLETED_DOWNLOADS = intPreferencesKey("completed_downloads")
        val DONATION_PROMPT_ANSWERED = booleanPreferencesKey("donation_prompt_answered")
        val HIDE_DONATION_REMINDERS = booleanPreferencesKey("hide_donation_reminders")
        val USER_BLOCKED_DOMAINS = stringSetPreferencesKey("user_blocked_domains")
    }
}
