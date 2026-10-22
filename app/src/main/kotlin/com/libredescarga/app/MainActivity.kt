package com.libredescarga.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.libredescarga.app.ui.LibreDescargaAppUi
import com.libredescarga.core.config.AppConfig
import com.libredescarga.core.designsystem.theme.LibreTheme
import com.libredescarga.core.domain.settings.DonationPrompt
import com.libredescarga.core.designsystem.theme.isDarkTheme
import com.libredescarga.feature.onboarding.permission.RequiredPermissionsGate
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Actividad única. Extiende AppCompatActivity para poder cambiar el idioma
 * de la app (per-app language) también en Android 12 o anterior.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val requestNotifications = notificationPermissionRequester()
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        var uiState: MainActivityUiState by mutableStateOf(MainActivityUiState.Loading)
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState = it }
            }
        }

        // El splash se mantiene hasta leer las preferencias (tema y bienvenida).
        splashScreen.setKeepOnScreenCondition { uiState is MainActivityUiState.Loading }

        // Un intent ya atendido no se repite al recrear la actividad (por ejemplo, al girar).
        if (savedInstanceState == null) handleIntent(intent)

        enableEdgeToEdge()

        setContent {
            val state = uiState
            if (state is MainActivityUiState.Success) {
                val darkTheme = isDarkTheme(state.settings.themeMode)
                val pendingLink by viewModel.pendingLink.collectAsStateWithLifecycle()

                // Con la bienvenida terminada se pide el permiso de notificaciones (opcional) para mostrar el avance.
                LaunchedEffect(state.showOnboarding) {
                    if (!state.showOnboarding) requestNotifications()
                }

                DisposableEffect(darkTheme) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(
                            Color.TRANSPARENT,
                            Color.TRANSPARENT,
                        ) { darkTheme },
                        navigationBarStyle = SystemBarStyle.auto(
                            LIGHT_SCRIM,
                            DARK_SCRIM,
                        ) { darkTheme },
                    )
                    onDispose {}
                }

                LibreTheme(
                    darkTheme = darkTheme,
                    dynamicColor = state.settings.useDynamicColor,
                    accent = state.settings.accentColor,
                ) {
                    // Durante la bienvenida los permisos se piden en su paso; después son obligatorios.
                    RequiredPermissionsGate(enabled = !state.showOnboarding) {
                        LibreDescargaAppUi(
                            showOnboarding = state.showOnboarding,
                            hasPendingLink = pendingLink != null,
                            showDonationPrompt = DonationPrompt.shouldShow(state.settings, AppConfig.DONATION_PROMPT_THRESHOLD),
                            onDonationPromptAnswered = viewModel::onDonationPromptAnswered,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** "Compartir con LibreDescarga" desde otras apps (ACTION_SEND con texto). */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            viewModel.onSharedText(intent.getStringExtra(Intent.EXTRA_TEXT))
        }
    }

    private companion object {
        val LIGHT_SCRIM = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}
