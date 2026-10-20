package com.libredescarga.feature.vault.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.core.model.CredentialType
import com.libredescarga.feature.vault.CredentialEntry
import com.libredescarga.feature.vault.EntryError
import com.libredescarga.feature.vault.EntryStep
import com.libredescarga.feature.vault.R
import com.libredescarga.feature.vault.SetupError
import kotlinx.coroutines.delay
import java.util.Locale

/** Contenedor común de las pantallas de acceso: centrado, con desplazamiento si no cabe. */
@Composable
private fun AccessLayout(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            content()
        }
    }
}

/** Entrada del PIN o del patrón, con el mensaje de error y la cuenta atrás si hay que esperar. */
@Composable
internal fun CredentialInput(
    type: CredentialType,
    entry: CredentialEntry,
    error: EntryError?,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    onPattern: (List<Int>) -> Unit,
) {
    // Si hubo demasiados intentos fallidos, hasta qué hora está bloqueado el acceso.
    val lockedUntil = (error as? EntryError.LockedOut)?.untilMs
    val remaining = lockedUntil?.let { rememberRemainingMillis(it) } ?: 0L
    // Verdadero mientras quede tiempo de espera; en ese caso se desactiva la entrada.
    val locked = lockedUntil != null && remaining > 0

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (type) {
            CredentialType.PIN -> PinPad(entry.pin, enabled = !locked, onDigit = onDigit, onBackspace = onBackspace, onSubmit = onSubmit)
            CredentialType.PATTERN -> PatternPad(enabled = !locked, onComplete = onPattern)
        }
        val message = when {
            locked -> stringResource(R.string.vault_error_locked, formatCountdown(remaining))
            error is EntryError.PinTooShort -> stringResource(R.string.vault_error_pin_short)
            error is EntryError.PatternTooShort -> stringResource(R.string.vault_error_pattern_short)
            error is EntryError.Mismatch -> stringResource(R.string.vault_error_mismatch)
            error is EntryError.Wrong -> stringResource(
                if (type == CredentialType.PIN) R.string.vault_error_wrong_pin else R.string.vault_error_wrong_pattern,
                error.attemptsBeforeLock,
            )
            else -> null
        }
        Box(modifier = Modifier.heightIn(min = 40.dp), contentAlignment = Alignment.Center) {
            message?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Pantalla de desbloqueo. */
@Composable
internal fun LockedScreen(
    type: CredentialType,
    entry: CredentialEntry,
    biometricAvailable: Boolean,
    biometricEnabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    onPattern: (List<Int>) -> Unit,
    onBiometric: () -> Unit,
    onForgot: () -> Unit,
) {
    AccessLayout(LibreIcons.Lock, stringResource(R.string.vault_locked_title)) {
        Text(
            stringResource(if (type == CredentialType.PIN) R.string.vault_locked_pin else R.string.vault_locked_pattern),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CredentialInput(type, entry, entry.error, onDigit, onBackspace, onSubmit, onPattern)
        if (biometricEnabled && biometricAvailable) {
            OutlinedButton(onClick = onBiometric) {
                Icon(LibreIcons.Fingerprint, contentDescription = null)
                Text(stringResource(R.string.vault_use_biometric), modifier = Modifier.padding(start = 8.dp))
            }
        }
        TextButton(onClick = onForgot) {
            Text(stringResource(if (type == CredentialType.PIN) R.string.vault_forgot_pin else R.string.vault_forgot_pattern))
        }
    }
}

/** Crear o cambiar el PIN o el patrón (se escribe dos veces). */
@Composable
internal fun CreateCredentialScreen(
    title: String,
    entry: CredentialEntry,
    onTypeSelected: (CredentialType) -> Unit,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    onPattern: (List<Int>) -> Unit,
    onRestart: () -> Unit,
    onCancel: (() -> Unit)?,
) {
    AccessLayout(LibreIcons.Shield, title) {
        if (entry.step == EntryStep.ENTER) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(CredentialType.PIN, CredentialType.PATTERN).forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = entry.type == type,
                        onClick = { onTypeSelected(type) },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                        label = { Text(stringResource(if (type == CredentialType.PIN) R.string.vault_type_pin else R.string.vault_type_pattern)) },
                    )
                }
            }
        }
        Text(
            text = stringResource(
                when {
                    entry.step == EntryStep.ENTER && entry.type == CredentialType.PIN -> R.string.vault_create_pin
                    entry.step == EntryStep.ENTER -> R.string.vault_create_pattern
                    entry.type == CredentialType.PIN -> R.string.vault_confirm_pin
                    else -> R.string.vault_confirm_pattern
                },
            ),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (entry.weakPin && entry.step == EntryStep.CONFIRM) {
            Text(stringResource(R.string.vault_weak_pin), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
        CredentialInput(entry.type, entry, entry.error, onDigit, onBackspace, onSubmit, onPattern)
        if (entry.step == EntryStep.CONFIRM) {
            TextButton(onClick = onRestart) { Text(stringResource(R.string.vault_start_over)) }
        }
        onCancel?.let { TextButton(onClick = it) { Text(stringResource(R.string.vault_cancel)) } }
    }
}

/** Pregunta de seguridad para recuperar el acceso, y huella o rostro si el teléfono los tiene. */
@Composable
internal fun SetupQuestionScreen(
    question: String,
    answer: String,
    error: SetupError?,
    biometricAvailable: Boolean,
    biometric: Boolean,
    onQuestionChange: (String) -> Unit,
    onAnswerChange: (String) -> Unit,
    onBiometricChange: (Boolean) -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit,
) {
    // Preguntas sugeridas; el usuario también puede escribir la suya.
    val presets = stringArrayResource(R.array.vault_security_questions)
    var custom by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    AccessLayout(LibreIcons.Shield, stringResource(R.string.vault_question_title)) {
        Text(
            stringResource(R.string.vault_question_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(
                    text = question.ifBlank { stringResource(R.string.vault_question_choose) },
                    maxLines = 2,
                    modifier = Modifier.weight(1f),
                )
                Icon(LibreIcons.ExpandMore, contentDescription = null)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                presets.forEach { preset ->
                    DropdownMenuItem(text = { Text(preset) }, onClick = { custom = false; onQuestionChange(preset); menu = false })
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.vault_question_custom)) },
                    onClick = { custom = true; onQuestionChange(""); menu = false },
                )
            }
        }
        if (custom) {
            OutlinedTextField(
                value = question,
                onValueChange = onQuestionChange,
                label = { Text(stringResource(R.string.vault_question_custom_label)) },
                isError = error == SetupError.QuestionMissing,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = answer,
            onValueChange = onAnswerChange,
            label = { Text(stringResource(R.string.vault_answer)) },
            singleLine = true,
            isError = error == SetupError.AnswerTooShort,
            supportingText = { Text(stringResource(R.string.vault_answer_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth(),
        )
        when (error) {
            SetupError.QuestionMissing -> ErrorText(stringResource(R.string.vault_error_question_missing))
            SetupError.AnswerTooShort -> ErrorText(stringResource(R.string.vault_error_answer_short))
            null -> Unit
        }
        if (biometricAvailable) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = biometric, role = Role.Switch, onValueChange = onBiometricChange)
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.vault_setup_biometric), modifier = Modifier.weight(1f))
                Switch(checked = biometric, onCheckedChange = null)
            }
        }
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(stringResource(R.string.vault_finish))
        }
        TextButton(onClick = onBack) { Text(stringResource(R.string.vault_back)) }
    }
}

