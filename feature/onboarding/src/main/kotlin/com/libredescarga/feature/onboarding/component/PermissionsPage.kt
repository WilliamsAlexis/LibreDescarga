package com.libredescarga.feature.onboarding.component

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.feature.onboarding.R

private enum class PermissionStatus { GRANTED, REQUESTABLE, AUTOMATIC, ON_DEMAND }

/** Paso 2: permisos que usa la app, explicados. Ninguno es obligatorio para continuar. */
@Composable
internal fun PermissionsPage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var notificationsGranted by remember { mutableStateOf(context.hasNotificationPermission()) }
    var mediaGranted by remember { mutableStateOf(context.hasMediaPermission()) }

    // Si el usuario vuelve desde los ajustes de Android, se refresca el estado.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsGranted = context.hasNotificationPermission()
        mediaGranted = context.hasMediaPermission()
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationsGranted = granted }

    val mediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { mediaGranted = context.hasMediaPermission() }

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
        PermissionCard(
            icon = LibreIcons.Notifications,
            title = stringResource(R.string.onboarding_permission_notifications_title),
            description = stringResource(R.string.onboarding_permission_notifications_body),
            status = if (notificationsGranted) PermissionStatus.GRANTED else PermissionStatus.REQUESTABLE,
            onRequest = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )
        PermissionCard(
            icon = LibreIcons.Media,
            title = stringResource(R.string.onboarding_permission_media_title),
            description = stringResource(R.string.onboarding_permission_media_body),
            status = if (mediaGranted) PermissionStatus.GRANTED else PermissionStatus.REQUESTABLE,
            onRequest = { mediaLauncher.launch(mediaPermissions()) },
        )
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

@Composable
private fun PermissionCard(
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

private fun Context.isGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** Antes de Android 13 las notificaciones no necesitan permiso. */
private fun Context.hasNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        isGranted(Manifest.permission.POST_NOTIFICATIONS)

private fun mediaPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

private fun Context.hasMediaPermission(): Boolean = mediaPermissions().all { isGranted(it) }
