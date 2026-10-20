package com.libredescarga.feature.browser.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.libredescarga.core.ui.MediaThumbnail
import com.libredescarga.feature.browser.R

/**
 * Hoja con las imágenes que tiene la página, para elegir cuáles guardar.
 * Las miniaturas se cargan de internet; si un sitio las bloquea se ve un ícono, pero igual se pueden descargar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImagesSheet(
    images: List<String>,
    onDownload: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    // Se guardan en una sola cadena para que la selección sobreviva a girar la pantalla.
    var selectedText by rememberSaveable { mutableStateOf("") }
    val selected = selectedText.split('\n').filter { it.isNotEmpty() }.toSet()
    fun setSelected(urls: Set<String>) {
        selectedText = urls.joinToString("\n")
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Text(
                text = stringResource(R.string.browser_images_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Text(
                text = stringResource(R.string.browser_images_hint, images.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                val allSelected = selected.size == images.size
                TextButton(onClick = { setSelected(if (allSelected) emptySet() else images.toSet()) }) {
                    Text(stringResource(if (allSelected) R.string.browser_images_select_none else R.string.browser_images_select_all))
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                items(images, key = { it }) { url ->
                    val checked = url in selected
                    val description = stringResource(R.string.browser_images_item, images.indexOf(url) + 1)
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .semantics { contentDescription = description }
                            .clickable(role = Role.Checkbox) { setSelected(if (checked) selected - url else selected + url) },
                    ) {
                        MediaThumbnail(url = url, modifier = Modifier.fillMaxSize())
                        Checkbox(
                            checked = checked,
                            onCheckedChange = null,
                            modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                        )
                    }
                }
            }
            Button(
                onClick = { onDownload(images.filter { it in selected }) },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Text(stringResource(R.string.browser_images_download, selected.size))
            }
        }
    }
}
