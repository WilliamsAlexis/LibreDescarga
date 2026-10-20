package com.libredescarga.feature.library

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.core.model.LibraryMedia
import com.libredescarga.core.model.LibrarySort
import com.libredescarga.core.model.MediaFolder
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.model.UserPlaylist
import com.libredescarga.core.ui.EmptyState
import com.libredescarga.feature.library.component.ConfirmDeleteDialog
import com.libredescarga.feature.library.component.DetailsDialog
import com.libredescarga.feature.library.component.MediaActionsSheet
import com.libredescarga.feature.library.component.MediaAction
import com.libredescarga.feature.library.component.MediaGridCell
import com.libredescarga.feature.library.component.MediaRow
import com.libredescarga.feature.library.component.MoveDialog
import com.libredescarga.feature.library.component.PlaylistPickerDialog
import com.libredescarga.feature.library.component.TextInputDialog
import kotlinx.coroutines.launch

/**
 * Pantalla "Biblioteca": muestra los videos y la música del teléfono, las carpetas, las listas y los favoritos.
 * Esta parte conecta la pantalla con su ViewModel y pide los permisos; el dibujo está en [LibraryContent].
 */
@Composable
internal fun LibraryScreen(
    onOpenVideo: (LibraryMedia) -> Unit,
    onOpenDuplicates: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOpenVideo by rememberUpdatedState(onOpenVideo)

    // Escucha eventos de una sola vez, como abrir el reproductor de video.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LibraryEvent.OpenVideo -> currentOpenVideo(event.media)
            }
        }
    }

    // Muestra los avisos breves de abajo (por ejemplo "Archivo renombrado").
    val message = uiState.message?.let { libraryMessage(it) }
    LaunchedEffect(uiState.message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.onMessageShown()
        }
    }

    // Diálogo del sistema para modificar o borrar archivos que no creó la app (Android 10+).
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        viewModel.onConsentResult(granted = it.resultCode == android.app.Activity.RESULT_OK)
    }
    LaunchedEffect(uiState.pendingConsent) {
        (uiState.pendingConsent?.request as? IntentSender)?.let {
            consentLauncher.launch(IntentSenderRequest.Builder(it).build())
        }
    }

    // Permiso para ver los videos y la música que ya existen en el teléfono.
    val permissions = remember { mediaPermissions() }
    var hasPermission by remember { mutableStateOf(context.hasAll(permissions)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPermission = context.hasAll(permissions)
        viewModel.refresh()
    }

    // Dibuja la pantalla y le pasa cada acción al ViewModel.
    LibraryContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        showPermissionCard = !hasPermission,
        onGrantPermission = { permissionLauncher.launch(permissions) },
        onTabSelected = viewModel::onTabSelected,
        onSearchActiveChange = viewModel::onSearchActiveChange,
        onQueryChange = viewModel::onQueryChange,
        onSortSelected = viewModel::onSortSelected,
        onToggleView = viewModel::onToggleView,
        onOpenDuplicates = onOpenDuplicates,
        onOpen = viewModel::onOpen,
        onCloseCollection = viewModel::onCloseCollection,
        onMediaClick = viewModel::onMediaClick,
        onPlayAll = viewModel::onPlayAll,
        onAction = { media, action, extra -> handleAction(context, viewModel, media, action, extra) },
        onCreatePlaylist = { name -> viewModel.onCreatePlaylist(name, null) },
        onRenamePlaylist = viewModel::onRenamePlaylist,
        onDeletePlaylist = viewModel::onDeletePlaylist,
        onCreatePlaylistWith = viewModel::onCreatePlaylist,
        onAddToPlaylist = viewModel::onAddToPlaylist,
        onRename = viewModel::onRename,
        onMove = viewModel::onMove,
        onDelete = viewModel::onDelete,
    )
}

/** Acciones de la hoja de un archivo que no necesitan un diálogo propio. */
private fun handleAction(
    context: Context,
    viewModel: LibraryViewModel,
    media: LibraryMedia,
    action: MediaAction,
    playlistId: Long?,
) {
    when (action) {
        MediaAction.PLAY_AS_AUDIO -> viewModel.onPlayAsAudio(media)
        MediaAction.FAVORITE -> viewModel.onToggleFavorite(media)
        MediaAction.REMOVE_FROM_PLAYLIST -> playlistId?.let { viewModel.onRemoveFromPlaylist(it, media) }
        MediaAction.SHARE -> context.shareMedia(media)
        MediaAction.CONVERT -> viewModel.onConvertToMp3(media)
        MediaAction.MOVE_TO_VAULT -> viewModel.onMoveToVault(media)
        else -> Unit
    }
}

