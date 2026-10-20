package com.libredescarga.core.network

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.libredescarga.core.config.AppConfig
import com.libredescarga.core.domain.settings.AppUpdateRepository
import com.libredescarga.core.domain.settings.UpdateChecker
import com.libredescarga.core.model.AppUpdate
import com.libredescarga.core.model.InstallOutcome
import com.libredescarga.core.model.UpdateCheck
import com.libredescarga.core.model.UpdateException
import com.libredescarga.core.model.UpdateFailure
import com.libredescarga.core.security.Hex
import com.libredescarga.core.security.UpdateSecurity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Actualizador de la app desde GitHub Releases. Antes de ofrecer la instalación comprueba:
 * 1. que la dirección sea de GitHub y por https;
 * 2. que el SHA-256 del archivo descargado sea el publicado;
 * 3. que esté firmado con el mismo certificado que la app instalada.
 */
@Singleton
internal class GithubAppUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val client: GithubClient,
) : AppUpdateRepository {

    private val packageInfo: PackageInfo
        get() = context.packageManager.getPackageInfo(context.packageName, 0)

    override val installedVersionName: String get() = packageInfo.versionName.orEmpty()

    @Suppress("DEPRECATION")
    override val installedVersionCode: Long
        get() = packageInfo.let { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode else it.versionCode.toLong() }

    override suspend fun check(): UpdateCheck {
        val text = client.fetchText(AppConfig.UPDATE_MANIFEST_URL)
        val published = UpdateManifestParser.parse(text, Locale.getDefault().language)
        return UpdateChecker.evaluate(published, installedVersionCode, Build.VERSION.SDK_INT)
    }

    override suspend fun download(update: AppUpdate, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val asset = update.assetFor(Build.SUPPORTED_ABIS.toList())
            ?: throw UpdateException(UpdateFailure.NO_COMPATIBLE_APK)

        val folder = File(context.cacheDir, "updates").apply { mkdirs() }
        folder.listFiles()?.forEach { it.delete() } // Solo se conserva la descarga actual.
        val partial = File(folder, "download.part")
        val apk = File(folder, "LibreDescarga-${update.versionName.filter { it.isLetterOrDigit() || it == '.' }}.apk")

        try {
            client.downloadTo(asset.url, partial, onProgress)

            val actualHash = partial.inputStream().buffered().use(UpdateSecurity::sha256Hex)
            if (!UpdateSecurity.hashMatches(actualHash, asset.sha256)) throw UpdateException(UpdateFailure.HASH_MISMATCH)

            val candidate = ApkSigners.ofArchive(context.packageManager, partial.absolutePath)
            val installed = ApkSigners.ofInstalled(context.packageManager, context.packageName)
            if (!UpdateSecurity.sameSigner(installed, candidate)) throw UpdateException(UpdateFailure.SIGNATURE_MISMATCH)

            if (!partial.renameTo(apk)) throw UpdateException(UpdateFailure.STORAGE)
            apk
        } catch (error: Exception) {
            partial.delete()
            apk.delete()
            throw error
        }
    }

    override fun install(apk: File): InstallOutcome {
        // Android 8 o superior exige que el usuario permita a esta app instalar otras.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return InstallOutcome.NEEDS_PERMISSION
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return InstallOutcome.STARTED
    }
}

/** Certificados (SHA-256) con los que está firmada una app instalada o un APK descargado. */
internal object ApkSigners {

    fun ofInstalled(pm: PackageManager, packageName: String): Set<String> =
        digests(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            },
        )

    fun ofArchive(pm: PackageManager, path: String): Set<String> {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val info = pm.getPackageArchiveInfo(path, flags) ?: throw UpdateException(UpdateFailure.SIGNATURE_MISMATCH, "APK ilegible")
        return digests(info)
    }

    @Suppress("DEPRECATION")
    private fun digests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo ?: return emptySet()
            // Incluye los certificados anteriores, por si la clave de firma se rotó.
            (signing.apkContentsSigners.orEmpty().toList() + signing.signingCertificateHistory.orEmpty().toList())
        } else {
            info.signatures.orEmpty().toList()
        }
        return signatures.map { Hex.encode(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }.toSet()
    }
}
