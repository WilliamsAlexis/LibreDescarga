@file:OptIn(UnstableApi::class)

package com.libredescarga.feature.player.video

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.ComponentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.libredescarga.core.designsystem.icon.LibreIcons
import com.libredescarga.core.model.PlaybackSpeeds
import com.libredescarga.core.model.VaultItem
import com.libredescarga.core.ui.formatDuration
import com.libredescarga.feature.player.R
import com.libredescarga.feature.player.SecureWindowEffect
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

// Los controles desaparecen solos después de 3,5 segundos mientras se reproduce.
private const val CONTROLS_HIDE_DELAY_MS = 3_500L
// Tiempo que se muestra el aviso grande de gestos (adelantar, volumen, zoom).
private const val FEEDBACK_DURATION_MS = 800L

/**
 * Pantalla del reproductor de video: imagen, gestos con los dedos, controles y ventana flotante (PiP).
 * Si el video viene de la bóveda privada, se bloquean las capturas de pantalla.
 */
@Composable
internal fun VideoPlayerScreen(
    args: VideoArgs,
    onBack: () -> Unit,
    viewModel: VideoPlayerViewModel = hiltViewModel<VideoPlayerViewModel, VideoPlayerViewModel.Factory>(
        key = args.uri,
    ) { it.create(args) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val view = LocalView.current
    // Indica si el video está en la ventana flotante pequeña.
    val inPip = rememberPictureInPictureState(activity)
    val currentOnBack by rememberUpdatedState(onBack)

    // Un video de la carpeta privada no se puede capturar ni grabar.
    SecureWindowEffect(enabled = VaultItem.isVaultUri(args.uri))

    // Pantalla completa inmersiva, pantalla siempre encendida y orientación elegida.
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    LaunchedEffect(uiState.orientation) {
        activity?.requestedOrientation = when (uiState.orientation) {
            OrientationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            OrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            OrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    // Al salir pausa, salvo que se pidiera seguir como audio o esté en ventana flotante.
    DisposableEffect(Unit) { onDispose { viewModel.onLeave(inPictureInPicture = activity?.isInPictureInPictureMode == true) } }

    // Ventana flotante automática al pulsar Inicio (Android 12+).
    LaunchedEffect(uiState.playback.isPlaying, uiState.playback.current) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && activity != null && context.supportsPip()) {
            activity.setPictureInPictureParams(
                PictureInPictureParams.Builder()
                    .setAutoEnterEnabled(uiState.playback.isPlaying)
                    .build(),
            )
        }
    }
    DisposableEffect(activity) {
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && activity != null && context.supportsPip()) {
                activity.setPictureInPictureParams(PictureInPictureParams.Builder().setAutoEnterEnabled(false).build())
            }
        }
    }

    // Selector de archivos para cargar un subtítulo propio.
    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onSubtitleFile(uri.toString(), uri.lastPathSegment.orEmpty())
    }

    BackHandler(enabled = uiState.locked) { /* Bloqueado: el botón Atrás no hace nada. */ }

    // Fondo negro con el video y, encima, los gestos y los controles.
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // El video se dibuja solo cuando el reproductor ya está listo.
        player?.let { exo ->
            VideoSurface(player = exo, filled = uiState.zoomFilled, modifier = Modifier.fillMaxSize())
        }

        // En la ventana flotante no se muestran controles ni gestos.
        if (!inPip) {
            GestureLayer(
                locked = uiState.locked,
                onTap = viewModel::onToggleControls,
                onDoubleTap = { x, width -> viewModel.onSeekBy(GesturePolicy.doubleTapSeekMs(x, width)) },
                onLevel = viewModel::onLevelChanged,
                onPinch = viewModel::onPinchZoom,
                activity = activity,
            )

            FeedbackOverlay(uiState.feedback, viewModel::onFeedbackShown)

            if (uiState.playback.isBuffering) {
                androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            AnimatedVisibility(
                visible = uiState.controlsVisible && !uiState.locked,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                Controls(
                    title = args.title,
                    uiState = uiState,
                    player = player,
                    viewModel = viewModel,
                    onBack = { currentOnBack() },
                    onPip = { activity?.enterPip(uiState.playback.current != null) },
                    onPickSubtitleFile = { subtitlePicker.launch(arrayOf("*/*")) },
                    canPip = context.supportsPip(),
                )
            }

            if (uiState.locked) {
                IconButton(
                    onClick = viewModel::onToggleLock,
                    modifier = Modifier.align(Alignment.CenterStart).systemBarsPadding().padding(16.dp).size(56.dp),
                ) {
                    Icon(LibreIcons.Lock, contentDescription = stringResource(R.string.player_unlock), tint = Color.White)
                }
            }
        }
    }

    // Los controles se ocultan solos mientras se reproduce.
    LaunchedEffect(uiState.controlsVisible, uiState.playback.isPlaying) {
        if (uiState.controlsVisible && uiState.playback.isPlaying) {
            delay(CONTROLS_HIDE_DELAY_MS)
            viewModel.onControlsVisibleChange(false)
        }
    }
}