/** Dibujo de la biblioteca: barra superior, pestañas, listado y los diálogos de cada acción. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryContent(
    uiState: LibraryUiState,
    snackbarHostState: SnackbarHostState,
    showPermissionCard: Boolean,
    onGrantPermission: () -> Unit,
    onTabSelected: (LibraryTab) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onSortSelected: (LibrarySort) -> Unit,
    onToggleView: () -> Unit,
    onOpenDuplicates: () -> Unit = {},
    onOpen: (OpenCollection) -> Unit,
    onCloseCollection: () -> Unit,
    onMediaClick: (LibraryMedia, List<LibraryMedia>) -> Unit,
    onPlayAll: (List<LibraryMedia>) -> Unit,
    onAction: (LibraryMedia, MediaAction, playlistId: Long?) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onRenamePlaylist: (Long, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onCreatePlaylistWith: (String, LibraryMedia?) -> Unit,
    onAddToPlaylist: (Long, LibraryMedia) -> Unit,
    modifier: Modifier = Modifier,
    onRename: (LibraryMedia, String) -> Unit = { _, _ -> },
    onMove: (LibraryMedia, String) -> Unit = { _, _ -> },
    onDelete: (LibraryMedia) -> Unit = {},
) {
    // Diálogos de un archivo (se resuelven aquí; el resto de acciones, en el ViewModel).
    var actionsFor by remember { mutableStateOf<LibraryMedia?>(null) }
    var renameFor by remember { mutableStateOf<LibraryMedia?>(null) }
    var moveFor by remember { mutableStateOf<LibraryMedia?>(null) }
    var deleteFor by remember { mutableStateOf<LibraryMedia?>(null) }
    var detailsFor by remember { mutableStateOf<LibraryMedia?>(null) }
    var playlistFor by remember { mutableStateOf<LibraryMedia?>(null) }
    var showCreatePlaylist by rememberSaveable { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<UserPlaylist?>(null) }
    var playlistToDelete by remember { mutableStateOf<UserPlaylist?>(null) }
    var sortMenu by remember { mutableStateOf(false) }

    // Si hay una carpeta, lista o favoritos abierto, el botón Atrás lo cierra en vez de salir.
    val inCollection = uiState.open != null
    BackHandler(enabled = inCollection || uiState.searchActive) {
        if (uiState.searchActive) onSearchActiveChange(false) else onCloseCollection()
    }

    // Estructura de la pantalla: barra de arriba, avisos y contenido.
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (uiState.searchActive) {
                SearchBar(uiState.query, onQueryChange) { onSearchActiveChange(false) }
            } else {
                TopAppBar(
                    title = { Text(uiState.openTitleOrDefault()) },
                    navigationIcon = {
                        if (inCollection) {
                            IconButton(onClick = onCloseCollection) {
                                Icon(LibreIcons.Back, contentDescription = stringResource(R.string.library_back))
                            }
                        }
                    },
                    actions = {
                        if (!inCollection) {
                            IconButton(onClick = onOpenDuplicates) {
                                Icon(LibreIcons.Copy, contentDescription = stringResource(R.string.duplicates_open))
                            }
                        }
                        IconButton(onClick = { onSearchActiveChange(true) }) {
                            Icon(LibreIcons.Search, contentDescription = stringResource(R.string.library_search))
                        }
                        IconButton(onClick = onToggleView) {
                            Icon(
                                imageVector = if (uiState.gridView) LibreIcons.ListView else LibreIcons.GridView,
                                contentDescription = stringResource(
                                    if (uiState.gridView) R.string.library_view_list else R.string.library_view_grid,
                                ),
                            )
                        }
                        Box {
                            IconButton(onClick = { sortMenu = true }) {
                                Icon(LibreIcons.Sort, contentDescription = stringResource(R.string.library_sort))
                            }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                LibrarySort.entries.forEach { sort ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(sort.labelRes)) },
                                        trailingIcon = {
                                            if (sort == uiState.sort) Icon(LibreIcons.Granted, contentDescription = null)
                                        },
                                        onClick = {
                                            onSortSelected(sort)
                                            sortMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (!inCollection) {
                // Pestañas: Videos, Música, Carpetas y Listas (se ocultan dentro de una carpeta o lista).
                PrimaryScrollableTabRow(selectedTabIndex = uiState.tab.ordinal, edgePadding = 8.dp) {
                    LibraryTab.entries.forEach { tab ->
                        Tab(
                            selected = tab == uiState.tab,
                            onClick = { onTabSelected(tab) },
                            text = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }

            // Si falta el permiso para ver los archivos, se muestra una tarjeta para pedirlo.
            if (showPermissionCard) PermissionCard(onGrantPermission)

            // Qué archivos se ven según la pestaña o la colección abierta; null en Carpetas y Listas.
            val visible: List<LibraryMedia>? = when {
                inCollection -> uiState.openItems
                uiState.tab == LibraryTab.VIDEOS -> uiState.videos
                uiState.tab == LibraryTab.MUSIC -> uiState.music
                else -> null
            }

            when {
                visible != null -> MediaList(
                    items = visible,
                    gridView = uiState.gridView,
                    favorites = uiState.favorites,
                    showPlayAll = visible.any { it.kind == MediaKind.AUDIO },
                    emptyIsSearch = uiState.query.isNotBlank(),
                    onClick = { onMediaClick(it, visible) },
                    onMore = { actionsFor = it },
                    onPlayAll = { onPlayAll(visible.filter { it.kind == MediaKind.AUDIO }) },
                )

                uiState.tab == LibraryTab.FOLDERS -> FolderList(uiState.folders, onOpen = { onOpen(OpenCollection.Folder(it.path)) })

                else -> PlaylistsTab(
                    playlists = uiState.playlists,
                    favoritesCount = uiState.favorites.size,
                    onOpenFavorites = { onOpen(OpenCollection.Favorites) },
                    onOpenPlaylist = { onOpen(OpenCollection.Playlist(it.id)) },
                    onCreate = { showCreatePlaylist = true },
                    onRename = { playlistToRename = it },
                    onDelete = { playlistToDelete = it },
                )
            }
        }
    }

    // Hoja con las acciones del archivo (renombrar, mover, borrar, detalles, favorito...).
    actionsFor?.let { media ->
        val playlistId = (uiState.open as? OpenCollection.Playlist)?.id
        MediaActionsSheet(
            media = media,
            isFavorite = media.uri in uiState.favorites,
            inPlaylist = playlistId != null,
            onDismiss = { actionsFor = null },
            onAction = { action ->
                actionsFor = null
                when (action) {
                    MediaAction.RENAME -> renameFor = media
                    MediaAction.MOVE -> moveFor = media
                    MediaAction.DELETE -> deleteFor = media
                    MediaAction.DETAILS -> detailsFor = media
                    MediaAction.ADD_TO_PLAYLIST -> playlistFor = media
                    else -> onAction(media, action, playlistId)
                }
            },
        )
    }

    // Diálogo para renombrar (sin la extensión).
    renameFor?.let { media ->
        TextInputDialog(
            title = stringResource(R.string.library_action_rename),
            label = stringResource(R.string.library_name),
            initial = media.displayName.substringBeforeLast('.'),
            confirmLabel = stringResource(R.string.library_save),
            onConfirm = {
                onRename(media, it)
                renameFor = null
            },
            onDismiss = { renameFor = null },
        )
    }
    // Diálogo para mover el archivo a otra carpeta.
    moveFor?.let { media ->
        MoveDialog(media, onConfirm = { onMove(media, it); moveFor = null }, onDismiss = { moveFor = null })
    }
    // Confirmación antes de borrar.
    deleteFor?.let { media ->
        ConfirmDeleteDialog(media.title, onConfirm = { onDelete(media); deleteFor = null }, onDismiss = { deleteFor = null })
    }
    // Diálogo con los detalles del archivo.
    detailsFor?.let { DetailsDialog(it, onDismiss = { detailsFor = null }) }
    // Selector de lista para agregar el archivo.
    playlistFor?.let { media ->
        PlaylistPickerDialog(
            playlists = uiState.playlists,
            onPick = { onAddToPlaylist(it, media); playlistFor = null },
            onCreate = { onCreatePlaylistWith(it, media); playlistFor = null },
            onDismiss = { playlistFor = null },
        )
    }
    // Diálogo para crear una lista nueva.
    if (showCreatePlaylist) {
        TextInputDialog(
            title = stringResource(R.string.library_playlist_new),
            label = stringResource(R.string.library_name),
            initial = "",
            confirmLabel = stringResource(R.string.library_playlist_create),
            onConfirm = { onCreatePlaylist(it); showCreatePlaylist = false },
            onDismiss = { showCreatePlaylist = false },
        )
    }
    // Diálogo para cambiar el nombre de una lista.
    playlistToRename?.let { playlist ->
        TextInputDialog(
            title = stringResource(R.string.library_playlist_rename),
            label = stringResource(R.string.library_name),
            initial = playlist.name,
            confirmLabel = stringResource(R.string.library_save),
            onConfirm = { onRenamePlaylist(playlist.id, it); playlistToRename = null },
            onDismiss = { playlistToRename = null },
        )
    }
    // Confirmación antes de borrar una lista.
    playlistToDelete?.let { playlist ->
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text(stringResource(R.string.library_playlist_delete)) },
            text = { Text(stringResource(R.string.library_playlist_delete_message, playlist.name)) },
            confirmButton = {
                TextButton(onClick = { onDeletePlaylist(playlist.id); playlistToDelete = null }) {
                    Text(stringResource(R.string.library_delete_confirm))
                }
            },
            dismissButton = { TextButton(onClick = { playlistToDelete = null }) { Text(stringResource(R.string.library_cancel)) } },
        )
    }
}

/** Título de la barra superior: el de la carpeta/lista abierta, o "Biblioteca". */
@Composable
private fun LibraryUiState.openTitleOrDefault(): String = when {
    open == null -> stringResource(R.string.feature_library_title)
    open == OpenCollection.Favorites -> stringResource(R.string.library_favorites)
    else -> openTitle ?: stringResource(R.string.feature_library_title)
}

