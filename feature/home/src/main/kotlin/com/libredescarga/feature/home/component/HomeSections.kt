package com.libredescarga.feature.home.component

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.core.model.Site
import com.libredescarga.feature.home.HomeCategory
import com.libredescarga.feature.home.R
import kotlin.math.absoluteValue

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** "¿Descargar este enlace?" al volver a la app con un enlace copiado. */
@Composable
internal fun ClipboardCard(url: String, onAnalyze: () -> Unit, onDismiss: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(LibreIcons.Link, contentDescription = null)
                Text(stringResource(R.string.home_clipboard_title), style = MaterialTheme.typography.titleMedium)
            }
            Text(url, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(
                modifier = Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_clipboard_dismiss)) }
                FilledTonalButton(onClick = onAnalyze) { Text(stringResource(R.string.home_clipboard_analyze)) }
            }
        }
    }
}

@Composable
internal fun CategoryRow(onCategoryClick: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(HomeCategory.entries) { category ->
            val query = stringResource(category.queryRes)
            AssistChip(
                onClick = { onCategoryClick(query) },
                label = { Text(stringResource(category.labelRes)) },
                leadingIcon = { Icon(category.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}

/** Cuadrícula de accesos directos. Mantén pulsado un sitio propio para quitarlo. */
@Composable
internal fun SitesGrid(
    sites: List<Site>,
    onSiteClick: (Site) -> Unit,
    onRemoveSite: (Site) -> Unit,
    onAddSite: () -> Unit,
) {
    var siteToRemove by rememberSaveable { mutableStateOf<Long?>(null) }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        sites.forEach { site ->
            SiteTile(
                name = site.name,
                avatar = { SiteAvatar(site.name) },
                onClick = { onSiteClick(site) },
                onLongClick = { siteToRemove = site.id },
            )
        }
        SiteTile(
            name = stringResource(R.string.home_site_add),
            avatar = {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(52.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(LibreIcons.Add, contentDescription = null) }
                }
            },
            onClick = onAddSite,
            onLongClick = null,
        )
    }

    sites.firstOrNull { it.id == siteToRemove }?.let { site ->
        AlertDialog(
            onDismissRequest = { siteToRemove = null },
            title = { Text(stringResource(R.string.home_site_remove_title)) },
            text = { Text(stringResource(R.string.home_site_remove_message, site.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveSite(site)
                    siteToRemove = null
                }) { Text(stringResource(R.string.home_site_remove_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { siteToRemove = null }) { Text(stringResource(R.string.home_cancel)) }
            },
        )
    }
}

@Composable
private fun SiteTile(
    name: String,
    avatar: @Composable () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .width(76.dp)
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = onLongClick?.let { stringResource(R.string.home_site_remove_title) },
            )
            .semantics { contentDescription = name }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        avatar()
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** Inicial del sitio sobre un color de la paleta (sin logos de marcas). */
@Composable
private fun SiteAvatar(name: String) {
    val scheme = MaterialTheme.colorScheme
    val palette: List<Pair<Color, Color>> = listOf(
        scheme.primaryContainer to scheme.onPrimaryContainer,
        scheme.secondaryContainer to scheme.onSecondaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer,
    )
    val (background, foreground) = palette[name.hashCode().absoluteValue % palette.size]
    Surface(shape = CircleShape, color = background, contentColor = foreground, modifier = Modifier.size(52.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

@Composable
internal fun AddSiteDialog(onConfirm: (name: String, url: String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_site_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.home_site_name)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.home_site_url)) },
                    placeholder = { Text("https://") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, url) }, enabled = url.isNotBlank()) {
                Text(stringResource(R.string.home_site_add_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_cancel)) }
        },
    )
}
