package com.libredescarga.core.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.core.model.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val thumbnailCache = LruCache<String, ImageBitmap>(120)

/**
 * Miniatura de un archivo del teléfono: fotograma del video o carátula del audio.
 * Se genera en el propio teléfono (sin internet) y se guarda en memoria.
 */
@Composable
fun LocalMediaThumbnail(
    uri: String,
    kind: MediaKind,
    modifier: Modifier = Modifier,
    durationMs: Long? = null,
) {
    val context = LocalContext.current
    val bitmap by produceState(initialValue = thumbnailCache.get(uri), uri) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { loadThumbnail(context, uri, kind) }
                ?.also { thumbnailCache.put(uri, it) }
        }
    }
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            PlaceholderIcon(if (kind == MediaKind.VIDEO) LibreIcons.Library else LibreIcons.Music)
        }
        DurationBadge(durationMs?.div(1000))
    }
}

private fun loadThumbnail(context: Context, uriString: String, kind: MediaKind): ImageBitmap? = runCatching {
    val uri = Uri.parse(uriString)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.contentResolver.loadThumbnail(uri, Size(THUMBNAIL_SIZE, THUMBNAIL_SIZE), null).asImageBitmap()
    } else {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val bitmap = when (kind) {
                MediaKind.VIDEO -> retriever.getFrameAtTime(FRAME_TIME_US)
                MediaKind.AUDIO -> retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                MediaKind.IMAGE -> null // Las imágenes se muestran directamente, no con miniatura de video.
            }
            bitmap?.asImageBitmap()
        } finally {
            retriever.release()
        }
    }
}.getOrNull()

private const val THUMBNAIL_SIZE = 320
private const val FRAME_TIME_US = 1_000_000L