/** Superficie donde se dibuja el video; "filled" lo agranda para llenar la pantalla. */
@Composable
private fun VideoSurface(player: Player, filled: Boolean, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                this.player = player
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
            view.resizeMode = if (filled) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
        },
        onRelease = { it.player = null },
    )
}

/** Toque, doble toque, deslizar para volumen y brillo, y pellizcar para zoom. */
@Composable
private fun GestureLayer(
    locked: Boolean,
    onTap: () -> Unit,
    onDoubleTap: (x: Float, width: Float) -> Unit,
    onLevel: (VerticalGestureTarget, Float) -> Unit,
    onPinch: (Float) -> Unit,
    activity: Activity?,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentOnLevel by rememberUpdatedState(onLevel)
    val currentOnPinch by rememberUpdatedState(onPinch)
    val audioManager = remember(activity) { activity?.getSystemService(AudioManager::class.java) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(locked) {
                detectTapGestures(
                    onTap = { currentOnTap() },
                    onDoubleTap = { if (!locked) currentOnDoubleTap(it.x, size.width.toFloat()) },
                )
            }
            .pointerInput(locked) {
                if (locked) return@pointerInput
                var target = VerticalGestureTarget.VOLUME
                var level = 0f
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        target = GesturePolicy.verticalTarget(offset.x, size.width.toFloat())
                        level = when (target) {
                            VerticalGestureTarget.BRIGHTNESS -> activity?.currentBrightness() ?: 0.5f
                            VerticalGestureTarget.VOLUME -> audioManager?.volumeLevel() ?: 0.5f
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        level = GesturePolicy.levelAfterDrag(level, dragAmount, size.height.toFloat())
                        when (target) {
                            VerticalGestureTarget.BRIGHTNESS -> activity?.setBrightness(level)
                            VerticalGestureTarget.VOLUME -> audioManager?.setVolumeLevel(level)
                        }
                        currentOnLevel(target, level)
                    },
                )
            }
            .pointerInput(locked) {
                if (locked) return@pointerInput
                var scale = 1f
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size >= 2) {
                            val zoom = event.calculateZoom()
                            if (zoom != 1f) {
                                scale *= zoom
                                currentOnPinch(scale)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    scale = 1f
                }
            },
    )
}

