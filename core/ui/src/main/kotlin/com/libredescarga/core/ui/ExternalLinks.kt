package com.libredescarga.core.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.libredescarga.core.security.UrlValidator

/**
 * Abre un enlace http(s) en el navegador externo (o en la app que lo maneje).
 * Nunca se usa un WebView de la app para esto. Devuelve false si no hay
 * ninguna app capaz de abrirlo o el enlace no es seguro.
 */
fun Context.openExternalLink(url: String): Boolean {
    val valid = UrlValidator.validate(url) as? UrlValidator.Result.Valid ?: return false
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(valid.url))
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
