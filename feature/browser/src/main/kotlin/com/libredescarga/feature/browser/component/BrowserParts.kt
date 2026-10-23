package com.libredescarga.feature.browser.component

import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.core.model.Bookmark
import com.libredescarga.feature.browser.BrowserTab
import com.libredescarga.feature.browser.PageState
import com.libredescarga.feature.browser.R

/** Color de fondo de la barra de direcciones (más oscuro en incógnito). */
@Composable
internal fun addressBarContainerColor(incognito: Boolean): Color =
    if (incognito) MaterialTheme.colorScheme.inverseSurface else MaterialTheme.colorScheme.surfaceContainer

/** Barra superior: cerrar, dirección, marcador y pestañas, con la barra de progreso debajo. */
@Composable
internal fun AddressBar(
    tab: BrowserTab?,
    page: PageState,
    isBookmarked: Boolean,
    tabCount: Int,
    onClose: () -> Unit,
    onSubmit: (String) -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenTabs: () -> Unit,
    onMenu: @Composable () -> Unit,
) {
    // Controla el teclado y el foco al escribir.
    val focusManager = LocalFocusManager.current
    val shownUrl = tab?.url.orEmpty()
    var text by remember(tab?.id, shownUrl) { mutableStateOf(shownUrl.removePrefix("https://")) }
    var focused by remember { mutableStateOf(false) }
    // Verdadero si la pestaña es de navegación privada.
    val incognito = tab?.incognito == true
    // La barra empieza justo debajo de la barra de estado (y de la cámara perforada o muesca) sin valores fijos.
    val topInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

    Surface(
        color = addressBarContainerColor(incognito),
        contentColor = if (incognito) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface,
    ) {
        // El color de la barra llega hasta arriba del todo; el contenido se baja lo que ocupa la barra de estado.
        Column(modifier = Modifier.windowInsetsPadding(topInsets)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(LibreIcons.Close, contentDescription = stringResource(R.string.browser_close))
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).onFocusChanged { focused = it.isFocused },
                    placeholder = {
                        Text(stringResource(if (incognito) R.string.browser_address_hint_incognito else R.string.browser_address_hint))
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (shownUrl.startsWith("https://")) LibreIcons.Lock else LibreIcons.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    trailingIcon = {
                        if (focused && text.isNotEmpty()) {
                            IconButton(onClick = { text = "" }) {
                                Icon(LibreIcons.Clear, contentDescription = stringResource(R.string.browser_clear))
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        focusManager.clearFocus()
                        onSubmit(text)
                    }),
                )
                IconButton(onClick = onToggleBookmark, enabled = shownUrl.isNotEmpty()) {
                    Icon(
                        imageVector = if (isBookmarked) LibreIcons.Bookmark else LibreIcons.BookmarkOutlined,
                        contentDescription = stringResource(
                            if (isBookmarked) R.string.browser_bookmark_remove else R.string.browser_bookmark_add,
                        ),
                        tint = if (isBookmarked) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
                TabsButton(count = tabCount, onClick = onOpenTabs)
                onMenu()
            }
            if (page.loading && page.progress < 100) {
                LinearProgressIndicator(progress = { page.progress / 100f }, modifier = Modifier.fillMaxWidth())
            } else {
                Box(Modifier.heightIn(min = 4.dp))
            }
        }
    }
}

/** Botón que muestra cuántas pestañas hay abiertas. */
@Composable
private fun TabsButton(count: Int, onClick: () -> Unit) {
    val description = stringResource(R.string.browser_tabs_count, count)
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = description }) {
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = Color.Transparent,
            border = BorderStroke(2.dp, LocalContentColor.current),
            modifier = Modifier.size(24.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(count.toString(), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Barra inferior: atrás, adelante, recargar o detener, e inicio. */
@Composable
internal fun BottomControls(
    page: PageState,
    canNavigate: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReloadOrStop: () -> Unit,
    onShare: () -> Unit,
    onHome: () -> Unit,
) {
    // Los botones quedan por encima de la barra de navegación (por gestos o por botones).
    val bottomInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier.windowInsetsPadding(bottomInsets).fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, enabled = page.canGoBack) {
                Icon(LibreIcons.Back, contentDescription = stringResource(R.string.browser_back))
            }
            IconButton(onClick = onForward, enabled = page.canGoForward) {
                Icon(LibreIcons.Forward, contentDescription = stringResource(R.string.browser_forward))
            }
            IconButton(onClick = onReloadOrStop, enabled = canNavigate) {
                Icon(
                    imageVector = if (page.loading) LibreIcons.Close else LibreIcons.Retry,
                    contentDescription = stringResource(if (page.loading) R.string.browser_stop else R.string.browser_reload),
                )
            }
            IconButton(onClick = onShare, enabled = canNavigate) {
                Icon(LibreIcons.Share, contentDescription = stringResource(R.string.browser_share))
            }
            IconButton(onClick = onHome) {
                Icon(LibreIcons.Home, contentDescription = stringResource(R.string.browser_home))
            }
        }
    }
}

/** Botón flotante de descarga con un pulso suave para llamar la atención. */
@Composable
internal fun DownloadFab(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Animación suave que hace latir el botón para llamar la atención.
    val pulse by rememberInfiniteTransition(label = "fabPulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "fabScale",
    )
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier.scale(pulse),
        icon = { Icon(LibreIcons.Downloads, contentDescription = null) },
        text = {
            Text(
                if (count > 1) stringResource(R.string.browser_download_count, count) else stringResource(R.string.browser_download),
            )
        },
    )
}

/** Aviso: el video tiene protección (DRM) y no se puede descargar. */
@Composable
internal fun DrmBanner() {
    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(LibreIcons.Lock, contentDescription = null)
            Text(stringResource(R.string.browser_drm), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Pantalla de error cuando una página no carga, con botón para reintentar. */
@Composable
internal fun PageError(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(LibreIcons.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
        Text(stringResource(R.string.browser_error), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.browser_error_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) { Text(stringResource(R.string.browser_retry)) }
    }
}

/** Pantalla de una pestaña nueva: marcadores y una explicación de la privacidad. */
@Composable
internal fun StartPage(
    incognito: Boolean,
    bookmarks: List<Bookmark>,
    incognitoIsolated: Boolean,
    onOpen: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().widthIn(max = 720.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                Text(
                    stringResource(if (incognito) R.string.browser_start_incognito_title else R.string.browser_start_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    stringResource(
                        when {
                            !incognito -> R.string.browser_start_message
                            incognitoIsolated -> R.string.browser_start_incognito_message
                            else -> R.string.browser_start_incognito_limited
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!incognito && bookmarks.isNotEmpty()) {
            item { Text(stringResource(R.string.browser_bookmarks), style = MaterialTheme.typography.titleMedium) }
            items(bookmarks.take(12), key = { it.url }) { bookmark ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(bookmark.url) }.heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(bookmark.title.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•")
                        }
                    }
                    Column {
                        Text(bookmark.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            bookmark.url.removePrefix("https://"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
