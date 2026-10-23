package com.libredescarga.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.libredescarga.core.designsystem.theme.LibreTheme
import com.libredescarga.core.designsystem.theme.isDarkTheme
import com.libredescarga.core.domain.usecase.ImageDownloads
import com.libredescarga.core.model.AnalisisPerf
import com.libredescarga.core.security.UrlValidator
import com.libredescarga.feature.analysis.MediaAnalysisSheet
import dagger.hilt.android.AndroidEntryPoint

/**
 * Ventana que aparece al usar "Compartir" → LibreDescarga desde otra app (YouTube, YouTube Music, el navegador...).
 *
 * Es transparente: la hoja de análisis (formato y calidad) se muestra encima de la app desde la que se comparte,
 * sin abrir LibreDescarga. Al elegir y descargar, la ventana se cierra y la descarga sigue en segundo plano.
 */
@AndroidEntryPoint
class ShareDownloadActivity : ComponentActivity() {

    private val viewModel: ShareDownloadViewModel by viewModels()

    // Enlace que se está atendiendo; cambia si llega otro "Compartir" con la ventana abierta.
    private var link by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        val requestNotifications = notificationPermissionRequester()
        if (!acceptIntent(intent)) return
        // Opcional: permite ver el avance de la descarga en las notificaciones.
        requestNotifications()

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val ready = state as? ShareUiState.Ready
            val current = link
            if (ready != null && current != null) {
                if (ready.needsOnboarding) {
                    // Antes de descargar hay que aceptar la bienvenida y los términos: se abre la app completa.
                    LaunchedEffect(Unit) {
                        openFullApp(current)
                        finish()
                    }
                } else {
                    LibreTheme(
                        darkTheme = isDarkTheme(ready.settings.themeMode),
                        dynamicColor = ready.settings.useDynamicColor,
                        accent = ready.settings.accentColor,
                    ) {
                        ShareContent(url = current)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptIntent(intent)
    }

    /** Lee el enlace compartido. Si no hay un enlace válido, avisa y cierra. Devuelve false si se cerró. */
    private fun acceptIntent(intent: Intent?): Boolean {
        val text = if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT) else null
        val candidate = text?.let { UrlValidator.extractFirstUrl(it) ?: it }
        val valid = candidate?.let { UrlValidator.validate(it) as? UrlValidator.Result.Valid }
        if (valid == null) {
            Toast.makeText(applicationContext, R.string.share_invalid_link, Toast.LENGTH_LONG).show()
            finish()
            return false
        }
        AnalisisPerf.mark("entrada: enlace recibido por Compartir (ventana flotante)", AnalisisPerf.hostOf(valid.url))
        link = valid.url
        return true
    }

    @Composable
    private fun ShareContent(url: String) {
        if (ImageDownloads.isImageUrl(url)) {
            // Un enlace directo a una imagen no tiene nada que elegir: se descarga y listo.
            LaunchedEffect(url) {
                val queued = runCatching { viewModel.downloadImage(url) }.getOrDefault(0)
                showToast(if (queued > 0) R.string.share_queued else R.string.share_failed)
                finish()
            }
        } else {
            MediaAnalysisSheet(
                url = url,
                onDismiss = { finish() },
                onQueued = { showToast(R.string.share_queued) },
            )
        }
    }

    private fun showToast(message: Int) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    }

    /** Abre LibreDescarga completa con el mismo enlace (cuando falta aceptar la bienvenida). */
    private fun openFullApp(url: String) {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, url)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
}
