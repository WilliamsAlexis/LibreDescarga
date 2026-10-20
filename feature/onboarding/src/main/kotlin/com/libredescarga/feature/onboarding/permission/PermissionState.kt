package com.libredescarga.feature.onboarding.permission

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * Estado de los permisos necesarios y forma de pedirlos: de uno en uno, o todos los que faltan en orden.
 * Si el sistema ya no muestra el cuadro (permiso rechazado para siempre), lleva a los ajustes de Android.
 */
@Stable
internal class PermissionState(private val context: Context) {

    var missing: List<AppPermission> by mutableStateOf(AppPermissions.missing(context))
        private set

    /** Faltan solo los obligatorios: las notificaciones son opcionales y no bloquean la app. */
    val missingRequired: List<AppPermission> get() = missing.filter { it.required }

    val allGranted: Boolean get() = missingRequired.isEmpty()

    private val queue = ArrayDeque<AppPermission>()

    // Los asigna rememberPermissionState con los lanzadores de la pantalla.
    var launchStorage: (Array<String>) -> Unit = {}
    var launchNotifications: (String) -> Unit = {}
    var launchSettings: (Intent) -> Unit = {}

    fun refresh() {
        missing = AppPermissions.missing(context)
    }

    /** Pide un permiso concreto. */
    fun request(permission: AppPermission) {
        queue.clear()
        launch(permission)
    }

    /** Pide, uno tras otro, todos los permisos que faltan. */
    fun requestAllMissing() {
        refresh()
        queue.clear()
        queue.addAll(missingRequired)
        next()
    }

    internal fun onResult(permission: AppPermission) {
        refresh()
        if (permission in missing && AppPermissions.needsSettings(context, permission)) {
            // No se puede volver a preguntar: solo se concede desde los ajustes de la app.
            queue.clear()
            launchSettings(AppPermissions.appSettingsIntent(context))
        } else {
            next()
        }
    }

    private fun next() {
        refresh()
        val permission = queue.removeFirstOrNull() ?: return
        if (permission in missing) launch(permission) else next()
    }

    private fun launch(permission: AppPermission) {
        when (permission) {
            AppPermission.STORAGE -> launchStorage(AppPermissions.storagePermissions())
            AppPermission.NOTIFICATIONS -> launchNotifications(android.Manifest.permission.POST_NOTIFICATIONS)
            AppPermission.OVERLAY -> launchSettings(AppPermissions.overlaySettingsIntent(context))
        }
    }
}

@Composable
internal fun rememberPermissionState(): PermissionState {
    val context = LocalContext.current
    val state = remember { PermissionState(context) }

    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { state.onResult(AppPermission.STORAGE) }
    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { state.onResult(AppPermission.NOTIFICATIONS) }
    // Ajustes de Android (sobre otras apps / detalles de la app): al volver se revisa de nuevo.
    var lastSettingsRequest by remember { mutableStateOf<Intent?>(null) }
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val wasOverlay = lastSettingsRequest?.action == android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION
        if (wasOverlay) state.onResult(AppPermission.OVERLAY) else state.refresh()
    }

    state.launchStorage = { storageLauncher.launch(it) }
    state.launchNotifications = { notificationsLauncher.launch(it) }
    state.launchSettings = { intent ->
        lastSettingsRequest = intent
        // Algunos teléfonos no tienen esa pantalla de ajustes: se abren los detalles de la app.
        runCatching { settingsLauncher.launch(intent) }.onFailure {
            runCatching { settingsLauncher.launch(AppPermissions.appSettingsIntent(context)) }
        }
    }

    // Si el usuario concede el permiso desde los ajustes de Android y vuelve, se refresca.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state.refresh() }
    return state
}
