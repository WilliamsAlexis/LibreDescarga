package com.libredescarga.feature.player

import com.libredescarga.core.model.RepeatMode
import com.libredescarga.core.player.AudioEffectsController
import com.libredescarga.core.testing.FakeFavoritesRepository
import com.libredescarga.core.testing.FakePlaybackController
import com.libredescarga.core.testing.FakeSleepTimer
import com.libredescarga.core.testing.MainDispatcherRule
import com.libredescarga.core.testing.TestLibrary
import com.libredescarga.feature.player.music.MusicPlayerViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MusicPlayerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val playback = FakePlaybackController()
    private val sleepTimer = FakeSleepTimer()
    private val favorites = FakeFavoritesRepository()
    private lateinit var viewModel: MusicPlayerViewModel

    @Before
    fun setUp() {
        viewModel = MusicPlayerViewModel(playback, sleepTimer, AudioEffectsController(), favorites)
        playback.playQueue(TestLibrary.songs, 0)
    }

    private fun TestScope.collectState() =
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect {} }

    private val state get() = viewModel.uiState.value

    @Test
    fun `al crearse se conecta con el servicio`() {
        assertEquals(1, playback.connectCount)
    }

    @Test
    fun `refleja la canción actual y permite pausar y cambiar de pista`() = runTest {
        collectState()
        assertEquals("Canción libre", state.playback.current?.title)
        assertTrue(state.playback.isPlaying)

        viewModel.onTogglePlayPause()
        assertFalse(state.playback.isPlaying)

        viewModel.onNext()
        assertEquals("Nocturno", state.playback.current?.title)
        viewModel.onPrevious()
        assertEquals("Canción libre", state.playback.current?.title)
    }

    @Test
    fun `repetir recorre desactivado, toda la cola y una canción`() = runTest {
        collectState()
        assertEquals(RepeatMode.OFF, state.playback.repeatMode)
        viewModel.onCycleRepeat()
        assertEquals(RepeatMode.ALL, state.playback.repeatMode)
        viewModel.onCycleRepeat()
        assertEquals(RepeatMode.ONE, state.playback.repeatMode)
        viewModel.onCycleRepeat()
        assertEquals(RepeatMode.OFF, state.playback.repeatMode)
    }

    @Test
    fun `aleatorio se activa y se desactiva`() = runTest {
        collectState()
        viewModel.onToggleShuffle()
        assertTrue(state.playback.shuffle)
        viewModel.onToggleShuffle()
        assertFalse(state.playback.shuffle)
    }

    @Test
    fun `saltar a una canción y quitarla de la cola`() = runTest {
        collectState()
        viewModel.onSkipTo(1)
        assertEquals(1, state.playback.currentIndex)

        viewModel.onRemoveFromQueue(1)
        assertEquals(1, state.playback.queue.size)
    }

    @Test
    fun `favorito de la canción actual`() = runTest {
        collectState()
        assertFalse(state.isFavorite)
        viewModel.onToggleFavorite()
        assertTrue(state.isFavorite)
        viewModel.onToggleFavorite()
        assertFalse(state.isFavorite)
    }

    @Test
    fun `temporizador de apagado`() = runTest {
        collectState()
        assertNull(state.sleepRemainingMs)
        viewModel.onStartSleepTimer(15)
        assertEquals(15 * 60_000L, state.sleepRemainingMs)
        viewModel.onCancelSleepTimer()
        assertNull(state.sleepRemainingMs)
    }

    @Test
    fun `cerrar el reproductor vacía la cola`() = runTest {
        collectState()
        viewModel.onStop()
        assertTrue(state.playback.queue.isEmpty())
    }
}
