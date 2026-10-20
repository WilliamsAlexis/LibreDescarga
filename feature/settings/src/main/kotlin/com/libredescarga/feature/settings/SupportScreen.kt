package com.libredescarga.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.feature.settings.component.SettingsScaffold
import kotlinx.coroutines.launch

/**
 * "Apoyar al creador": una donación voluntaria por PayPal que no desbloquea nada.
 * La app es idéntica para quien dona y para quien no.
 */
@Composable
internal fun SupportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val copiedMessage = stringResource(R.string.settings_support_copied)
    val noBrowserMessage = stringResource(R.string.settings_support_no_browser)
    val copyLabel = stringResource(R.string.settings_support_copy)
    val shareTitle = stringResource(R.string.settings_support_share)

    fun copyLink() {
        context.copyDonationLink()
        scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
    }

    SettingsScaffold(
        title = stringResource(R.string.settings_support),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(LibreIcons.Heart, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
            Text(
                text = stringResource(R.string.settings_support_message),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = {
                    if (!context.openDonationLink()) {
                        // Sin navegador: se avisa y se ofrece copiar el enlace para abrirlo en otro lado.
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(noBrowserMessage, actionLabel = copyLabel, duration = SnackbarDuration.Long)
                            if (result == SnackbarResult.ActionPerformed) copyLink()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text(stringResource(R.string.settings_support_donate)) }
            OutlinedButton(onClick = ::copyLink, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(LibreIcons.Copy, contentDescription = null)
                Text(copyLabel, modifier = Modifier.padding(start = 8.dp))
            }
            TextButton(onClick = { context.shareDonationLink(shareTitle) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(LibreIcons.Share, contentDescription = null)
                Text(shareTitle, modifier = Modifier.padding(start = 8.dp))
            }
            Text(
                text = stringResource(R.string.settings_support_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
