package com.libredescarga.feature.onboarding.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.libredescarga.core.designsystem.component.LibreLogo
import com.libredescarga.core.designsystem.component.rememberAppName
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.feature.onboarding.R

/** Paso 1: bienvenida y principios del producto. */
@Composable
internal fun WelcomePage(modifier: Modifier = Modifier) {
    val appName = rememberAppName()
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LibreLogo(
            size = 112.dp,
            contentDescription = stringResource(R.string.onboarding_logo_content_description, appName),
        )
        Text(
            text = stringResource(R.string.onboarding_welcome_title, appName),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.onboarding_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        PrincipleRow(
            icon = LibreIcons.Free,
            title = stringResource(R.string.onboarding_principle_free_title),
            body = stringResource(R.string.onboarding_principle_free_body),
        )
        PrincipleRow(
            icon = LibreIcons.NoAds,
            title = stringResource(R.string.onboarding_principle_no_ads_title),
            body = stringResource(R.string.onboarding_principle_no_ads_body),
        )
        PrincipleRow(
            icon = LibreIcons.NoAccount,
            title = stringResource(R.string.onboarding_principle_no_account_title),
            body = stringResource(R.string.onboarding_principle_no_account_body),
        )
        PrincipleRow(
            icon = LibreIcons.NoTracking,
            title = stringResource(R.string.onboarding_principle_no_tracking_title),
            body = stringResource(R.string.onboarding_principle_no_tracking_body),
        )
    }
}

@Composable
private fun PrincipleRow(icon: ImageVector, title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
