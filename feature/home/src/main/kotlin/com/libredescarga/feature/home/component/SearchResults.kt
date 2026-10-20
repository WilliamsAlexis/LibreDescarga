package com.libredescarga.feature.home.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.core.model.MediaEntry
import com.libredescarga.core.model.SearchProvider
import com.libredescarga.core.ui.MediaThumbnail
import com.libredescarga.core.ui.userMessage
import com.libredescarga.feature.home.R
import com.libredescarga.feature.home.SearchState

@Composable
internal fun SearchResultsHeader(search: SearchState, provider: SearchProvider, onClose: () -> Unit) {
    val query = when (search) {
        is SearchState.Loading -> search.query
        is SearchState.Results -> search.query
        is SearchState.Failed -> search.query
        SearchState.Idle -> return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.home_results_for, query, provider.displayName),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onClose) {
            Icon(LibreIcons.Close, contentDescription = stringResource(R.string.home_results_close))
        }
    }
}

/** Carga, error o lista vacía. */
@Composable
internal fun SearchResultsState(search: SearchState, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (search) {
            is SearchState.Loading -> {
                CircularProgressIndicator()
                Text(stringResource(R.string.home_searching), style = MaterialTheme.typography.bodyLarge)
            }

            is SearchState.Failed -> {
                Text(
                    text = search.error.userMessage(),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onRetry) { Text(stringResource(R.string.home_retry)) }
            }

            is SearchState.Results -> Text(
                text = stringResource(R.string.home_no_results),
                style = MaterialTheme.typography.bodyLarge,
            )

            SearchState.Idle -> Unit
        }
    }
}

@Composable
internal fun SearchResultRow(
    entry: MediaEntry,
    showQuickDownload: Boolean,
    onClick: () -> Unit,
    onQuickDownload: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MediaThumbnail(
            url = entry.thumbnailUrl,
            durationSeconds = entry.durationSeconds,
            modifier = Modifier.size(width = 128.dp, height = 72.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            entry.uploader?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showQuickDownload) {
            IconButton(onClick = onQuickDownload) {
                Icon(
                    imageVector = LibreIcons.Bolt,
                    contentDescription = stringResource(R.string.home_quick_download, entry.title),
                )
            }
        }
    }
}
