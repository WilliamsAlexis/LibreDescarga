package com.libredescarga.feature.library.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.core.model.LibraryMedia
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.ui.LocalMediaThumbnail
import com.libredescarga.core.ui.formatBytes
import com.libredescarga.feature.library.R

/** Fila de la lista: miniatura, título, artista o carpeta, tamaño y menú de acciones. */
@Composable
internal fun MediaRow(
    media: LibraryMedia,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LocalMediaThumbnail(
            uri = media.uri,
            kind = media.kind,
            durationMs = media.durationMs,
            modifier = if (media.kind == MediaKind.VIDEO) {
                Modifier.size(width = 112.dp, height = 63.dp)
            } else {
                Modifier.size(56.dp)
            },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = media.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    media.artist ?: media.folder.takeIf { it.isNotEmpty() },
                    formatBytes(media.sizeBytes).takeIf { media.sizeBytes > 0 },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isFavorite) {
            Icon(
                imageVector = LibreIcons.Heart,
                contentDescription = stringResource(R.string.library_favorite),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onMore) {
            Icon(LibreIcons.More, contentDescription = stringResource(R.string.library_actions_for, media.title))
        }
    }
}

/** Celda de la vista en cuadrícula. */
@Composable
internal fun MediaGridCell(
    media: LibraryMedia,
    onClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box {
            LocalMediaThumbnail(
                uri = media.uri,
                kind = media.kind,
                durationMs = media.durationMs,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (media.kind == MediaKind.VIDEO) 16f / 9f else 1f),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = media.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onMore) {
                Icon(LibreIcons.More, contentDescription = stringResource(R.string.library_actions_for, media.title))
            }
        }
    }
}
