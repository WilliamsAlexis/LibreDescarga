package com.libredescarga.feature.player.image

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.feature.player.R

/**
 * Visor de imágenes de la propia app: se desliza para pasar a la anterior o siguiente,
 * se hace zoom con dos dedos y un doble toque acerca o aleja.
 */
@Composable
internal fun ImageViewerScreen(
    uris: List<String>,
    titles: List<String>,
    startIndex: Int,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, (uris.size - 1).coerceAtLeast(0))) { uris.size }
    // Con zoom, deslizar mueve la imagen; el pager solo cambia de página cuando está sin zoom.
    var zoomed by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !zoomed,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            ZoomableImage(
                uri = uris[page],
                description = titles.getOrElse(page) { "" },
                onZoomChange = { if (page == pagerState.currentPage) zoomed = it },
            )
        }

        // Barra superior: volver, título, posición y compartir.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .systemBarsPadding()
                .background(Color.Black.copy(alpha = 0.45f))
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(LibreIcons.Back, contentDescription = stringResource(R.string.player_back), tint = Color.White)
            }
            Text(
                text = titles.getOrElse(pagerState.currentPage) { "" },
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (uris.size > 1) {
                Text(
                    text = stringResource(R.string.player_image_counter, pagerState.currentPage + 1, uris.size),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            IconButton(onClick = { context.shareImage(uris.getOrNull(pagerState.currentPage)) }) {
                Icon(LibreIcons.Share, contentDescription = stringResource(R.string.player_image_share), tint = Color.White)
            }
        }
    }
}

/** Una imagen con zoom (hasta 5×), arrastre cuando está ampliada y doble toque para acercar o alejar. */
@Composable
private fun ZoomableImage(uri: String, description: String, onZoomChange: (Boolean) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    // Evita arrastrar la imagen más allá de sus bordes.
    fun clamp(value: Offset, zoom: Float): Offset {
        val maxX = size.width * (zoom - 1f) / 2f
        val maxY = size.height * (zoom - 1f) / 2f
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, MAX_ZOOM)
        offset = if (scale > 1f) clamp(offset + panChange, scale) else Offset.Zero
        onZoomChange(scale > 1f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = DOUBLE_TAP_ZOOM
                        }
                        onZoomChange(scale > 1f)
                    },
                )
            }
            .transformable(state = transformState, canPan = { scale > 1f }),
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = uri,
            contentDescription = description,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            error = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.player_image_error), color = Color.White)
                }
            },
        )
    }
}

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

/** Abre el menú de Android para compartir la imagen mostrada. */
private fun Context.shareImage(uri: String?) {
    if (uri == null) return
    val intent = Intent(Intent.ACTION_SEND)
        .setType("image/*")
        .putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // Sin apps para compartir: no hay nada más que hacer.
    }
}
