package com.libredescarga.feature.vault

import android.content.IntentSender
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.libredescarga.core.model.VaultItem
import com.libredescarga.feature.vault.component.AddFromLibrarySheet
import com.libredescarga.feature.vault.component.CreateCredentialScreen
import com.libredescarga.feature.vault.component.LockedScreen
import com.libredescarga.feature.vault.component.RecoveryAnswerScreen
import com.libredescarga.feature.vault.component.SetupQuestionScreen
import com.libredescarga.feature.vault.component.VaultContent

@Composable
internal fun VaultScreen(
    onOpenVideo: (VaultItem) -> Unit,
    viewModel: VaultViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnOpenVideo by rememberUpdatedState(onOpenVideo)
    val biometricAvailable = remember(context) { context.biometricAvailable() }

    // Dentro de la carpeta privada no se permiten capturas ni grabación de pantalla,
    // y la vista previa de apps recientes queda en blanco.
    DisposableEffect(activity) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is VaultEvent.OpenVideo -> currentOnOpenVideo(event.item)
            }
        }
    }

    val message = uiState.message?.let { vaultMessage(it) }
    LaunchedEffect(uiState.message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.onMessageShown()
        }
    }

    // Diálogo del sistema para borrar los originales de la galería (Android 10 o superior).
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        viewModel.onConsentResult(granted = it.resultCode == android.app.Activity.RESULT_OK)
    }
    LaunchedEffect(uiState.pendingConsent) {
        (uiState.pendingConsent as? IntentSender)?.let { consentLauncher.launch(IntentSenderRequest.Builder(it).build()) }
    }

    val biometricTitle = stringResource(R.string.vault_biometric_title)
    val biometricSubtitle = stringResource(R.string.vault_biometric_subtitle)
    val biometricCancel = stringResource(R.string.vault_cancel)
    fun promptUnlock() {
        activity?.promptBiometric(biometricTitle, biometricSubtitle, biometricCancel, viewModel::onBiometricSuccess)
    }

    // Al llegar a la pantalla de acceso se ofrece la huella enseguida.
    LaunchedEffect(uiState.mode, uiState.settings.biometricEnabled) {
        if (uiState.mode == VaultMode.LOCKED && uiState.settings.biometricEnabled && biometricAvailable) promptUnlock()
    }

    BackHandler(enabled = uiState.mode == VaultMode.SETUP_QUESTION) { viewModel.onBackToCredential() }
    BackHandler(enabled = uiState.mode == VaultMode.CHANGE_CREDENTIAL) { viewModel.onCancelChangeCredential() }
    BackHandler(enabled = uiState.mode == VaultMode.RECOVERY_ANSWER) { viewModel.onRecoveryCancel() }

    val entry = uiState.entry
    when (uiState.mode) {
        VaultMode.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

        VaultMode.SETUP_CREDENTIAL -> CreateCredentialScreen(
            title = stringResource(R.string.vault_setup_title),
            entry = entry,
            onTypeSelected = viewModel::onTypeSelected,
            onDigit = viewModel::onPinDigit,
            onBackspace = viewModel::onPinBackspace,
            onSubmit = viewModel::onPinSubmit,
            onPattern = viewModel::onPatternEntered,
            onRestart = viewModel::onRestartEntry,
            onCancel = null,
        )

        VaultMode.SETUP_QUESTION -> SetupQuestionScreen(
            question = uiState.question,
            answer = uiState.answer,
            error = uiState.setupError,
            biometricAvailable = biometricAvailable,
            biometric = uiState.setupBiometric,
            onQuestionChange = viewModel::onQuestionChange,
            onAnswerChange = viewModel::onAnswerChange,
            onBiometricChange = viewModel::onSetupBiometricChange,
            onFinish = viewModel::onFinishSetup,
            onBack = viewModel::onBackToCredential,
        )

        VaultMode.LOCKED -> LockedScreen(
            type = uiState.activeType,
            entry = entry,
            biometricAvailable = biometricAvailable,
            biometricEnabled = uiState.settings.biometricEnabled,
            onDigit = viewModel::onPinDigit,
            onBackspace = viewModel::onPinBackspace,
            onSubmit = viewModel::onPinSubmit,
            onPattern = viewModel::onPatternEntered,
            onBiometric = ::promptUnlock,
            onForgot = viewModel::onForgotCredential,
        )

        VaultMode.CHANGE_CREDENTIAL -> CreateCredentialScreen(
            title = stringResource(R.string.vault_change_title),
            entry = entry,
            onTypeSelected = viewModel::onTypeSelected,
            onDigit = viewModel::onPinDigit,
            onBackspace = viewModel::onPinBackspace,
            onSubmit = viewModel::onPinSubmit,
            onPattern = viewModel::onPatternEntered,
            onRestart = viewModel::onRestartEntry,
            onCancel = viewModel::onCancelChangeCredential,
        )

        VaultMode.RECOVERY_ANSWER -> RecoveryAnswerScreen(
            question = uiState.settings.securityQuestion,
            answer = uiState.answer,
            error = uiState.recoveryError,
            onAnswerChange = viewModel::onRecoveryAnswerChange,
            onSubmit = viewModel::onRecoverySubmit,
            onCancel = viewModel::onRecoveryCancel,
            onEraseEverything = viewModel::onEraseEverything,
        )

        VaultMode.RECOVERY_NEW_CREDENTIAL -> CreateCredentialScreen(
            title = stringResource(R.string.vault_recovery_new_title),
            entry = entry,
            onTypeSelected = viewModel::onTypeSelected,
            onDigit = viewModel::onPinDigit,
            onBackspace = viewModel::onPinBackspace,
            onSubmit = viewModel::onPinSubmit,
            onPattern = viewModel::onPatternEntered,
            onRestart = viewModel::onRestartEntry,
            onCancel = viewModel::onRecoveryCancel,
        )

        VaultMode.UNLOCKED -> {
            VaultContent(
                items = uiState.items,
                settings = uiState.settings,
                biometricAvailable = biometricAvailable,
                snackbarHostState = snackbarHostState,
                onItemClick = viewModel::onItemClick,
                onRestore = viewModel::onRestore,
                onDelete = viewModel::onDelete,
                onRename = viewModel::onRename,
                onAdd = viewModel::onShowAddFromLibrary,
                onLock = viewModel::onLockNow,
                onChangeCredential = viewModel::onStartChangeCredential,
                // Activar la huella pide confirmarla antes; desactivarla es inmediato.
                onBiometricChange = { enable ->
                    if (enable) {
                        activity?.promptBiometric(biometricTitle, biometricSubtitle, biometricCancel) {
                            viewModel.onBiometricEnabledChange(true)
                        }
                    } else {
                        viewModel.onBiometricEnabledChange(false)
                    }
                },
                onEncryptChange = viewModel::onEncryptChange,
                onDownloadsChange = viewModel::onDownloadsToVaultChange,
            )
            uiState.libraryChoices?.let { choices ->
                AddFromLibrarySheet(choices, onMove = viewModel::onMoveFromLibrary, onDismiss = viewModel::onDismissAddFromLibrary)
            }
        }
    }
}

@Composable
private fun vaultMessage(message: VaultMessage): String = when (message) {
    VaultMessage.CredentialChanged -> stringResource(R.string.vault_message_changed)
    VaultMessage.Restored -> stringResource(R.string.vault_message_restored)
    VaultMessage.RestoreFailed -> stringResource(R.string.vault_message_restore_failed)
    VaultMessage.Deleted -> stringResource(R.string.vault_message_deleted)
    VaultMessage.Erased -> stringResource(R.string.vault_message_erased)
    VaultMessage.OriginalsKept -> stringResource(R.string.vault_message_originals_kept)
    is VaultMessage.Moved -> when {
        message.count == 0 -> stringResource(R.string.vault_message_move_failed)
        message.failed > 0 -> pluralStringResource(R.plurals.vault_message_moved_partial, message.count, message.count, message.failed)
        else -> pluralStringResource(R.plurals.vault_message_moved, message.count, message.count)
    }
}
