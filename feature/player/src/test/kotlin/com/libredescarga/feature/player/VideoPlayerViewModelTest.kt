package com.libredescarga.feature.player

import androidx.media3.common.Player
import com.libredescarga.core.player.PlayerSurfaceProvider
import com.libredescarga.core.testing.FakePlaybackController
import com.libredescarga.core.testing.FakeVideoPlayback
import com.libredescarga.core.testing.MainDispatcherRule
import com.libredescarga.feature.player.video.GestureFeedback
import com.libredescarga.feature.player.video.OrientationMode
import com.libredescarga.feature.player.video.VerticalGestureTarget
import com.libredescarga.feature.player.video.VideoArgs
import com.libredescarga.feature.player.video.VideoPlayerViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

class VideoPlayerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val playback = FakePlaybackController()
    private val video = FakeVideoPlayback(playback)
    private val surface = object : PlayerSurfaceProvider {
        override val player: StateFlow<Player?> = MutableStateFlow(null)
    }
    private lateinit var viewModel: VideoPlayerViewModel

    @Before
    fun setUp() {
        viewModel = VideoPlayerViewModel(
            args = VideoArgs("content://media/external/video/media/1", "Mi video", 60_000),
            playback = playback,
            video = video,
            surface = surface,
        )
    }

    private fun TestScope.collectState() =
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect {} }

    private val state get() = viewModel.uiState.value

    @Test
    fun `al abrirse conecta y reproduce el video`() = runTest {
        collectState()
        assertEquals(1, playback.connectCount)
        assertEquals("Mi video", state.playback.current?.title)
        assertTrue(state.playback.current!!.isVideo)
        assertTrue(state.playback.isPlaying)
    }

    @Test
    fun `pausar, buscar y cambiar la velocidad`() = runTest {
        collectState()
        viewModel.onTogglePlayPause()
        assertFalse(state.playback.isPlaying)

        viewModel.onSeekTo(30_000)
        assertEquals(30_000L, state.playback.positionMs)

        viewModel.onSeekBy(10_000)
        assertEquals(40_000L, state.playback.positionMs)
        assertEquals(GestureFeedback.Seek(forward = true), state.feedback)

        viewModel.onSpeedSelected(1.5f)
        assertEquals(1.5f, state.playback.speed, 0.001f)
    }

    @Test
    fun `un salto de cero no hace nada`() = runTest {
        collectState()
        viewModel.onSeekBy(0)
        assertNull(state.feedback)
    }

    @Test
    fun `el bloqueo oculta los controles y los toques no los muestran`() = runTest {
        collectState()
        viewModel.onToggleLock()
        assertTrue(state.locked)
        assertFalse(state.controlsVisible)

        viewModel.onToggleControls()
        assertFalse(state.controlsVisible)

        viewModel.onToggleLock()
        assertFalse(state.locked)
        assertTrue(state.controlsVisible)
    }

    @Test
    fun `la orientación recorre automática, horizontal y vertical`() = runTest {
        collectState()
        assertEquals(OrientationMode.AUTO, state.orientation)
        viewModel.onCycleOrientation()
        assertEquals(OrientationMode.LANDSCAPE, state.orientation)
        viewModel.onCycleOrientation()
        assertEquals(OrientationMode.PORTRAIT, state.orientation)
        viewModel.onCycleOrientation()
        assertEquals(OrientationMode.AUTO, state.orientation)
    }

    @Test
    fun `el pellizco alterna llenar y ajustar mostrando un aviso`() = runTest {
        collectState()
        viewModel.onPinchZoom(1.5f)
        assertTrue(state.zoomFilled)
        assertEquals(GestureFeedback.Zoom(filled = true), state.feedback)

        viewModel.onFeedbackShown()
        viewModel.onPinchZoom(1.6f) // Sin cambio: no vuelve a avisar.
        assertNull(state.feedback)

        viewModel.onPinchZoom(0.7f)
        assertFalse(state.zoomFilled)
    }

    @Test
    fun `volumen y brillo muestran el porcentaje`() = runTest {
        collectState()
        viewModel.onLevelChanged(VerticalGestureTarget.VOLUME, 0.42f)
        assertEquals(GestureFeedback.Level(VerticalGestureTarget.VOLUME, 42), state.feedback)
    }

    @Test
    fun `subtítulos externos SRT y VTT`() = runTest {
        collectState()
        viewModel.onSubtitleFile("content://docs/1", "pelicula.vtt")
        assertEquals("content://docs/1" to "text/vtt", video.attachedSubtitle)
        viewModel.onSubtitleFile("content://docs/2", "pelicula.srt")
        assertEquals("content://docs/2" to "application/x-subrip", video.attachedSubtitle)
    }

    @Test
    fun `al salir pausa salvo audio en segundo plano o ventana flotante`() = runTest {
        collectState()
        viewModel.onLeave(inPictureInPicture = true)
        assertTrue(state.playback.isPlaying)

        viewModel.onKeepPlayingInBackgroundChange(true)
        viewModel.onLeave(inPictureInPicture = false)
        assertTrue(state.playback.isPlaying)

        viewModel.onKeepPlayingInBackgroundChange(false)
        viewModel.onLeave(inPictureInPicture = false)
        assertFalse(state.playback.isPlaying)
    }
}
