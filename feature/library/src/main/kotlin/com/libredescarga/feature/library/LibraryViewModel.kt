package com.libredescarga.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.libredescarga.core.domain.player.PlaybackController
import com.libredescarga.core.domain.repository.FavoritesRepository
import com.libredescarga.core.domain.repository.LibraryRepository
import com.libredescarga.core.domain.repository.PlaylistRepository
import com.libredescarga.core.domain.usecase.ConvertToAudioUseCase
import com.libredescarga.core.domain.usecase.LibraryQuery
import com.libredescarga.core.domain.vault.MoveToVaultUseCase
import com.libredescarga.core.model.FileOperationResult
import com.libredescarga.core.model.LibraryMedia
import com.libredescarga.core.model.LibrarySort
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.model.MoveToVaultResult
import com.libredescarga.core.model.PlaylistItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val playlists: PlaylistRepository,
    private val favorites: FavoritesRepository,
    private val player: PlaybackController,
    private val convertToAudio: ConvertToAudioUseCase,
    private val moveToVault: MoveToVaultUseCase,
) : ViewModel() {

    /** Estado propio de la pantalla (pestaña, búsqueda, orden...). */
    private val local = MutableStateFlow(LibraryUiState())

    private val _events = Channel<LibraryEvent>(Channel.BUFFERED)
    val events: Flow<LibraryEvent> = _events.receiveAsFlow()

    private val media = combine(library.videos, library.audio) { videos, audio -> videos to audio }

    val uiState: StateFlow<LibraryUiState> = combine(
        local,
        media,
        playlists.playlists,
        favorites.favorites,
    ) { state, (videos, audio), userPlaylists, favoriteUris ->
        val sortedVideos = LibraryQuery.filterAndSort(videos, state.query, state.sort)
        val sortedMusic = LibraryQuery.filterAndSort(audio, state.query, state.sort)
        val folders = LibraryQuery.folders(sortedVideos + sortedMusic)
        val all = videos + audio
        val byUri = all.associateBy { it.uri }

        val (openTitle, openItems) = when (val open = state.open) {
            is OpenCollection.Folder -> folders.firstOrNull { it.path == open.path }
                .let { (it?.name ?: open.path) to it?.items.orEmpty() }
            OpenCollection.Favorites -> null to LibraryQuery.filterAndSort(
                all.filter { it.uri in favoriteUris },
                state.query,
                state.sort,
            )
            is OpenCollection.Playlist -> userPlaylists.firstOrNull { it.id == open.id }
                .let { playlist -> playlist?.name to playlist?.items.orEmpty().mapNotNull { byUri[it.uri] } }
            null -> null to emptyList()
        }

        state.copy(
            loaded = true,
            videos = sortedVideos,
            music = sortedMusic,
            folders = folders,
            playlists = userPlaylists,
            favorites = favoriteUris,
            openTitle = openTitle,
            openItems = openItems,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    // --- Navegación dentro de la biblioteca --------------------------------------------------

    fun onTabSelected(tab: LibraryTab) = local.update { it.copy(tab = tab, open = null) }

    fun onSearchActiveChange(active: Boolean) = local.update {
        it.copy(searchActive = active, query = if (active) it.query else "")
    }

    fun onQueryChange(query: String) = local.update { it.copy(query = query) }

    fun onSortSelected(sort: LibrarySort) = local.update { it.copy(sort = sort) }

    fun onToggleView() = local.update { it.copy(gridView = !it.gridView) }

    fun onOpen(collection: OpenCollection) = local.update { it.copy(open = collection) }

    fun onCloseCollection() = local.update { it.copy(open = null) }

    /** Tras conceder el permiso de archivos multimedia. */
    fun refresh() = library.refresh()

    // --- Reproducción -----------------------------------------------------------------------

    /** Un video abre el reproductor de video; la música se reproduce con la lista visible como cola. */
    fun onMediaClick(media: LibraryMedia, visible: List<LibraryMedia>) {
        if (media.kind == MediaKind.VIDEO) {
            viewModelScope.launch { _events.send(LibraryEvent.OpenVideo(media)) }
            return
        }
        val queue = visible.filter { it.kind == MediaKind.AUDIO }
        player.playQueue(queue, queue.indexOfFirst { it.uri == media.uri }.coerceAtLeast(0))
    }

    /** Videos descargados como audio, en segundo plano. */
    fun onPlayAsAudio(media: LibraryMedia) = player.playQueue(listOf(media), 0)

    fun onPlayAll(items: List<LibraryMedia>) {
        if (items.isNotEmpty()) player.playQueue(items, 0)
    }

    // --- Favoritos y listas -----------------------------------------------------------------

    fun onToggleFavorite(media: LibraryMedia) = launch { favorites.toggle(media.uri) }

    fun onAddToPlaylist(playlistId: Long, media: LibraryMedia) = launch {
        playlists.addItems(playlistId, listOf(media.toPlaylistItem()))
        val name = uiState.value.playlists.firstOrNull { it.id == playlistId }?.name
            ?: playlists.playlists.first().firstOrNull { it.id == playlistId }?.name.orEmpty()
        showMessage(LibraryMessage.AddedToPlaylist(name))
    }

    fun onCreatePlaylist(name: String, firstItem: LibraryMedia?) = launch {
        if (name.isBlank()) return@launch
        val id = playlists.create(name)
        if (firstItem != null) {
            playlists.addItems(id, listOf(firstItem.toPlaylistItem()))
            showMessage(LibraryMessage.AddedToPlaylist(name.trim()))
        }
    }

    fun onRenamePlaylist(id: Long, name: String) = launch { if (name.isNotBlank()) playlists.rename(id, name) }

    fun onDeletePlaylist(id: Long) = launch {
        playlists.delete(id)
        local.update { if (it.open == OpenCollection.Playlist(id)) it.copy(open = null) else it }
    }

    fun onRemoveFromPlaylist(id: Long, media: LibraryMedia) = launch { playlists.removeItem(id, media.uri) }

    // --- Operaciones de archivo -------------------------------------------------------------

    fun onRename(media: LibraryMedia, newName: String) {
        if (newName.isBlank()) return
        run(FileOperation.Rename(media, newName.trim()))
    }

    fun onMove(media: LibraryMedia, folder: String) = run(FileOperation.Move(media, folder))

    fun onDelete(media: LibraryMedia) = run(FileOperation.Delete(listOf(media)))

    /** Copia el archivo a la carpeta privada y, si salió bien, borra el original de la galería. */
    fun onMoveToVault(media: LibraryMedia) = launch {
        when (val result = moveToVault(media)) {
            MoveToVaultResult.Moved -> {
                library.refresh()
                showMessage(LibraryMessage.MovedToVault)
            }
            MoveToVaultResult.NotUnlocked -> showMessage(LibraryMessage.VaultLocked)
            MoveToVaultResult.ImportFailed -> showMessage(LibraryMessage.VaultImportFailed)
            MoveToVaultResult.CopiedOriginalKept -> showMessage(LibraryMessage.VaultOriginalKept)
            is MoveToVaultResult.NeedsConsentToDeleteOriginal -> local.update {
                it.copy(
                    pendingConsent = PendingConsent(result.request, FileOperation.Delete(listOf(media)), movedToVault = true),
                )
            }
        }
    }

    fun onConvertToMp3(media: LibraryMedia) = launch {
        if (convertToAudio(media)) showMessage(LibraryMessage.ConversionQueued)
    }

    /** Respuesta al diálogo del sistema. Al borrar, el propio sistema ya borró los archivos. */
    fun onConsentResult(granted: Boolean) {
        val pending = local.value.pendingConsent ?: return
        local.update { it.copy(pendingConsent = null) }
        if (!granted) {
            // La copia ya está en la carpeta privada: solo falta que el original se quede en la galería.
            if (pending.movedToVault) showMessage(LibraryMessage.VaultOriginalKept)
            return
        }
        when (val operation = pending.operation) {
            is FileOperation.Delete -> {
                library.refresh()
                showMessage(if (pending.movedToVault) LibraryMessage.MovedToVault else LibraryMessage.Deleted)
            }
            else -> run(operation, afterConsent = true)
        }
    }

    fun onMessageShown() = local.update { it.copy(message = null) }

    private fun run(operation: FileOperation, afterConsent: Boolean = false) = launch {
        val result = when (operation) {
            is FileOperation.Rename -> library.rename(operation.media.uri, operation.newName)
            is FileOperation.Move -> library.move(operation.media.uri, operation.folder)
            is FileOperation.Delete -> library.delete(operation.media.map { it.uri })
        }
        when (result) {
            FileOperationResult.Success -> showMessage(
                when (operation) {
                    is FileOperation.Rename -> LibraryMessage.Renamed
                    is FileOperation.Move -> LibraryMessage.Moved
                    is FileOperation.Delete -> LibraryMessage.Deleted
                },
            )
            FileOperationResult.Failed -> showMessage(LibraryMessage.OperationFailed)
            is FileOperationResult.NeedsUserConsent ->
                // Si ya se concedió y aun así falla, no se vuelve a preguntar.
                if (afterConsent) {
                    showMessage(LibraryMessage.OperationFailed)
                } else {
                    local.update { it.copy(pendingConsent = PendingConsent(result.request, operation)) }
                }
        }
    }

    private fun showMessage(message: LibraryMessage) = local.update { it.copy(message = message) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun LibraryMedia.toPlaylistItem() = PlaylistItem(
        uri = uri,
        title = title,
        artist = artist,
        durationMs = durationMs,
        kind = kind,
    )
}
