package com.libredescarga.core.domain.settings

import com.libredescarga.core.config.AppConfig
import com.libredescarga.core.model.ApkAsset
import com.libredescarga.core.model.AppUpdate
import com.libredescarga.core.model.UpdateCheck
import com.libredescarga.core.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRulesTest {

    private val threshold = AppConfig.DONATION_PROMPT_THRESHOLD

    @Test
    fun `el mensaje de apoyo aparece tras 20 descargas completadas`() {
        assertEquals(20, threshold)
        assertFalse(DonationPrompt.shouldShow(UserSettings(completedDownloadsCount = 19), threshold))
        assertTrue(DonationPrompt.shouldShow(UserSettings(completedDownloadsCount = 20), threshold))
        assertTrue(DonationPrompt.shouldShow(UserSettings(completedDownloadsCount = 57), threshold))
    }

    @Test
    fun `una vez respondido no vuelve a aparecer nunca`() {
        val answered = UserSettings(completedDownloadsCount = 100, donationPromptAnswered = true)
        assertFalse(DonationPrompt.shouldShow(answered, threshold))
    }

    @Test
    fun `si se ocultaron los recordatorios no aparece`() {
        val hidden = UserSettings(completedDownloadsCount = 100, hideDonationReminders = true)
        assertFalse(DonationPrompt.shouldShow(hidden, threshold))
    }

    private fun update(code: Long = 5, minSdk: Int = 24, assets: List<ApkAsset> = emptyList()) = AppUpdate(
        versionCode = code,
        versionName = "1.0.$code",
        notes = "Notas",
        minSdk = minSdk,
        assets = assets,
    )

    @Test
    fun `una versión más nueva está disponible`() {
        val result = UpdateChecker.evaluate(update(code = 5), installedVersionCode = 4, deviceSdk = 34)
        assertTrue(result is UpdateCheck.Available)
    }

    @Test
    fun `la misma versión o una anterior no se ofrece`() {
        assertEquals(UpdateCheck.UpToDate, UpdateChecker.evaluate(update(code = 4), 4, 34))
        assertEquals(UpdateCheck.UpToDate, UpdateChecker.evaluate(update(code = 3), 4, 34))
    }

    @Test
    fun `si la versión exige un Android más nuevo se avisa`() {
        val result = UpdateChecker.evaluate(update(code = 9, minSdk = 33), installedVersionCode = 4, deviceSdk = 30)
        assertTrue(result is UpdateCheck.Unsupported)
    }

    @Test
    fun `elige el APK de la arquitectura del teléfono y si no, el universal`() {
        val arm64 = ApkAsset("arm64-v8a", "https://github.com/o/r/releases/download/v1/arm64.apk", "a".repeat(64))
        val v7 = ApkAsset("armeabi-v7a", "https://github.com/o/r/releases/download/v1/v7.apk", "b".repeat(64))
        val universal = ApkAsset(null, "https://github.com/o/r/releases/download/v1/universal.apk", "c".repeat(64))

        val both = update(assets = listOf(v7, arm64, universal))
        assertEquals(arm64, both.assetFor(listOf("arm64-v8a", "armeabi-v7a", "armeabi")))
        assertEquals(v7, both.assetFor(listOf("armeabi-v7a", "armeabi")))
        assertEquals(universal, both.assetFor(listOf("x86_64")))

        assertNull(update(assets = listOf(arm64)).assetFor(listOf("x86_64")))
    }
}
