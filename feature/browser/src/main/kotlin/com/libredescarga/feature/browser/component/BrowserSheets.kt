package com.libredescarga.feature.browser.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.core.model.Bookmark
import com.libredescarga.core.model.HistoryEntry
import com.libredescarga.feature.browser.BrowserTab
import com.libredescarga.feature.browser.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TabsSheet(
    tabs: List<BrowserTab>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onNewTab: () -> Unit,
    onNewIncognitoTab: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(onClick = onNewTab, modifier = Modifier.weight(1f)) {
                    Icon(LibreIcons.Add, contentDescription = null)
                    Text(stringResource(R.string.browser_new_tab), modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = onNewIncognitoTab, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.browser_new_incognito_tab))
                }
            }
            LazyColumn {
                items(tabs, key = { it.id }) { tab ->
                    val selected = tab.id == selectedId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(tab.id); onDismiss() }
                            .heightIn(min = 60.dp)
                            .padding(start = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (selected) Icon(LibreIcons.Granted, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tab.title.ifBlank { stringResource(R.string.browser_new_tab) },
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (tab.incognito) {
                                    stringResource(R.string.browser_incognito_label)
                                } else {
                                    tab.url.removePrefix("https://").ifEmpty { stringResource(R.string.browser_blank_page) }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { onClose(tab.id) }) {
                            Icon(LibreIcons.Close, contentDescription = stringResource(R.string.browser_close_tab))
                        }
                    }
                }
            }
        }
    }
}

/** Marcadores e historial. Tocar un elemento lo abre en la pestaña actual. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibrarySheet(
    bookmarks: List<Bookmark>,
    history: List<HistoryEntry>,
    onOpen: (String) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.browser_bookmarks)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.browser_history)) })
            }
            if (tab == 1 && history.isNotEmpty()) {
                TextButton(onClick = onClearHistory, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.browser_history_clear))
                }
            }
            val empty = if (tab == 0) bookmarks.isEmpty() else history.isEmpty()
            if (empty) {
                Text(
                    text = stringResource(if (tab == 0) R.string.browser_bookmarks_empty else R.string.browser_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            } else {
                LazyColumn {
                    if (tab == 0) {
                        items(bookmarks, key = { it.url }) { item ->
                            LinkRow(item.title, item.url, { onOpen(item.url) }, { onRemoveBookmark(item.url) })
                        }
                    } else {
                        items(history, key = { it.url }) { item ->
                            LinkRow(item.title, item.url, { onOpen(item.url) }, { onRemoveHistory(item.url) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkRow(title: String, url: String, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 60.dp).padding(start = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title.ifBlank { url }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                url.removePrefix("https://"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(LibreIcons.Delete, contentDescription = stringResource(R.string.browser_remove), modifier = Modifier.size(20.dp))
        }
    }
}
