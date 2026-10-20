package com.libredescarga.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource

/**
 * Pantalla temporal para las secciones que se implementan en fases
 * posteriores del desarrollo. Cada fase la reemplaza por la pantalla real.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpcomingFeatureScreen(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(title = { Text(title) })
        EmptyState(
            icon = icon,
            title = title,
            message = stringResource(R.string.core_ui_upcoming_feature),
        )
    }
}
