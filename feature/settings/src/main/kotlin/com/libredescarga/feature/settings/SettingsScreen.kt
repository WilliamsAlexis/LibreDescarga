package com.libredescarga.feature.settings

import android.content.Context
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.core.designsystem.theme.supportsDynamicColor
import com.libredescarga.core.designsystem.theme.swatch
import com.libredescarga.core.model.AccentColor
import com.libredescarga.core.model.AudioFormat
import com.libredescarga.core.model.ThemeMode
import com.libredescarga.core.model.VideoQuality
import com.libredescarga.core.ui.label
import com.libredescarga.feature.settings.component.ChoiceDialog
import com.libredescarga.feature.settings.component.ClickRow
import com.libredescarga.feature.settings.component.SettingsScaffold
import com.libredescarga.feature.settings.component.SettingsSection
import com.libredescarga.feature.settings.component.SwitchRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Pantalla "Ajustes": apariencia, descargas, sitios, actualizaciones, datos, apoyo y acerca de. */
@Composable
internal fun SettingsScreen(
    onOpenSupport: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSites: () -> Unit,
    onOpenUpdates: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = uiState.settings
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Diálogo abierto en este momento (tema, calidad o formato); null si no hay ninguno.
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }

    // Muestra los avisos breves de abajo (caché limpiada, copia guardada...).
    val message = uiState.message?.let { settingsMessage(context, it) }
    LaunchedEffect(uiState.message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.onMessageShown()
        }
    }

    // Copia de seguridad en un archivo JSON local, elegido con el selector de archivos de Android.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            viewModel.onExportBackup { json ->
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("No se pudo abrir el archivo")
                    output.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                }
            }
        }
    }
    // Selector de archivos para elegir la copia de seguridad que se quiere restaurar.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.onRestoreBackup {
                withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri) ?: throw IOException("No se pudo abrir el archivo")
                    input.use { it.readTextLimited(MAX_BACKUP_BYTES) }
                }
            }
        }
    }

    // Estructura de la pantalla: barra superior y lista de secciones.
    SettingsScaffold(title = stringResource(R.string.feature_settings_title), onBack = null, snackbarHostState = snackbarHostState) {
        // Sección Apariencia: tema, color dinámico y color de acento.
        SettingsSection(stringResource(R.string.settings_section_appearance)) {
            ClickRow(
                title = stringResource(R.string.settings_theme),
                subtitle = stringResource(uiState.settings.themeMode.labelRes),
                icon = LibreIcons.Palette,
                onClick = { dialog = SettingsDialog.Theme },
            )
            if (supportsDynamicColor) {
                SwitchRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    subtitle = stringResource(R.string.settings_dynamic_color_description),
                    checked = settings.useDynamicColor,
                    onCheckedChange = viewModel::onDynamicColor,
                )
            }
            AccentPicker(selected = settings.accentColor, enabled = !(supportsDynamicColor && settings.useDynamicColor), onSelect = viewModel::onAccent)
        }

        // Sección Descargas: calidad, formato, descargas a la vez, solo Wi-Fi y detección del portapapeles.
        SettingsSection(stringResource(R.string.settings_section_downloads)) {
            ClickRow(
                title = stringResource(R.string.settings_default_video),
                subtitle = settings.defaultVideoQuality.label(),
                onClick = { dialog = SettingsDialog.VideoQuality },
            )
            ClickRow(
                title = stringResource(R.string.settings_default_audio),
                subtitle = settings.defaultAudioFormat.label(),
                onClick = { dialog = SettingsDialog.AudioFormat },
            )
            ConcurrentSlider(value = settings.maxConcurrentDownloads, onChange = viewModel::onMaxConcurrent)
            SwitchRow(
                title = stringResource(R.string.settings_wifi_only),
                subtitle = stringResource(R.string.settings_wifi_only_description),
                checked = settings.wifiOnly,
                onCheckedChange = viewModel::onWifiOnly,
            )
            SwitchRow(
                title = stringResource(R.string.settings_clipboard),
                subtitle = stringResource(R.string.settings_clipboard_description),
                checked = settings.clipboardDetection,
                onCheckedChange = viewModel::onClipboardDetection,
            )
        }

        // Sección Sitios: administrar los accesos de la pantalla de inicio.
        SettingsSection(stringResource(R.string.settings_section_sites)) {
            ClickRow(
                title = stringResource(R.string.settings_sites),
                subtitle = stringResource(R.string.settings_sites_description),
                icon = LibreIcons.Internet,
                onClick = onOpenSites,
            )
        }

        // Sección Actualizaciones.
        SettingsSection(stringResource(R.string.settings_section_updates)) {
            ClickRow(
                title = stringResource(R.string.settings_updates),
                subtitle = stringResource(R.string.settings_updates_description),
                icon = LibreIcons.SystemUpdate,
                onClick = onOpenUpdates,
            )
        }

        // Sección Datos: limpiar la memoria temporal y la copia de seguridad.
        SettingsSection(stringResource(R.string.settings_section_data)) {
            ClickRow(
                title = stringResource(R.string.settings_clear_cache),
                subtitle = uiState.cacheBytes?.let { stringResource(R.string.settings_cache_size, Formatter.formatShortFileSize(context, it)) },
                icon = LibreIcons.Storage,
                enabled = !uiState.busy,
                onClick = viewModel::onClearCache,
            )
            ClickRow(
                title = stringResource(R.string.settings_backup_export),
                subtitle = stringResource(R.string.settings_backup_export_description),
                icon = LibreIcons.Backup,
                enabled = !uiState.busy,
                onClick = { exportLauncher.launch(backupFileName()) },
            )
            ClickRow(
                title = stringResource(R.string.settings_backup_restore),
                subtitle = stringResource(R.string.settings_backup_restore_description),
                icon = LibreIcons.Upload,
                enabled = !uiState.busy,
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            )
        }

        // Sección Apoyo: pantalla de donación voluntaria y opción para ocultar los recordatorios.
        SettingsSection(stringResource(R.string.settings_section_support)) {
            ClickRow(
                title = stringResource(R.string.settings_support),
                subtitle = stringResource(R.string.settings_support_description),
                icon = LibreIcons.Heart,
                onClick = onOpenSupport,
            )
            SwitchRow(
                title = stringResource(R.string.settings_hide_reminders),
                subtitle = stringResource(R.string.settings_hide_reminders_description),
                checked = settings.hideDonationReminders,
                onCheckedChange = viewModel::onHideDonationReminders,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
        ClickRow(
            title = stringResource(R.string.settings_about),
            icon = LibreIcons.Info,
            onClick = onOpenAbout,
        )
    }

    // Muestra el diálogo de elección que corresponda.
    when (dialog) {
        SettingsDialog.Theme -> ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries.map { it to stringResource(it.labelRes) },
            selected = settings.themeMode,
            onSelect = viewModel::onThemeMode,
            onDismiss = { dialog = null },
        )
        SettingsDialog.VideoQuality -> ChoiceDialog(
            title = stringResource(R.string.settings_default_video),
            options = VideoQuality.entries.map { it to it.label() },
            selected = settings.defaultVideoQuality,
            onSelect = viewModel::onVideoQuality,
            onDismiss = { dialog = null },
        )
        SettingsDialog.AudioFormat -> ChoiceDialog(
            title = stringResource(R.string.settings_default_audio),
            options = AudioFormat.entries.map { it to it.label() },
            selected = settings.defaultAudioFormat,
            onSelect = viewModel::onAudioFormat,
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

// Diálogos de elección que puede abrir esta pantalla.
private enum class SettingsDialog { Theme, VideoQuality, AudioFormat }

// Nombre de cada modo de tema.
private val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    }

/** Selector de color de acento (círculos de colores). Se desactiva si se usa el color dinámico del sistema. */
@Composable
private fun AccentPicker(selected: AccentColor, enabled: Boolean, onSelect: (AccentColor) -> Unit) {
    val dark = isSystemInDarkTheme()
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.settings_accent), style = MaterialTheme.typography.bodyLarge)
        if (!enabled) {
            Text(
                stringResource(R.string.settings_accent_disabled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Los círculos pasan a la línea de abajo cuando no caben todos.
        FlowRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AccentColor.entries.forEach { accent ->
                val isSelected = accent == selected
                val name = stringResource(accent.labelRes)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = name }
                        .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton) { onSelect(accent) }
                        .background(accent.swatch(dark), CircleShape)
                        .then(
                            if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        // La palomita es negra sobre colores claros (amarillo, lima...) y blanca sobre los demás.
                        val checkColor = if (accent.swatch(dark).luminance() > 0.45f) {
                            androidx.compose.ui.graphics.Color.Black
                        } else {
                            androidx.compose.ui.graphics.Color.White
                        }
                        Icon(LibreIcons.Granted, contentDescription = null, tint = checkColor)
                    }
                }
            }
        }
    }
}

