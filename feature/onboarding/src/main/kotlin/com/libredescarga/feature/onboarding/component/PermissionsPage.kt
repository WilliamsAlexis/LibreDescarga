package com.libredescarga.feature.onboarding.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.feature.onboarding.R
import com.libredescarga.feature.onboarding.permission.AppPermission
import com.libredescarga.feature.onboarding.permission.PermissionState
import com.libredescarga.feature.onboarding.permission.rememberPermissionState

internal enum class PermissionStatus { GRANTED, REQUESTABLE, AUTOMATIC, ON_DEMAND }

/** Paso 2: permisos que usa la app, explicados. Los de archivos, notificaciones y "sobre otras apps" se piden aquí. */
@Composable
internal fun PermissionsPage(modifier: Modifier = Modifier) {
    val state = rememberPermissionState()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_permissions_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.onboarding_permissions_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppPermission.entries.forEach { permission ->
            AppPermissionCard(permission = permission, state = state)
        }
        PermissionCard(
            icon = LibreIcons.Internet,
            title = stringResource(R.string.onboarding_permission_internet_title),
            description = stringResource(R.string.onboarding_permission_internet_body),
            status = PermissionStatus.AUTOMATIC,
        )
        PermissionCard(
            icon = LibreIcons.SystemUpdate,
            title = stringResource(R.string.onboarding_permission_updates_title),
            description = stringResource(R.string.onboarding_permission_updates_body),
            status = PermissionStatus.ON_DEMAND,
        )
    }
}

/** Tarjeta de uno de los permisos necesarios, con su botón para pedirlo. */
@Composable
internal fun AppPermissionCard(permission: AppPermission, state: PermissionState) {
    val (icon, title, description) = when (permission) {
        AppPermission.STORAGE -> Triple(
            LibreIcons.Media,
            stringResource(R.string.onboarding_permission_media_title),
            stringResource(R.string.onboarding_permission_media_body),
        )
        AppPermission.NOTIFICATIONS -> Triple(
            LibreIcons.Notifications,
            stringResource(R.string.onboarding_permission_notifications_title),
            stringResource(R.string.onboarding_permission_notifications_body),
        )
        AppPermission.OVERLAY -> Triple(
            LibreIcons.Overlay,
            stringResource(R.string.onboarding_permission_overlay_title),
            stringResource(R.string.onboarding_permission_overlay_body),
        )
    }
    PermissionCard(
        icon = icon,
        title = title,
        description = description,
        status = if (permission in state.missing) PermissionStatus.REQUESTABLE else PermissionStatus.GRANTED,
        onRequest = { state.request(permission) },
    )
}

@Composable
internal fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    status: PermissionStatus,
    onRequest: () -> Unit = {},
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PermissionStatusLabel(status = status, onRequest = onRequest)
            }
        }
    }
}

@Composable
private fun PermissionStatusLabel(status: PermissionStatus, onRequest: () -> Unit) {
    when (status) {
        PermissionStatus.REQUESTABLE -> FilledTonalButton(onClick = onRequest) {
            Text(stringResource(R.string.onboarding_permission_allow))
        }

        PermissionStatus.GRANTED -> Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = LibreIcons.Granted,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.onboarding_permission_granted),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        PermissionStatus.AUTOMATIC -> StatusText(stringResource(R.string.onboarding_permission_automatic))
        PermissionStatus.ON_DEMAND -> StatusText(stringResource(R.string.onboarding_permission_on_demand))
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
