package com.libredescarga.feature.settings

import com.libredescarga.core.model.AccentColor
import com.libredescarga.core.model.AppUpdate
import com.libredescarga.core.model.ApkAsset
import com.libredescarga.core.model.AudioFormat
import com.libredescarga.core.model.BlocklistRefresh
import com.libredescarga.core.model.EngineUpdateOutcome
import com.libredescarga.core.model.InstallOutcome
import com.libredescarga.core.model.ThemeMode
import com.libredescarga.core.model.UpdateCheck
import com.libredescarga.core.model.UpdateException
import com.libredescarga.core.model.UpdateFailure
import com.libredescarga.core.model.VideoQuality
import com.libredescarga.core.testing.FakeAppUpdateRepository
import com.libredescarga.core.testing.FakeBackupRepository
import com.libredescarga.core.testing.FakeBlocklistUpdater
import com.libredescarga.core.testing.FakeCacheRepository
import com.libredescarga.core.testing.FakeEngineRepository
import com.libredescarga.core.testing.FakeSiteRepository
import com.libredescarga.core.testing.FakeUserPreferencesRepository
import com.libredescarga.core.testing.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val preferences = FakeUserPreferencesRepository()

    private fun <T> TestScope.collect(flow: kotlinx.coroutines.flow.Flow<T>) =
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { flow.collect {} }

    // --- Ajustes ---------------------------------------------------------------------------------------------

    private val cache = FakeCacheRepository(size = 8_000_000)
    private val backup = FakeBackupRepository()
    private fun settingsViewModel() = SettingsViewModel(preferences, cache, backup)

    @Test
    fun `los ajustes de apariencia y descargas se guardan`() = runTest {
        val viewModel = settingsViewModel()
        collect(viewModel.uiState)

        viewModel.onThemeMode(ThemeMode.DARK)
        viewModel.onAccent(AccentColor.ROSE)
        viewModel.onDynamicColor(true)
        viewModel.onClipboardDetection(false)
        viewModel.onWifiOnly(true)
        viewModel.onMaxConcurrent(4)
        viewModel.onVideoQuality(VideoQuality.P1080)
        viewModel.onAudioFormat(AudioFormat.OPUS)
        viewModel.onHideDonationReminders(true)

        val settings = viewModel.uiState.value.settings
        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertEquals(AccentColor.ROSE, settings.accentColor)
        assertTrue(settings.useDynamicColor)
        assertFalse(settings.clipboardDetection)
        assertTrue(settings.wifiOnly)
        assertEquals(4, settings.maxConcurrentDownloads)
        assertEquals(VideoQuality.P1080, settings.defaultVideoQuality)
        assertEquals(AudioFormat.OPUS, settings.defaultAudioFormat)
        assertTrue(settings.hideDonationReminders)
    }

    @Test
    fun `muestra el tamaño de la caché y la limpia`() = runTest {
        val viewModel = settingsViewModel()
        collect(viewModel.uiState)
        assertEquals(8_000_000L, viewModel.uiState.value.cacheBytes)

        viewModel.onClearCache()

        assertEquals(0L, viewModel.uiState.value.cacheBytes)
        assertEquals(SettingsMessage.CacheCleared(8_000_000), viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.busy)
    }

    @Test
    fun `exportar entrega el JSON a quien lo guarda`() = runTest {
        backup.exported = """{"app":"LibreDescarga"}"""
        val viewModel = settingsViewModel()
        collect(viewModel.uiState)
        var saved: String? = null

        viewModel.onExportBackup { saved = it }

        assertEquals("""{"app":"LibreDescarga"}""", saved)
        assertEquals(SettingsMessage.BackupSaved, viewModel.uiState.value.message)
    }

    @Test
    fun `si no se puede guardar el archivo se avisa`() = runTest {
        val viewModel = settingsViewModel()
        collect(viewModel.uiState)
        viewModel.onExportBackup { throw java.io.IOException("sin espacio") }
        assertEquals(SettingsMessage.BackupIoError, viewModel.uiState.value.message)
    }

    @Test
    fun `restaurar une el archivo y devuelve el resumen`() = runTest {
        val viewModel = settingsViewModel()
        collect(viewModel.uiState)

        viewModel.onRestoreBackup { """{"app":"LibreDescarga"}""" }

        assertEquals("""{"app":"LibreDescarga"}""", backup.imported)
        assertTrue(viewModel.uiState.value.message is SettingsMessage.BackupRestored)
    }

    @Test
    fun `un archivo inválido o ilegible no rompe nada`() = runTest {
        val viewModel = settingsViewModel()
        collect(viewModel.uiState)

        backup.importError = IllegalArgumentException("no es una copia")
        viewModel.onRestoreBackup { "basura" }
        assertEquals(SettingsMessage.BackupInvalid, viewModel.uiState.value.message)

        backup.importError = null
        viewModel.onRestoreBackup { throw java.io.IOException("no se puede leer") }
        assertEquals(SettingsMessage.BackupIoError, viewModel.uiState.value.message)

        viewModel.onMessageShown()
        assertNull(viewModel.uiState.value.message)
    }

    // --- Actualizaciones ---------------------------------------------------------------------------------------

    private val appUpdater = FakeAppUpdateRepository()
    private val engine = FakeEngineRepository()
    private val blocklistUpdater = FakeBlocklistUpdater()
    private fun updatesViewModel() = UpdatesViewModel(appUpdater, engine, blocklistUpdater)

    private val newVersion = AppUpdate(
        versionCode = 2,
        versionName = "1.0.1",
        notes = "Mejoras",
        minSdk = 24,
        assets = listOf(ApkAsset(null, "https://github.com/o/r/releases/download/v1/app.apk", "a".repeat(64))),
    )

    @Test
    fun `muestra las versiones instaladas`() = runTest {
        val viewModel = updatesViewModel()
        assertEquals("1.0.0", viewModel.uiState.value.installedVersionName)
        assertEquals("2026.01.01", viewModel.uiState.value.engineVersion)
    }

    @Test
    fun `actualizar el motor cambia la versión de yt-dlp`() = runTest {
        val viewModel = updatesViewModel()
        viewModel.onUpdateEngine()
        assertEquals(EngineStatus.Updated, viewModel.uiState.value.engine)
        assertEquals("2026.02.02", viewModel.uiState.value.engineVersion)

        engine.outcome = EngineUpdateOutcome.ALREADY_UP_TO_DATE
        viewModel.onUpdateEngine()
        assertEquals(EngineStatus.Current, viewModel.uiState.value.engine)
    }

    @Test
    fun `si falla la actualización del motor se muestra el error`() = runTest {
        engine.error = java.io.IOException("sin red")
        val viewModel = updatesViewModel()
        viewModel.onUpdateEngine()
        assertEquals(EngineStatus.Failed(UpdateFailure.NETWORK), viewModel.uiState.value.engine)
    }

    @Test
    fun `buscar la actualización de la app`() = runTest {
        val viewModel = updatesViewModel()
        viewModel.onCheckApp()
        assertEquals(AppUpdateStatus.UpToDate, viewModel.uiState.value.app)

        appUpdater.checkResult = UpdateCheck.Available(newVersion)
        viewModel.onCheckApp()
        assertEquals(AppUpdateStatus.Available(newVersion), viewModel.uiState.value.app)

        appUpdater.checkResult = UpdateCheck.Unsupported(newVersion)
        viewModel.onCheckApp()
        assertEquals(AppUpdateStatus.Unsupported(newVersion), viewModel.uiState.value.app)
    }

    @Test
    fun `los errores al buscar se traducen a un motivo`() = runTest {
        val viewModel = updatesViewModel()
        appUpdater.checkError = UpdateException(UpdateFailure.INVALID_MANIFEST)
        viewModel.onCheckApp()
        assertEquals(AppUpdateStatus.Failed(UpdateFailure.INVALID_MANIFEST), viewModel.uiState.value.app)

        appUpdater.checkError = IllegalStateException("raro")
        viewModel.onCheckApp()
        assertEquals(AppUpdateStatus.Failed(UpdateFailure.UNKNOWN), viewModel.uiState.value.app)
    }

    @Test
    fun `descargar deja el APK listo y luego se instala`() = runTest {
        val viewModel = updatesViewModel()
        appUpdater.checkResult = UpdateCheck.Available(newVersion)
        viewModel.onCheckApp()

        viewModel.onDownloadUpdate()
        val ready = viewModel.uiState.value.app as AppUpdateStatus.Ready
        assertEquals("update-1.0.1.apk", ready.apk.name)
        assertFalse(ready.needsPermission)

        viewModel.onInstallUpdate()
        assertEquals(listOf(ready.apk), appUpdater.installed)
    }

    @Test
    fun `si falta el permiso de instalar se explica y se puede reintentar`() = runTest {
        val viewModel = updatesViewModel()
        appUpdater.checkResult = UpdateCheck.Available(newVersion)
        viewModel.onCheckApp()
        viewModel.onDownloadUpdate()

        appUpdater.installOutcome = InstallOutcome.NEEDS_PERMISSION
        viewModel.onInstallUpdate()
        assertTrue((viewModel.uiState.value.app as AppUpdateStatus.Ready).needsPermission)

        appUpdater.installOutcome = InstallOutcome.STARTED
        viewModel.onInstallUpdate()
        assertFalse((viewModel.uiState.value.app as AppUpdateStatus.Ready).needsPermission)
        assertEquals(2, appUpdater.installed.size)
    }

    @Test
    fun `un APK que no pasa la verificación nunca llega a instalarse`() = runTest {
        val viewModel = updatesViewModel()
        appUpdater.checkResult = UpdateCheck.Available(newVersion)
        viewModel.onCheckApp()

        appUpdater.downloadError = UpdateException(UpdateFailure.HASH_MISMATCH)
        viewModel.onDownloadUpdate()
        assertEquals(AppUpdateStatus.Failed(UpdateFailure.HASH_MISMATCH), viewModel.uiState.value.app)

        viewModel.onInstallUpdate()
        assertTrue(appUpdater.installed.isEmpty())
    }

    @Test
    fun `no se instala ni descarga sin haber una actualización disponible`() = runTest {
        val viewModel = updatesViewModel()
        viewModel.onDownloadUpdate()
        viewModel.onInstallUpdate()
        assertEquals(AppUpdateStatus.Idle, viewModel.uiState.value.app)
        assertTrue(appUpdater.installed.isEmpty())
    }

    @Test
    fun `actualizar la lista de sitios bloqueados`() = runTest {
        val viewModel = updatesViewModel()
        blocklistUpdater.result = BlocklistRefresh.Updated(version = 3, domains = 12)
        viewModel.onUpdateBlocklist()
        assertEquals(BlocklistStatus.Updated(12), viewModel.uiState.value.blocklist)

        blocklistUpdater.result = BlocklistRefresh.AlreadyCurrent
        viewModel.onUpdateBlocklist()
        assertEquals(BlocklistStatus.Current, viewModel.uiState.value.blocklist)

        blocklistUpdater.error = UpdateException(UpdateFailure.NETWORK)
        viewModel.onUpdateBlocklist()
        assertEquals(BlocklistStatus.Failed(UpdateFailure.NETWORK), viewModel.uiState.value.blocklist)
    }

    // --- Sitios ------------------------------------------------------------------------------------------------------

    @Test
    fun `gestionar sitios y la lista negra propia`() = runTest {
        val sites = FakeSiteRepository()
        val viewModel = SitesViewModel(sites, preferences)
        collect(viewModel.uiState)

        viewModel.onAddSite("Uno", "https://uno.example.org")
        viewModel.onAddSite("Dos", "https://dos.example.org")
        val (uno, dos) = viewModel.uiState.value.sites

        viewModel.onSetEnabled(uno, false)
        assertFalse(viewModel.uiState.value.sites.first { it.id == uno.id }.enabled)

        viewModel.onMove(dos, up = true)
        assertEquals(listOf("Dos", "Uno"), viewModel.uiState.value.sites.map { it.name })

        viewModel.onAddSite("Malo", "javascript:alert(1)")
        assertEquals(2, viewModel.uiState.value.sites.size)

        viewModel.onRemove(uno)
        assertEquals(listOf("Dos"), viewModel.uiState.value.sites.map { it.name })

        viewModel.onBlockDomain("https://www.Bloqueado.com/ruta")
        viewModel.onBlockDomain("palabra")
        assertEquals(listOf("bloqueado.com"), viewModel.uiState.value.blockedDomains)
        assertEquals(SitesError.InvalidDomain, viewModel.uiState.value.error)

        viewModel.onUnblockDomain("bloqueado.com")
        assertTrue(viewModel.uiState.value.blockedDomains.isEmpty())
    }
}
