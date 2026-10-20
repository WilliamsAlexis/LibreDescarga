package com.libredescarga.feature.library

import app.cash.turbine.test
import com.libredescarga.core.domain.usecase.ConvertToAudioUseCase
import com.libredescarga.core.domain.vault.MoveToVaultUseCase
import com.libredescarga.core.model.FileOperationResult
import com.libredescarga.core.model.LibrarySort
import com.libredescarga.core.testing.FakeDownloadRepository
import com.libredescarga.core.testing.FakeFavoritesRepository
import com.libredescarga.core.testing.FakeLibraryRepository
import com.libredescarga.core.testing.FakePlaybackController
import com.libredescarga.core.testing.FakePlaylistRepository
import com.libredescarga.core.testing.FakeVaultRepository
import com.libredescarga.core.testing.FakeVaultSession
import com.libredescarga.core.testing.MainDispatcherRule
import com.libredescarga.core.testing.TestLibrary
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LibraryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val library = FakeLibraryRepository()
    private val playlists = FakePlaylistRepository()
    private val favorites = FakeFavoritesRepository()
    private val player = FakePlaybackController()
    private val downloads = FakeDownloadRepository()
    private val vault = FakeVaultRepository()
    private val session = FakeVaultSession(initiallyUnlocked = true)
    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setUp() {
        viewModel = LibraryViewModel(
            library, playlists, favorites, player, ConvertToAudioUseCase(downloads),
            MoveToVaultUseCase(vault, library, session),
        )
    }

    private fun TestScope.collectState() =
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect {} }

    private val state get() = viewModel.uiState.value

    @Test
    fun `muestra videos, música y carpetas`() = runTest {
        collectState()
        assertEquals(3, state.videos.size)
        assertEquals(2, state.music.size)
        assertEquals(3, state.folders.size)
    }

    @Test
    fun `busca y ordena`() = runTest {
        collectState()
        viewModel.onSearchActiveChange(true)
        viewModel.onQueryChange("bos")
        assertEquals(listOf("Bosque"), state.videos.map { it.title })

        viewModel.onSearchActiveChange(false)
        viewModel.onSortSelected(LibrarySort.NAME)
        assertEquals(listOf("Árbol", "Bosque", "Zeta"), state.videos.map { it.title })
    }

    @Test
    fun `tocar una canción reproduce la lista visible desde ella`() = runTest {
        collectState()
        val song = state.music[1]
        viewModel.onMediaClick(song, state.music)

        val playback = player.state.value
        assertEquals(state.music.map { it.uri }, playback.queue.map { it.uri })
        assertEquals(1, playback.currentIndex)
    }

    @Test
    fun `tocar un video abre el reproductor de video`() = runTest {
        collectState()
        viewModel.events.test {
            viewModel.onMediaClick(TestLibrary.videos.first(), state.videos)
            assertEquals(LibraryEvent.OpenVideo(TestLibrary.videos.first()), awaitItem())
        }
    }

    @Test
    fun `un video puede sonar como audio en segundo plano`() = runTest {
        collectState()
        viewModel.onPlayAsAudio(TestLibrary.videos.first())
        assertTrue(player.state.value.current!!.isVideo)
        assertTrue(player.state.value.isPlaying)
    }

    @Test
    fun `favoritos y listas propias`() = runTest {
        collectState()
        val song = TestLibrary.songs.first()
        viewModel.onToggleFavorite(song)
        viewModel.onOpen(OpenCollection.Favorites)
        assertEquals(listOf(song.uri), state.openItems.map { it.uri })

        viewModel.onCreatePlaylist("Viaje", song)
        val playlist = state.playlists.single()
        assertEquals(LibraryMessage.AddedToPlaylist("Viaje"), state.message)

        viewModel.onOpen(OpenCollection.Playlist(playlist.id))
        assertEquals("Viaje", state.openTitle)
        assertEquals(listOf(song.uri), state.openItems.map { it.uri })

        viewModel.onRemoveFromPlaylist(playlist.id, song)
        assertTrue(state.openItems.isEmpty())

        viewModel.onDeletePlaylist(playlist.id)
        assertNull(state.open)
    }

    @Test
    fun `renombrar, mover y borrar`() = runTest {
        collectState()
        val video = TestLibrary.videos.first()

        viewModel.onRename(video, "Nuevo nombre")
        assertEquals(LibraryMessage.Renamed, state.message)
        assertTrue(state.videos.any { it.title == "Nuevo nombre" })

        viewModel.onMove(video, "DCIM")
        assertEquals(LibraryMessage.Moved, state.message)

        viewModel.onDelete(video)
        assertEquals(LibraryMessage.Deleted, state.message)
        assertTrue(state.videos.none { it.uri == video.uri })
    }

    @Test
    fun `si el sistema pide confirmación se repite tras aceptarla`() = runTest {
        collectState()
        val video = TestLibrary.videos.first()
        library.nextResult = FileOperationResult.NeedsUserConsent("intent-sender")

        viewModel.onRename(video, "Otro")
        val pending = state.pendingConsent!!
        assertEquals("intent-sender", pending.request)

        library.nextResult = FileOperationResult.Success
        viewModel.onConsentResult(granted = true)
        assertNull(state.pendingConsent)
        assertEquals(LibraryMessage.Renamed, state.message)
        assertEquals(2, library.operations.size)
    }

    @Test
    fun `al borrar con confirmación no se repite la operación`() = runTest {
        collectState()
        library.nextResult = FileOperationResult.NeedsUserConsent("intent-sender")
        viewModel.onDelete(TestLibrary.videos.last())

        viewModel.onConsentResult(granted = true)
        assertEquals(LibraryMessage.Deleted, state.message)
        assertEquals(1, library.operations.size)
        assertEquals(1, library.refreshCount)
    }

    @Test
    fun `convertir un video a MP3 lo envía a la cola de descargas`() = runTest {
        collectState()
        viewModel.onConvertToMp3(TestLibrary.videos.first())
        assertEquals(LibraryMessage.ConversionQueued, state.message)
        assertEquals(1, downloads.enqueued.size)
    }

    @Test
    fun `mover a la carpeta privada copia y quita el original de la biblioteca`() = runTest {
        collectState()
        val video = TestLibrary.videos.first()
        viewModel.onMoveToVault(video)

        assertEquals(LibraryMessage.MovedToVault, state.message)
        assertEquals(1, vault.current.size)
        assertTrue(state.videos.none { it.uri == video.uri })
    }

    @Test
    fun `con la carpeta privada bloqueada se avisa y no se toca nada`() = runTest {
        collectState()
        session.lock()
        viewModel.onMoveToVault(TestLibrary.videos.first())

        assertEquals(LibraryMessage.VaultLocked, state.message)
        assertTrue(vault.current.isEmpty())
        assertTrue(library.operations.isEmpty())
    }

    @Test
    fun `si Android pide confirmar el borrado del original se recuerda que viene de la carpeta privada`() = runTest {
        collectState()
        library.nextResult = FileOperationResult.NeedsUserConsent("intent-sender")
        viewModel.onMoveToVault(TestLibrary.videos.first())
        assertEquals("intent-sender", state.pendingConsent!!.request)
        assertTrue(state.pendingConsent!!.movedToVault)

        viewModel.onConsentResult(granted = true)
        assertEquals(LibraryMessage.MovedToVault, state.message)
        assertNull(state.pendingConsent)
    }

    @Test
    fun `si se rechaza el borrado el original sigue y la copia queda en la carpeta privada`() = runTest {
        collectState()
        library.nextResult = FileOperationResult.NeedsUserConsent("intent-sender")
        viewModel.onMoveToVault(TestLibrary.videos.first())

        viewModel.onConsentResult(granted = false)
        assertEquals(LibraryMessage.VaultOriginalKept, state.message)
        assertEquals(1, vault.current.size)
    }
}