/** Aviso grande y breve en el centro: adelantar/atrasar, volumen, brillo o zoom. */
@Composable
private fun BoxScope.FeedbackOverlay(feedback: GestureFeedback?, onShown: () -> Unit) {
    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(FEEDBACK_DURATION_MS)
            onShown()
        }
    }
    AnimatedVisibility(visible = feedback != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
        val (icon, text) = when (feedback) {
            is GestureFeedback.Seek -> (if (feedback.forward) LibreIcons.Forward10 else LibreIcons.Replay10) to
                stringResource(if (feedback.forward) R.string.player_seek_forward else R.string.player_seek_back)
            is GestureFeedback.Level -> (if (feedback.target == VerticalGestureTarget.VOLUME) LibreIcons.VolumeUp else LibreIcons.Brightness) to
                "${feedback.percent} %"
            is GestureFeedback.Zoom -> LibreIcons.Fullscreen to
                stringResource(if (feedback.filled) R.string.player_zoom_fill else R.string.player_zoom_fit)
            null -> LibreIcons.Play to ""
        }
        Column(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
            Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Controles del video: barra de arriba, botones del centro y barra de progreso con velocidad abajo. */
@Composable
private fun Controls(
    title: String,
    uiState: VideoPlayerUiState,
    player: Player?,
    viewModel: VideoPlayerViewModel,
    onBack: () -> Unit,
    onPip: () -> Unit,
    onPickSubtitleFile: () -> Unit,
    canPip: Boolean,
) {
    val playback = uiState.playback
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }
    var speedMenu by remember { mutableStateOf(false) }
    var subtitleMenu by remember { mutableStateOf(false) }
    var audioMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    // Pistas de audio y subtítulos que tiene el video.
    val tracks = rememberTracks(player)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).systemBarsPadding()) {
        // Barra superior
        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlButton(LibreIcons.Back, stringResource(R.string.player_back), onBack)
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Box {
                ControlButton(LibreIcons.Subtitles, stringResource(R.string.player_subtitles)) { subtitleMenu = true }
                DropdownMenu(expanded = subtitleMenu, onDismissRequest = { subtitleMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.player_subtitles_off)) },
                        onClick = { player?.disableText(); subtitleMenu = false },
                    )
                    tracks.text.forEach { track ->
                        DropdownMenuItem(
                            text = { Text(track.label) },
                            trailingIcon = { if (track.selected) Icon(LibreIcons.Granted, contentDescription = null) },
                            onClick = { player?.select(track); subtitleMenu = false },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.player_subtitles_load)) },
                        leadingIcon = { Icon(LibreIcons.Add, contentDescription = null) },
                        onClick = { subtitleMenu = false; onPickSubtitleFile() },
                    )
                }
            }
            // El botón de pistas de audio solo aparece si hay más de una.
            if (tracks.audio.size > 1) {
                Box {
                    ControlButton(LibreIcons.AudioTrack, stringResource(R.string.player_audio_track)) { audioMenu = true }
                    DropdownMenu(expanded = audioMenu, onDismissRequest = { audioMenu = false }) {
                        tracks.audio.forEach { track ->
                            DropdownMenuItem(
                                text = { Text(track.label) },
                                trailingIcon = { if (track.selected) Icon(LibreIcons.Granted, contentDescription = null) },
                                onClick = { player?.select(track); audioMenu = false },
                            )
                        }
                    }
                }
            }
            Box {
                ControlButton(LibreIcons.More, stringResource(R.string.player_more)) { moreMenu = true }
                DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.player_keep_background)) },
                        trailingIcon = {
                            if (uiState.keepPlayingInBackground) Icon(LibreIcons.Granted, contentDescription = null)
                        },
                        onClick = {
                            viewModel.onKeepPlayingInBackgroundChange(!uiState.keepPlayingInBackground)
                            moreMenu = false
                        },
                    )
                }
            }
        }

        // Controles centrales
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlButton(LibreIcons.Replay10, stringResource(R.string.player_seek_back)) { viewModel.onSeekBy(-GesturePolicy.SEEK_STEP_MS) }
            IconButton(onClick = viewModel::onTogglePlayPause, modifier = Modifier.size(72.dp)) {
                Icon(
                    imageVector = if (playback.isPlaying) LibreIcons.Pause else LibreIcons.Play,
                    contentDescription = stringResource(if (playback.isPlaying) R.string.player_pause else R.string.player_play),
                    tint = Color.White,
                    modifier = Modifier.size(56.dp),
                )
            }
            ControlButton(LibreIcons.Forward10, stringResource(R.string.player_seek_forward)) { viewModel.onSeekBy(GesturePolicy.SEEK_STEP_MS) }
        }

        // Barra inferior
        Column(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
            Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                val duration = playback.durationMs.coerceAtLeast(1)
                // Mientras el dedo arrastra la barra se muestra esa posición; si no, la real del video.
                val shown = if (dragging) dragPosition else playback.positionMs.toFloat()
                Text(formatDuration((shown / 1000).toLong()), color = Color.White, style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = shown.coerceIn(0f, duration.toFloat()),
                    onValueChange = { dragging = true; dragPosition = it },
                    onValueChangeFinished = { viewModel.onSeekTo(dragPosition.toLong()); dragging = false },
                    valueRange = 0f..duration.toFloat(),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text(formatDuration(playback.durationMs / 1000), color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    androidx.compose.material3.TextButton(onClick = { speedMenu = true }) {
                        Text(speedLabel(playback.speed), color = Color.White)
                    }
                    DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                        PlaybackSpeeds.forEach { speed ->
                            DropdownMenuItem(
                                text = { Text(speedLabel(speed)) },
                                trailingIcon = { if (abs(speed - playback.speed) < 0.01f) Icon(LibreIcons.Granted, contentDescription = null) },
                                onClick = { viewModel.onSpeedSelected(speed); speedMenu = false },
                            )
                        }
                    }
                }
                Row {
                    ControlButton(LibreIcons.Lock, stringResource(R.string.player_lock), viewModel::onToggleLock)
                    ControlButton(LibreIcons.Rotate, stringResource(R.string.player_rotation), viewModel::onCycleOrientation)
                    if (canPip) ControlButton(LibreIcons.PictureInPicture, stringResource(R.string.player_pip), onPip)
                }
            }
        }
    }
}

