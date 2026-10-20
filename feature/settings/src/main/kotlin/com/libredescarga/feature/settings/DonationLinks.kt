package com.libredescarga.feature.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.libredescarga.core.config.AppConfig
import com.libredescarga.core.designsystem.component.rememberAppName
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.core.ui.openExternalLink

/*
 * La donación es siempre voluntaria y no desbloquea nada. El enlace sale de AppConfig.DONATION_URL
 * (fijo en el código) y se abre en el navegador externo o en la app de PayPal: nunca dentro de un WebView.
 * La app no procesa pagos, no pide datos de tarjeta y no guarda si el usuario donó.
 */

/** Abre PayPal.Me fuera de la app. Devuelve false si no hay navegador. */
internal fun Context.openDonationLink(): Boolean = openExternalLink(AppConfig.DONATION_URL)

internal fun Context.copyDonationLink() {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("PayPal", AppConfig.DONATION_URL))
}

internal fun Context.shareDonationLink(chooserTitle: String) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, AppConfig.DONATION_URL)
    try {
        startActivity(Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // Sin apps para compartir: no hay nada más que hacer.
    }
}

/**
 * Mensaje amable que aparece una sola vez, tras 20 descargas completadas.
 * Responda lo que responda el usuario, no vuelve a salir.
 */
@Composable
fun DonationPromptDialog(onDonate: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(LibreIcons.Heart, contentDescription = null) },
        title = { Text(stringResource(R.string.settings_prompt_title, rememberAppName())) },
        text = { Text(stringResource(R.string.settings_prompt_message)) },
        confirmButton = {
            TextButton(onClick = {
                context.openDonationLink()
                onDonate()
            }) { Text(stringResource(R.string.settings_prompt_donate)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_prompt_no)) } },
    )
}