// Nombre de cada color de acento (lo lee el lector de pantalla).
private val AccentColor.labelRes: Int
    get() = when (this) {
        AccentColor.TEAL -> R.string.settings_accent_teal
        AccentColor.CORAL -> R.string.settings_accent_coral
        AccentColor.INDIGO -> R.string.settings_accent_indigo
        AccentColor.AMBER -> R.string.settings_accent_amber
        AccentColor.ROSE -> R.string.settings_accent_rose
        AccentColor.GREEN -> R.string.settings_accent_green
        AccentColor.BLUE -> R.string.settings_accent_blue
        AccentColor.PURPLE -> R.string.settings_accent_purple
        AccentColor.RED -> R.string.settings_accent_red
        AccentColor.ORANGE -> R.string.settings_accent_orange
        AccentColor.CYAN -> R.string.settings_accent_cyan
        AccentColor.PINK -> R.string.settings_accent_pink
        AccentColor.BROWN -> R.string.settings_accent_brown
        AccentColor.SLATE -> R.string.settings_accent_slate
        AccentColor.YELLOW -> R.string.settings_accent_yellow
        AccentColor.LIME -> R.string.settings_accent_lime
        AccentColor.SKY -> R.string.settings_accent_sky
        AccentColor.LAVENDER -> R.string.settings_accent_lavender
        AccentColor.GRAPHITE -> R.string.settings_accent_graphite
    }

