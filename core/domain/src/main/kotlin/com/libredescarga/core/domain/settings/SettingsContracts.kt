package com.libredescarga.core.domain.settings

import com.libredescarga.core.model.AppUpdate
import com.libredescarga.core.model.BlocklistRefresh
import com.libredescarga.core.model.EngineUpdateOutcome
import com.libredescarga.core.model.InstallOutcome
import com.libredescarga.core.model.UpdateCheck
import com.libredescarga.core.model.UserSettings
import com.libredescarga.core.security.UpdateSecurity
import java.io.File

/** Actualización de la app desde GitHub Releases. Todo se verifica antes de instalar. */
interface AppUpdateRepository {
    val installedVersionName: String
    val installedVersionCode: Long

    /** Consulta el JSON público de la última versión. Lanza [com.libredescarga.core.model.UpdateException] si falla. */
    suspend fun check(): UpdateCheck

    /**
     * Descarga el APK y comprueba su SHA-256 y que esté firmado igual que la app instalada.
     * Si algo no cuadra, el archivo se borra y se lanza [com.libredescarga.core.model.UpdateException].
     */
    suspend fun download(update: AppUpdate, onProgress: (Float) -> Unit): File

    /** Pide a Android instalar el APK ya verificado. */
    fun install(apk: File): InstallOutcome
}

/** El motor de descarga (yt-dlp) se actualiza sin reinstalar la app. */
interface EngineRepository {
    suspend fun version(): String?

    suspend fun update(): EngineUpdateOutcome
}

/** Lista de sitios bloqueados por solicitudes de derechos de autor, actualizable desde GitHub. */
interface BlocklistUpdater {
    suspend fun refresh(): BlocklistRefresh
}

data class BackupSummary(
    val settings: Boolean,
    val searches: Int,
    val bookmarks: Int,
    val history: Int,
    val playlists: Int,
    val favorites: Int,
    val sites: Int,
    val blockedDomains: Int,
)

/** Copia de seguridad en un archivo JSON local: historial, marcadores, listas y ajustes. */
interface BackupRepository {
    suspend fun export(): String

    /** Une los datos del archivo con los actuales (no borra nada). Lanza [IllegalArgumentException] si el archivo no sirve. */
    suspend fun import(json: String): BackupSummary
}

interface CacheRepository {
    suspend fun sizeBytes(): Long

    /** Vacía la caché y devuelve los bytes liberados. */
    suspend fun clear(): Long
}

/** Cuándo se ofrece, una sola vez, el mensaje de apoyo al creador. */
object DonationPrompt {
    fun shouldShow(settings: UserSettings, threshold: Int): Boolean =
        !settings.donationPromptAnswered && !settings.hideDonationReminders && settings.completedDownloadsCount >= threshold
}

/** Decide qué hacer con la versión publicada en GitHub (sin red ni Android, para probarla). */
object UpdateChecker {
    fun evaluate(published: AppUpdate, installedVersionCode: Long, deviceSdk: Int): UpdateCheck = when {
        !UpdateSecurity.isNewer(installedVersionCode, published.versionCode) -> UpdateCheck.UpToDate
        deviceSdk < published.minSdk -> UpdateCheck.Unsupported(published)
        else -> UpdateCheck.Available(published)
    }
}
