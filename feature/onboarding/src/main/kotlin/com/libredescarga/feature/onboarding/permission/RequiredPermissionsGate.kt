package com.libredescarga.feature.onboarding.permission

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.feature.onboarding.R
import com.libredescarga.feature.onboarding.component.AppPermissionCard

/**
 * Muestra [content] siempre, pero si [enabled] y falta algún permiso necesario (archivos, notificaciones o
 * mostrar sobre otras apps) lo tapa con una pantalla que explica por qué se necesitan y los pide al tocar el botón.
 * Al conceder el último permiso, la pantalla desaparece sola y la app sigue donde estaba.
 */
@Composable
fun RequiredPermissionsGate(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberPermissionState()
    Box(modifier = modifier.fillMaxSize()) {
        content()
        if (enabled && !state.allGranted) {
            MissingPermissionsScreen(state = state)
        }
    }
}

@Composable
private fun MissingPermissionsScreen(state: PermissionState) {
    // Surface opaca: además de tapar la app, evita que los toques lleguen a lo que hay debajo.
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = LibreIcons.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    text = stringResource(R.string.permissions_required_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(R.string.permissions_required_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.missingRequired.forEach { permission ->
                    AppPermissionCard(permission = permission, state = state)
                }
                Button(onClick = state::requestAllMissing, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.permissions_required_grant))
                }
                Text(
                    text = stringResource(R.string.permissions_required_settings_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
