package com.libredescarga.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.core.model.AnalisisPerf

/** Miniatura 16:9 de internet con la duración en una esquina. Muestra un ícono si no hay imagen. */
@Composable
fun MediaThumbnail(
    url: String?,
    modifier: Modifier = Modifier,
    durationSeconds: Long? = null,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            // Diagnóstico: cuánto tarda en llegar la miniatura desde internet.
            val loadStart = remember(url) { System.nanoTime() }
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                error = { PlaceholderIcon(LibreIcons.Library) },
                onSuccess = {
                    AnalisisPerf.duration("miniatura: descarga y decodificación (Coil)", (System.nanoTime() - loadStart) / 1_000_000, AnalisisPerf.hostOf(url))
                },
                onError = {
                    AnalisisPerf.duration("miniatura: error al cargar", (System.nanoTime() - loadStart) / 1_000_000, AnalisisPerf.hostOf(url))
                },
            )
        } else {
            PlaceholderIcon(LibreIcons.Library)
        }
        DurationBadge(durationSeconds)
    }
}

@Composable
internal fun PlaceholderIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun BoxScope.DurationBadge(durationSeconds: Long?) {
    if (durationSeconds == null || durationSeconds <= 0) return
    Text(
        text = formatDuration(durationSeconds),
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}