/** "Olvidé mi PIN": responder la pregunta de seguridad. */
@Composable
internal fun RecoveryAnswerScreen(
    question: String?,
    answer: String,
    error: EntryError?,
    onAnswerChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onEraseEverything: () -> Unit,
) {
    var confirmErase by rememberSaveable { mutableStateOf(false) }
    var understood by rememberSaveable { mutableStateOf(false) }
    // Igual que arriba: bloqueo temporal tras demasiados intentos.
    val lockedUntil = (error as? EntryError.LockedOut)?.untilMs
    val remaining = lockedUntil?.let { rememberRemainingMillis(it) } ?: 0L
    val locked = lockedUntil != null && remaining > 0

    AccessLayout(LibreIcons.Shield, stringResource(R.string.vault_recovery_title)) {
        Text(question.orEmpty(), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        OutlinedTextField(
            value = answer,
            onValueChange = onAnswerChange,
            label = { Text(stringResource(R.string.vault_answer)) },
            singleLine = true,
            isError = error != null,
            enabled = !locked,
            modifier = Modifier.fillMaxWidth(),
        )
        when {
            locked -> ErrorText(stringResource(R.string.vault_error_locked, formatCountdown(remaining)))
            error is EntryError.Wrong -> ErrorText(stringResource(R.string.vault_error_wrong_answer, error.attemptsBeforeLock))
        }
        Button(
            onClick = onSubmit,
            enabled = answer.isNotBlank() && !locked,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text(stringResource(R.string.vault_continue)) }
        TextButton(onClick = onCancel) { Text(stringResource(R.string.vault_cancel)) }
        TextButton(onClick = { confirmErase = true }) {
            Text(stringResource(R.string.vault_erase_everything), color = MaterialTheme.colorScheme.error)
        }
    }

    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false; understood = false },
            icon = { Icon(LibreIcons.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.vault_erase_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.vault_erase_message))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(value = understood, role = Role.Checkbox, onValueChange = { understood = it })
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = understood, onCheckedChange = null)
                        Text(stringResource(R.string.vault_erase_understood), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { confirmErase = false; understood = false; onEraseEverything() }, enabled = understood) {
                    Text(stringResource(R.string.vault_erase_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmErase = false; understood = false }) { Text(stringResource(R.string.vault_cancel)) } },
        )
    }
}

/** Mensaje de error en rojo. */
@Composable
private fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
}

/** Milisegundos que faltan hasta [untilMs]; se actualiza cada segundo. */
@Composable
private fun rememberRemainingMillis(untilMs: Long): Long {
    val remaining by produceState(initialValue = (untilMs - System.currentTimeMillis()).coerceAtLeast(0), untilMs) {
        while (true) {
            value = (untilMs - System.currentTimeMillis()).coerceAtLeast(0)
            if (value == 0L) break
            delay(1_000)
        }
    }
    return remaining
}

/** Convierte milisegundos a texto de minutos y segundos para mostrar la espera. */
private fun formatCountdown(millis: Long): String {
    val totalSeconds = (millis + 999) / 1000
    return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
