package com.libredescarga.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.libredescarga.core.config.AppConfig
import com.libredescarga.core.designsystem.component.LibreLogo
import com.libredescarga.core.designsystem.component.rememberAppName
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.core.ui.openExternalLink
import com.libredescarga.feature.settings.component.BodyText
import com.libredescarga.feature.settings.component.ClickRow
import com.libredescarga.feature.settings.component.SettingsScaffold
import com.libredescarga.feature.settings.component.SettingsSection
import kotlinx.coroutines.launch

@Composable
internal fun AboutScreen(
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenLegal: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    val context = LocalContext.current
    val appName = rememberAppName()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val noBrowserMessage = stringResource(R.string.settings_support_no_browser)
    val version = remember(context) { context.versionLabel() }

    SettingsScaffold(title = stringResource(R.string.settings_about), onBack = onBack, snackbarHostState = snackbarHostState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LibreLogo(size = 96.dp, contentDescription = null)
            Text(appName, style = MaterialTheme.typography.headlineMedium)
            Text(
                text = stringResource(R.string.settings_about_version, version),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.settings_about_tagline, appName),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = {
                    if (!context.openDonationLink()) scope.launch { snackbarHostState.showSnackbar(noBrowserMessage) }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                androidx.compose.material3.Icon(LibreIcons.Heart, contentDescription = null)
                Text(stringResource(R.string.settings_about_coffee), modifier = Modifier.padding(start = 8.dp))
            }
        }
        HorizontalDivider()
        ClickRow(title = stringResource(R.string.settings_licenses), icon = LibreIcons.Code, onClick = onOpenLicenses)
        ClickRow(title = stringResource(R.string.settings_legal), icon = LibreIcons.Legal, onClick = onOpenLegal)
        ClickRow(title = stringResource(R.string.settings_privacy), icon = LibreIcons.PrivacyTip, onClick = onOpenPrivacy)
        ClickRow(
            title = stringResource(R.string.settings_source_code),
            subtitle = AppConfig.GITHUB_REPO_URL.removePrefix("https://"),
            icon = LibreIcons.Internet,
            onClick = {
                if (!context.openExternalLink(AppConfig.GITHUB_REPO_URL)) {
                    scope.launch { snackbarHostState.showSnackbar(noBrowserMessage) }
                }
            },
        )
    }
}

@Composable
internal fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    SettingsScaffold(title = stringResource(R.string.settings_licenses), onBack = onBack) {
        BodyText(stringResource(R.string.settings_licenses_intro, rememberAppName()))
        BodyText(stringResource(R.string.settings_licenses_source, AppConfig.GITHUB_REPO_URL))
        SettingsSection(stringResource(R.string.settings_licenses_engine)) {
            OpenSourceEntry("yt-dlp", "The Unlicense", "https://github.com/yt-dlp/yt-dlp", context)
            OpenSourceEntry("youtubedl-android", "GPL-3.0", "https://github.com/junkfood02/youtubedl-android", context)
            OpenSourceEntry("FFmpeg", "LGPL-2.1+ / GPL-3.0", "https://ffmpeg.org", context)
            OpenSourceEntry("Python", "PSF License", "https://www.python.org", context)
        }
        SettingsSection(stringResource(R.string.settings_licenses_libraries)) {
            OpenSourceEntry("AndroidX Media3 (ExoPlayer, MediaSession)", "Apache 2.0", "https://github.com/androidx/media", context)
            OpenSourceEntry("Jetpack Compose, Material 3, Navigation, Room, WorkManager, DataStore", "Apache 2.0", "https://developer.android.com/jetpack", context)
            OpenSourceEntry("Hilt / Dagger", "Apache 2.0", "https://github.com/google/dagger", context)
            OpenSourceEntry("Tink", "Apache 2.0", "https://github.com/tink-crypto/tink-java", context)
            OpenSourceEntry("OkHttp", "Apache 2.0", "https://github.com/square/okhttp", context)
            OpenSourceEntry("Coil", "Apache 2.0", "https://github.com/coil-kt/coil", context)
            OpenSourceEntry("Kotlin, kotlinx.coroutines, kotlinx.serialization", "Apache 2.0", "https://github.com/JetBrains/kotlin", context)
        }
    }
}

@Composable
private fun OpenSourceEntry(name: String, license: String, url: String, context: Context) {
    ClickRow(title = name, subtitle = license, onClick = { context.openExternalLink(url) })
}

@Composable
internal fun LegalScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    SettingsScaffold(title = stringResource(R.string.settings_legal), onBack = onBack) {
        BodyText(stringResource(R.string.settings_legal_use))
        BodyText(stringResource(R.string.settings_legal_responsibility))
        BodyText(stringResource(R.string.settings_legal_drm, rememberAppName()))
        BodyText(stringResource(R.string.settings_legal_blocklist))
        SettingsSection(stringResource(R.string.settings_legal_contact_title)) {
            BodyText(stringResource(R.string.settings_legal_contact_message))
            ClickRow(
                title = AppConfig.LEGAL_CONTACT_EMAIL,
                subtitle = stringResource(R.string.settings_legal_contact_action),
                icon = LibreIcons.Email,
                onClick = { context.composeEmail(AppConfig.LEGAL_CONTACT_EMAIL) },
            )
        }
    }
}

@Composable
internal fun PrivacyScreen(onBack: () -> Unit) {
    SettingsScaffold(title = stringResource(R.string.settings_privacy), onBack = onBack) {
        Text(
            text = stringResource(R.string.settings_privacy_summary),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
        )
        SettingsSection(stringResource(R.string.settings_privacy_connections_title)) {
            BodyText(stringResource(R.string.settings_privacy_connections_intro))
            BodyText(stringResource(R.string.settings_privacy_sites))
            BodyText(stringResource(R.string.settings_privacy_github))
            BodyText(stringResource(R.string.settings_privacy_search))
        }
        SettingsSection(stringResource(R.string.settings_privacy_never_title)) {
            BodyText(stringResource(R.string.settings_privacy_never))
        }
    }
}

private fun Context.versionLabel(): String = runCatching {
    packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
}.getOrDefault("")

/** Abre la app de correo con el destinatario ya escrito (la app no envía nada por sí sola). */
private fun Context.composeEmail(address: String) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Sin app de correo: la dirección queda visible en pantalla para copiarla.
    }
}