/** Barra de búsqueda que reemplaza a la barra superior mientras se escribe. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TopAppBar(
        title = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.library_search_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(LibreIcons.Back, contentDescription = stringResource(R.string.library_search_close))
            }
        },
    )
}

/** Tarjeta que explica por qué hace falta el permiso y tiene el botón para concederlo. */
@Composable
private fun PermissionCard(onGrant: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.library_permission_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.library_permission_message), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onGrant, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.library_permission_grant))
            }
        }
    }
}

/** Lista o cuadrícula de archivos; si no hay ninguno muestra un mensaje de "vacío". */
@Composable
private fun MediaList(
    items: List<LibraryMedia>,
    gridView: Boolean,
    favorites: Set<String>,
    showPlayAll: Boolean,
    emptyIsSearch: Boolean,
    onClick: (LibraryMedia) -> Unit,
    onMore: (LibraryMedia) -> Unit,
    onPlayAll: () -> Unit,
) {
    if (items.isEmpty()) {
        EmptyState(
            icon = LibreIcons.Library,
            title = stringResource(if (emptyIsSearch) R.string.library_empty_search_title else R.string.library_empty_title),
            message = stringResource(if (emptyIsSearch) R.string.library_empty_search_message else R.string.library_empty_message),
        )
        return
    }
    if (gridView) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 156.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            gridItems(items, key = { it.uri }) { media ->
                MediaGridCell(media, onClick = { onClick(media) }, onMore = { onMore(media) })
            }
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            if (showPlayAll) {
                item(key = "play-all") {
                    TextButton(onClick = onPlayAll) {
                        Icon(LibreIcons.Play, contentDescription = null)
                        Text(stringResource(R.string.library_play_all), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            items(items, key = { it.uri }) { media ->
                MediaRow(media, isFavorite = media.uri in favorites, onClick = { onClick(media) }, onMore = { onMore(media) })
            }
        }
    }
}

/** Lista de carpetas con la cantidad de archivos de cada una. */
@Composable
private fun FolderList(folders: List<MediaFolder>, onOpen: (MediaFolder) -> Unit) {
    if (folders.isEmpty()) {
        EmptyState(
            icon = LibreIcons.Folder,
            title = stringResource(R.string.library_empty_title),
            message = stringResource(R.string.library_empty_message),
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        items(folders, key = { it.path }) { folder ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(folder) }
                    .heightIn(min = 64.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(LibreIcons.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(folder.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = folder.path,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(folder.items.size.toString(), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Pestaña de listas: Favoritos, las listas del usuario y el botón para crear una nueva. */
@Composable
private fun PlaylistsTab(
    playlists: List<UserPlaylist>,
    favoritesCount: Int,
    onOpenFavorites: () -> Unit,
    onOpenPlaylist: (UserPlaylist) -> Unit,
    onCreate: () -> Unit,
    onRename: (UserPlaylist) -> Unit,
    onDelete: (UserPlaylist) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        item(key = "favorites") {
            CollectionRow(
                icon = LibreIcons.Heart,
                title = stringResource(R.string.library_favorites),
                count = favoritesCount,
                onClick = onOpenFavorites,
            )
        }
        items(playlists, key = { it.id }) { playlist ->
            var menu by remember { mutableStateOf(false) }
            CollectionRow(
                icon = LibreIcons.Playlist,
                title = playlist.name,
                count = playlist.items.size,
                onClick = { onOpenPlaylist(playlist) },
                trailing = {
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(LibreIcons.More, contentDescription = stringResource(R.string.library_actions_for, playlist.name))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_action_rename)) },
                                onClick = { menu = false; onRename(playlist) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_action_delete)) },
                                onClick = { menu = false; onDelete(playlist) },
                            )
                        }
                    }
                },
            )
        }
        item(key = "create") {
            TextButton(onClick = onCreate, modifier = Modifier.padding(top = 8.dp)) {
                Icon(LibreIcons.Add, contentDescription = null)
                Text(stringResource(R.string.library_playlist_new), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** Fila de una colección (favoritos o lista) con su icono, nombre y cantidad de elementos. */
@Composable
private fun CollectionRow(
    icon: ImageVector,
    title: String,
    count: Int,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.library_items_count, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}

/** Texto del aviso que corresponde a cada resultado. */
@Composable
private fun libraryMessage(message: LibraryMessage): String = when (message) {
    LibraryMessage.Renamed -> stringResource(R.string.library_message_renamed)
    LibraryMessage.Moved -> stringResource(R.string.library_message_moved)
    LibraryMessage.Deleted -> stringResource(R.string.library_message_deleted)
    LibraryMessage.OperationFailed -> stringResource(R.string.library_message_failed)
    LibraryMessage.ConversionQueued -> stringResource(R.string.library_message_conversion)
    is LibraryMessage.AddedToPlaylist -> stringResource(R.string.library_message_added, message.playlistName)
    LibraryMessage.MovedToVault -> stringResource(R.string.library_message_vault_moved)
    LibraryMessage.VaultLocked -> stringResource(R.string.library_message_vault_locked)
    LibraryMessage.VaultImportFailed -> stringResource(R.string.library_message_vault_failed)
    LibraryMessage.VaultOriginalKept -> stringResource(R.string.library_message_vault_original_kept)
}

// Nombre de cada pestaña.
private val LibraryTab.labelRes: Int
    get() = when (this) {
        LibraryTab.VIDEOS -> R.string.library_tab_videos
        LibraryTab.MUSIC -> R.string.library_tab_music
        LibraryTab.FOLDERS -> R.string.library_tab_folders
        LibraryTab.PLAYLISTS -> R.string.library_tab_playlists
    }

// Nombre de cada criterio de orden.
private val LibrarySort.labelRes: Int
    get() = when (this) {
        LibrarySort.DATE -> R.string.library_sort_date
        LibrarySort.NAME -> R.string.library_sort_name
        LibrarySort.SIZE -> R.string.library_sort_size
        LibrarySort.DURATION -> R.string.library_sort_duration
    }

/** Permisos que necesita Android para ver videos y audio (cambian según la versión del sistema). */
private fun mediaPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

/** Indica si ya se concedieron todos los permisos de la lista. */
private fun Context.hasAll(permissions: Array<String>): Boolean =
    permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

/** Abre el menú de Android para compartir el archivo con otra app. */
private fun Context.shareMedia(media: LibraryMedia) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType(media.mimeType ?: "*/*")
        .putExtra(Intent.EXTRA_STREAM, Uri.parse(media.uri))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        startActivity(Intent.createChooser(intent, media.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // Sin apps para compartir: no hay nada más que hacer.
    }
}
