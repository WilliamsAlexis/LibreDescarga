package com.libredescarga.core.testing

import com.libredescarga.core.domain.player.PlaybackController
import com.libredescarga.core.domain.player.SleepTimer
import com.libredescarga.core.domain.player.VideoPlayback
import com.libredescarga.core.domain.repository.FavoritesRepository
import com.libredescarga.core.domain.repository.LibraryRepository
import com.libredescarga.core.domain.repository.PlaylistRepository
import com.libredescarga.core.model.FileOperationResult
import com.libredescarga.core.model.LibraryMedia
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.model.PlaybackState
import com.libredescarga.core.model.PlaylistItem
import com.libredescarga.core.model.QueueEntry
import com.libredescarga.core.model.RepeatMode
import com.libredescarga.core.model.UserPlaylist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FakeLibraryRepository(
    videos: List<LibraryMedia> = TestLibrary.videos,
    audio: List<LibraryMedia> = TestLibrary.songs,
) : LibraryRepository {
    private val videoState = MutableStateFlow(videos)
    private val audioState = MutableStateFlow(audio)

    /** Resultado que devolverán las operaciones de archivo (por defecto, éxito). */
    var nextResult: FileOperationResult = FileOperationResult.Success
    var refreshCount = 0
        private set
    val operations = mutableListOf<String>()

    override val videos: Flow<List<LibraryMedia>> = videoState
    override val audio: Flow<List<LibraryMedia>> = audioState

    override fun refresh() {
        refreshCount++
    }

    override suspend fun rename(uri: String, newName: String): FileOperationResult = apply("rename:$uri:$newName") {
        update(uri) { it.copy(title = newName, displayName = "$newName.${it.extension}") }
    }

    override suspend fun delete(uris: List<String>): FileOperationResult = apply("delete:${uris.joinToString()}") {
        videoState.update { list -> list.filterNot { it.uri in uris } }
        audioState.update { list -> list.filterNot { it.uri in uris } }
    }

    override suspend fun move(uri: String, folder: String): FileOperationResult = apply("move:$uri:$folder") {
        update(uri) { it.copy(folder = folder) }
    }

    private fun apply(operation: String, onSuccess: () -> Unit): FileOperationResult {
        operations += operation
        val result = nextResult
        if (result == FileOperationResult.Success) onSuccess()
        return result
    }

    private fun update(uri: String, transform: (LibraryMedia) -> LibraryMedia) {
        videoState.update { list -> list.map { if (it.uri == uri) transform(it) else it } }
        audioState.update { list -> list.map { if (it.uri == uri) transform(it) else it } }
    }
}

class FakePlaylistRepository : PlaylistRepository {
    private val state = MutableStateFlow<List<UserPlaylist>>(emptyList())
    override val playlists: Flow<List<UserPlaylist>> = state
    val current: List<UserPlaylist> get() = state.value

    override suspend fun create(name: String): Long {
        val id = (state.value.maxOfOrNull { it.id } ?: 0) + 1
        state.update { it + UserPlaylist(id, name, emptyList()) }
        return id
    }

    override suspend fun rename(id: Long, name: String) = edit(id) { it.copy(name = name) }

    override suspend fun delete(id: Long) = state.update { list -> list.filterNot { it.id == id } }

    override suspend fun addItems(id: Long, items: List<PlaylistItem>) = edit(id) { playlist ->
        val existing = playlist.items.map { it.uri }.toSet()
        playlist.copy(items = playlist.items + items.filterNot { it.uri in existing })
    }

    override suspend fun removeItem(id: Long, uri: String) = edit(id) { playlist ->
        playlist.copy(items = playlist.items.filterNot { it.uri == uri })
    }

    private fun edit(id: Long, transform: (UserPlaylist) -> UserPlaylist) =
        state.update { list -> list.map { if (it.id == id) transform(it) else it } }
}

class FakeFavoritesRepository : FavoritesRepository {
    private val state = MutableStateFlow<Set<String>>(emptySet())
    override val favorites: Flow<Set<String>> = state

    override suspend fun toggle(uri: String) = state.update { if (uri in it) it - uri else it + uri }
}

/** Reproductor en memoria: refleja las órdenes en [state] sin reproducir nada. */
class FakePlaybackController : PlaybackController {
    private val _state = MutableStateFlow(PlaybackState(isConnected = true))
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()
    var connectCount = 0
        private set

    override fun connect() {
        connectCount++
    }

    override fun playQueue(items: List<LibraryMedia>, startIndex: Int) = _state.update {
        it.copy(
            queue = items.map { media -> QueueEntry(media.uri, media.title, media.artist, media.kind == MediaKind.VIDEO) },
            currentIndex = startIndex,
            isPlaying = true,
            positionMs = 0,
            durationMs = items[startIndex].durationMs,
        )
    }

    override fun togglePlayPause() = _state.update { it.copy(isPlaying = !it.isPlaying) }

    override fun pause() = _state.update { it.copy(isPlaying = false) }

    override fun seekTo(positionMs: Long) = _state.update { it.copy(positionMs = positionMs) }

    override fun seekBy(deltaMs: Long) = _state.update { it.copy(positionMs = (it.positionMs + deltaMs).coerceAtLeast(0)) }

    override fun next() = _state.update { it.copy(currentIndex = (it.currentIndex + 1).coerceAtMost(it.queue.lastIndex)) }

    override fun previous() = _state.update { it.copy(currentIndex = (it.currentIndex - 1).coerceAtLeast(0)) }

    override fun skipTo(index: Int) = _state.update { it.copy(currentIndex = index, isPlaying = true) }

    override fun removeFromQueue(index: Int) = _state.update { state ->
        val queue = state.queue.toMutableList().apply { removeAt(index) }
        state.copy(queue = queue, currentIndex = state.currentIndex.coerceAtMost(queue.lastIndex))
    }

    override fun setShuffle(enabled: Boolean) = _state.update { it.copy(shuffle = enabled) }

    override fun setRepeatMode(mode: RepeatMode) = _state.update { it.copy(repeatMode = mode) }

    override fun setSpeed(speed: Float) = _state.update { it.copy(speed = speed) }

    override fun stop() = _state.update { PlaybackState(isConnected = true) }
}

class FakeVideoPlayback(private val controller: FakePlaybackController) : VideoPlayback {
    var attachedSubtitle: Pair<String, String>? = null
        private set

    override fun open(media: LibraryMedia) {
        if (controller.state.value.current?.uri != media.uri) controller.playQueue(listOf(media), 0)
    }

    override fun attachSubtitle(uri: String, mimeType: String) {
        attachedSubtitle = uri to mimeType
    }
}

class FakeSleepTimer : SleepTimer {
    private val _remaining = MutableStateFlow<Long?>(null)
    override val remainingMs: StateFlow<Long?> = _remaining

    override fun start(minutes: Int) {
        _remaining.value = minutes * 60_000L
    }

    override fun cancel() {
        _remaining.value = null
    }
}