/** Deslizador para elegir cuántas descargas se hacen a la vez (de 1 a 5). Guarda al soltar. */
@Composable
private fun ConcurrentSlider(value: Int, onChange: (Int) -> Unit) {
    var current by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_concurrent), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(current.roundToInt().toString(), style = MaterialTheme.typography.titleMedium)
        }
        Slider(
            value = current,
            onValueChange = { current = it },
            onValueChangeFinished = { onChange(current.roundToInt()) },
            valueRange = 1f..5f,
            steps = 3,
        )
    }
}

// Texto del aviso que corresponde a cada resultado.
private fun settingsMessage(context: Context, message: SettingsMessage): String = when (message) {
    is SettingsMessage.CacheCleared ->
        context.getString(R.string.settings_message_cache, Formatter.formatShortFileSize(context, message.freedBytes))
    SettingsMessage.BackupSaved -> context.getString(R.string.settings_message_backup_saved)
    is SettingsMessage.BackupRestored -> context.getString(
        R.string.settings_message_backup_restored,
        message.summary.bookmarks,
        message.summary.playlists,
        message.summary.searches,
    )
    SettingsMessage.BackupInvalid -> context.getString(R.string.settings_message_backup_invalid)
    SettingsMessage.BackupIoError -> context.getString(R.string.settings_message_backup_io)
}

// Nombre sugerido para la copia, con la fecha de hoy.
private fun backupFileName(): String =
    "LibreDescarga-${SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())}.json"

// Tamaño máximo aceptado para un archivo de copia (unos 5 MB).
private const val MAX_BACKUP_BYTES = 5_000_001

/** Lee como máximo [maxBytes] bytes: un archivo enorme se corta y luego se rechaza por tamaño. */
private fun InputStream.readTextLimited(maxBytes: Int): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    while (output.size() < maxBytes) {
        val read = read(buffer, 0, minOf(buffer.size, maxBytes - output.size()))
        if (read < 0) break
        output.write(buffer, 0, read)
    }
    return output.toString(Charsets.UTF_8.name())
}
