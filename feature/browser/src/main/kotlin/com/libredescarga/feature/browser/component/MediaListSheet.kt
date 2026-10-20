package com.libredescarga.feature.browser.component

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.core.domain.browser.DetectedMedia
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.ui.MediaThumbnail
import com.libredescarga.core.ui.formatBytes
import com.libredescarga.feature.browser.R

/**
 * Lista de todo lo que se puede descargar de la página actual: el video o audio de la propia página
 * (si el motor la reconoce) y los videos, audios e imágenes que se detectaron.
 * Cada fila tiene miniatura, tipo, tamaño (si el servidor lo informa) y su propio botón Descargar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaListSheet(
    pageUrl: String?,
    media: List<DetectedMedia>,
    sizes: Map<String, Long>,
    queued: Set<String>,
    onDownloadPage: () -> Unit,
    onDownload: (DetectedMedia) -> Unit,
    onDismiss: () -> Unit,
) {
    // Primero videos, luego audios y al final las imágenes.
    val sorted = media.sortedBy {
        when (it.kind) {
            MediaKind.VIDEO -> 0
            MediaKind.AUDIO -> 1
            MediaKind.IMAGE -> 2
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Text(
                text = stringResource(R.string.browser_media_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Text(
                text = stringResource(R.string.browser_media_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            if (pageUrl == null && sorted.isEmpty()) {
                Text(
                    text = stringResource(R.string.browser_media_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(24.dp),
                )
            }
            LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)) {
                if (pageUrl != null) {
                    item(key = "page") {
                        MediaRow(
                            thumbnail = { KindIcon(MediaKind.VIDEO) },
                            title = stringResource(R.string.browser_media_page_title),
                            subtitle = stringResource(R.string.browser_media_page_subtitle),
                            isQueued = false,
                            onDownload = onDownloadPage,
                        )
                    }
                }
                items(sorted, key = { it.url }) { item ->
                    val size = sizes[item.url]
                    val kind = stringResource(
                        when (item.kind) {
                            MediaKind.VIDEO -> R.string.browser_media_kind_video
                            MediaKind.AUDIO -> R.string.browser_media_kind_audio
                            MediaKind.IMAGE -> R.string.browser_media_kind_image
                        },
                    )
                    MediaRow(
                        thumbnail = {
                            if (item.kind == MediaKind.IMAGE) {
                                MediaThumbnail(url = item.url, modifier = Modifier.size(56.dp))
                            } else {
                                KindIcon(item.kind)
                            }
                        },
                        title = fileNameOf(item.url),
                        subtitle = if (size != null) "$kind · ${formatBytes(size)}" else kind,
                        isQueued = item.url in queued,
                        onDownload = { onDownload(item) },
                    )
                }
            }
        }
    }
}

/** Una fila: miniatura, nombre, tipo y tamaño, y el botón para descargar (o "En cola" si ya se agregó). */
@Composable
private fun MediaRow(
    thumbnail: @Composable () -> Unit,
    title: String,
    subtitle: String,
    isQueued: Boolean,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 24.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        thumbnail()
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FilledTonalButton(onClick = onDownload, enabled = !isQueued) {
            if (isQueued) Icon(LibreIcons.Granted, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = stringResource(if (isQueued) R.string.browser_media_queued else R.string.browser_media_download),
                modifier = Modifier.padding(start = if (isQueued) 6.dp else 0.dp),
            )
        }
    }
}

/** Icono para los videos y audios, que no tienen miniatura. */
@Composable
private fun KindIcon(kind: MediaKind) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.size(56.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (kind == MediaKind.AUDIO) LibreIcons.Music else LibreIcons.Library,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** Nombre del archivo que aparece al final de la dirección; si no hay, el nombre del sitio. */
private fun fileNameOf(url: String): String {
    val name = Uri.decode(url.substringBefore('#').substringBefore('?').substringAfterLast('/')).trim()
    return name.ifBlank { Uri.parse(url).host.orEmpty() }
}