/** Botón redondo con icono blanco, usado en todos los controles. */
@Composable
private fun ControlButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = description, tint = Color.White)
    }
}

// Texto de la velocidad, por ejemplo "1x" o "1.5x".
private fun speedLabel(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else String.format(Locale.ROOT, "%sx", speed.toString().trimEnd('0'))

// --- Pistas de audio y subtítulos ---------------------------------------------------------------

// Una pista elegible (audio o subtítulo) con su nombre y si está seleccionada.
private data class TrackOption(val label: String, val group: Tracks.Group, val index: Int, val selected: Boolean)

// Pistas disponibles, separadas en audio y subtítulos.
private data class VideoTracks(val audio: List<TrackOption>, val text: List<TrackOption>)

/** Lee las pistas del video y se actualiza cuando cambian. */
@Composable
private fun rememberTracks(player: Player?): VideoTracks {
    var tracks by remember(player) { mutableStateOf(player?.currentTracks ?: Tracks.EMPTY) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(newTracks: Tracks) {
                tracks = newTracks
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
    return remember(tracks) {
        fun options(type: Int) = tracks.groups.filter { it.type == type && it.isSupported }.flatMap { group ->
            (0 until group.length).filter { group.isTrackSupported(it) }.map { i ->
                val format = group.getTrackFormat(i)
                val language = format.language?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.getDefault()) }
                    ?.replaceFirstChar { c -> c.titlecase(Locale.getDefault()) }
                TrackOption(
                    label = format.label ?: language ?: "#${i + 1}",
                    group = group,
                    index = i,
                    selected = group.isTrackSelected(i),
                )
            }
        }
        VideoTracks(audio = options(C.TRACK_TYPE_AUDIO), text = options(C.TRACK_TYPE_TEXT))
    }
}

// Activa la pista elegida.
private fun Player.select(track: TrackOption) {
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(track.group.type, false)
        .setOverrideForType(TrackSelectionOverride(track.group.mediaTrackGroup, track.index))
        .build()
}

// Apaga los subtítulos.
private fun Player.disableText() {
    trackSelectionParameters = trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
}

// --- Sistema: brillo, volumen, PiP -------------------------------------------------------------------

// Busca la ventana (Activity) que contiene este contexto.
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// Brillo actual de la pantalla (de 0 a 1).
private fun Activity.currentBrightness(): Float {
    val value = window.attributes.screenBrightness
    if (value >= 0f) return value
    return runCatching {
        android.provider.Settings.System.getInt(contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f
    }.getOrDefault(0.5f)
}

// Cambia el brillo solo dentro de la app, sin bajar de un mínimo para no dejar la pantalla negra.
private fun Activity.setBrightness(level: Float) {
    window.attributes = window.attributes.apply { screenBrightness = level.coerceIn(0.02f, 1f) }
}

// Volumen actual de multimedia (de 0 a 1).
private fun AudioManager.volumeLevel(): Float =
    getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / getStreamMaxVolume(AudioManager.STREAM_MUSIC)

// Cambia el volumen de multimedia.
private fun AudioManager.setVolumeLevel(level: Float) {
    setStreamVolume(AudioManager.STREAM_MUSIC, (level * getStreamMaxVolume(AudioManager.STREAM_MUSIC)).toInt(), 0)
}

// Indica si el teléfono permite la ventana flotante.
private fun Context.supportsPip(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

// Pasa el video a la ventana flotante.
private fun Activity.enterPip(hasMedia: Boolean) {
    if (!hasMedia || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val (w, h) = GesturePolicy.pipAspectRatio(null, null)
    enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(w, h)).build())
}

/** Avisa cuando la app entra o sale de la ventana flotante. */
@Composable
private fun rememberPictureInPictureState(activity: Activity?): Boolean {
    var inPip by remember { mutableStateOf(activity?.isInPictureInPictureMode == true) }
    DisposableEffect(activity) {
        val componentActivity = activity as? ComponentActivity
        val listener = Consumer<PictureInPictureModeChangedInfo> { inPip = it.isInPictureInPictureMode }
        componentActivity?.addOnPictureInPictureModeChangedListener(listener)
        onDispose { componentActivity?.removeOnPictureInPictureModeChangedListener(listener) }
    }
    return inPip
}
