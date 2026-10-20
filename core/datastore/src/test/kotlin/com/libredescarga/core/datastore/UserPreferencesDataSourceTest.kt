package com.libredescarga.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.libredescarga.core.model.AccentColor
import com.libredescarga.core.model.ThemeMode
import com.libredescarga.core.model.UserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UserPreferencesDataSourceTest {

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

    private fun TestScope.createDataSource() = UserPreferencesDataSource(
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            tempFolder.newFile("user_preferences_test.preferences_pb")
        },
    )

    @Test
    fun `sin datos guardados devuelve los valores por defecto`() = runTest {
        val dataSource = createDataSource()
        assertEquals(UserSettings(), dataSource.settings.first())
    }

    @Test
    fun `guarda el tema y el acento`() = runTest {
        val dataSource = createDataSource()

        dataSource.setThemeMode(ThemeMode.DARK)
        dataSource.setAccentColor(AccentColor.CORAL)

        val settings = dataSource.settings.first()
        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertEquals(AccentColor.CORAL, settings.accentColor)
    }

    @Test
    fun `las descargas simultáneas se limitan entre 1 y 5`() = runTest {
        val dataSource = createDataSource()

        dataSource.setMaxConcurrentDownloads(42)
        assertEquals(5, dataSource.settings.first().maxConcurrentDownloads)

        dataSource.setMaxConcurrentDownloads(0)
        assertEquals(1, dataSource.settings.first().maxConcurrentDownloads)
    }

    @Test
    fun `completar la bienvenida guarda la versión de los términos`() = runTest {
        val dataSource = createDataSource()

        dataSource.completeOnboarding(termsVersion = 3)

        val settings = dataSource.settings.first()
        assertTrue(settings.onboardingCompleted)
        assertEquals(3, settings.acceptedTermsVersion)
    }

    @Test
    fun `el contador de descargas se incrementa`() = runTest {
        val dataSource = createDataSource()

        dataSource.incrementCompletedDownloads()
        val total = dataSource.incrementCompletedDownloads()

        assertEquals(2, total)
        assertEquals(2, dataSource.settings.first().completedDownloadsCount)
    }
}
