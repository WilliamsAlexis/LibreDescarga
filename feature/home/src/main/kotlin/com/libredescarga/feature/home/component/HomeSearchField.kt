package com.libredescarga.feature.home.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.core.model.SearchProvider
import com.libredescarga.feature.home.R
import com.libredescarga.feature.home.readClipboardText

/** Barra universal: acepta un enlace (se analiza) o palabras clave (se buscan). */
@Composable
internal fun HomeSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onPaste: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { onFocusChange(it.isFocused) },
        placeholder = { Text(stringResource(R.string.home_search_placeholder)) },
        leadingIcon = { Icon(LibreIcons.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(LibreIcons.Clear, contentDescription = stringResource(R.string.home_search_clear))
                }
            } else {
                IconButton(onClick = { context.readClipboardText()?.let(onPaste) }) {
                    Icon(LibreIcons.Paste, contentDescription = stringResource(R.string.home_search_paste))
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                focusManager.clearFocus()
                onSubmit()
            },
        ),
    )
}

@Composable
internal fun SuggestionsCard(
    suggestions: List<String>,
    onClick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            suggestions.forEach { suggestion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            focusManager.clearFocus()
                            onClick(suggestion)
                        }
                        .heightIn(min = 48.dp)
                        .padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        imageVector = LibreIcons.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onRemove(suggestion) }) {
                        Icon(
                            imageVector = LibreIcons.Close,
                            contentDescription = stringResource(R.string.home_history_remove, suggestion),
                        )
                    }
                }
            }
            TextButton(
                onClick = onClearHistory,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = 8.dp),
            ) {
                Text(stringResource(R.string.home_history_clear))
            }
        }
    }
}

/** Sitio donde se buscan las palabras clave. */
@Composable
internal fun ProviderChips(selected: SearchProvider, onSelected: (SearchProvider) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.home_search_in),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SearchProvider.entries) { provider ->
                FilterChip(
                    selected = provider == selected,
                    onClick = { onSelected(provider) },
                    label = { Text(provider.displayName) },
                )
            }
        }
    }
}
