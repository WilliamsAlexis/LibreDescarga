package com.libredescarga.feature.analysis

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.libredescarga.core.domain.repository.PageImageResolver
import com.libredescarga.core.domain.repository.StorageRepository
import com.libredescarga.core.domain.repository.UserPreferencesRepository
import com.libredescarga.core.domain.usecase.AnalysisCacheKey
import com.libredescarga.core.domain.usecase.AnalyzeUrlUseCase
import com.libredescarga.core.domain.usecase.DownloadRequestFactory
import com.libredescarga.core.domain.usecase.DownloadRequestFactory.toEntry
import com.libredescarga.core.domain.usecase.EnqueueDownloadsUseCase
import com.libredescarga.core.domain.usecase.ImageDownloads
import com.libredescarga.core.domain.usecase.QualityPresets
import com.libredescarga.core.model.AnalisisPerf
import com.libredescarga.core.model.AnalysisUpdate
import com.libredescarga.core.model.AnalysisResult
import com.libredescarga.core.model.AudioFormat
import com.libredescarga.core.model.DownloadRequest
import com.libredescarga.core.model.MediaError
import com.libredescarga.core.model.MediaInfo
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.model.PlaylistInfo
import com.libredescarga.core.model.SubtitleSelection
import com.libredescarga.core.model.SubtitleTrack
import com.libredescarga.core.model.UserSettings
import com.libredescarga.core.model.VideoContainer
import com.libredescarga.core.model.VideoOption
import com.libredescarga.core.model.VideoQuality
import com.libredescarga.core.model.VideoSelection
import com.libredescarga.core.security.DomainMatcher
import com.libredescarga.core.security.FileNameSanitizer
import com.libredescarga.core.security.UrlValidator
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Lógica de la pantalla que analiza un enlace: obtiene las opciones de video/audio, guarda lo que el usuario elige
 * (calidad, subtítulos, nombre, recorte) y envía las descargas a la cola.
 */
@HiltViewModel(assistedFactory = AnalysisViewModel.Factory::class)
class AnalysisViewModel @AssistedInject constructor(
    // Analiza el enlace y devuelve el video, el audio o la lista disponibles.
    @Assisted private val url: String,
    private val analyzeUrl: AnalyzeUrlUseCase,
    // Agrega las descargas a la cola.
    private val enqueueDownloads: EnqueueDownloadsUseCase,
    // Preferencias del usuario (calidad y formato por defecto).
    private val preferences: UserPreferencesRepository,
    // Espacio libre del teléfono, para avisar si es poco.
    private val storage: StorageRepository,
    // Busca la imagen principal de una página cuando el enlace no es un video ni un audio (por ejemplo, un pin de Pinterest).
    private val pageImages: PageImageResolver,
) : ViewModel() {

    // Fábrica que permite crear el ViewModel con el enlace recibido.
    @AssistedFactory
    interface Factory {
        fun create(url: String): AnalysisViewModel
    }

    // Estado de la pantalla: cargando, un solo elemento, lista o error.
    private val _uiState = MutableStateFlow<AnalysisUiState>(AnalysisUiState.Loading)
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    // Eventos de una sola vez (cerrar la hoja, avisar...).
    private val _events = Channel<AnalysisEvent>(Channel.BUFFERED)
    val events: Flow<AnalysisEvent> = _events.receiveAsFlow()

    // Análisis en curso (se cancela al reintentar). Debe declararse antes de `init`, que ya llama a load().
    private var loadJob: Job? = null

    // Progreso ESTIMADO del análisis, de 0 a 1 (yt-dlp no informa un avance real; ver AnalysisProgress).
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    init {
        load()
    }

    // Vuelve a intentar el análisis después de un error (cancela el intento anterior si seguía en marcha).
    fun retry() = load()

    // Analiza el enlace y prepara las opciones iniciales según las preferencias del usuario; si falla, muestra el error.
    // Mientras tanto muestra el título y la miniatura (YouTube y TikTok) y, en YouTube, primero las opciones rápidas.
    private fun load() {
        loadJob?.cancel()
        _uiState.value = AnalysisUiState.Loading
        val host = AnalisisPerf.hostOf(url)
        val startedAt = System.nanoTime()
        AnalisisPerf.mark("análisis: inicio en el ViewModel", host)
        loadJob = viewModelScope.launch {
            // Páginas de imágenes (Pinterest...): sin video no hace falta yt-dlp, basta la imagen de la página.
            if (isImageSite(url)) {
                val image = AnalisisPerf.measure("imagen: página sin video, imagen directa", host) {
                    runCatching { pageImages.imageOnlyOf(url) }.getOrNull()
                }
                if (image != null && queueImageDownload(image)) {
                    AnalisisPerf.duration("análisis: TOTAL hasta tener resultado", (System.nanoTime() - startedAt) / 1_000_000, host)
                    return@launch
                }
            }

            // Vista previa casi instantánea mientras el motor trabaja; se descarta si el resultado llega antes.
            val preview = launch {
                val data = AnalisisPerf.measure("vista previa: oEmbed (título y miniatura)", host) {
                    runCatching { pageImages.previewOf(url) }.getOrNull()
                } ?: return@launch
                _uiState.update { if (it is AnalysisUiState.Loading) AnalysisUiState.Preview(data) else it }
            }

            // Barra de progreso estimada: sube con el tiempo y llega a 100 % solo cuando el análisis termina de verdad.
            val kind = if (AnalysisCacheKey.of(url).startsWith("youtube:")) AnalysisProgress.Kind.YOUTUBE else AnalysisProgress.Kind.OTHER
            val expectedMs = AnalysisProgress.expectedMs(kind)
            _progress.value = 0f
            val ticker = launch {
                repeat(MAX_PROGRESS_TICKS) {
                    _progress.value = AnalysisProgress.estimate((System.nanoTime() - startedAt) / 1_000_000, expectedMs)
                    delay(PROGRESS_TICK_MS)
                }
            }

            var failure: AnalysisUiState? = null
            try {
                val settings = AnalisisPerf.measure("análisis: leer preferencias", host) { preferences.settings.first() }
                val availableBytes = runCatching { storage.availableBytes() }.getOrNull()
                var firstResult = true
                analyzeUrl.stream(url).collect { update ->
                    preview.cancel()
                    if (firstResult) {
                        firstResult = false
                        AnalisisPerf.duration(
                            if (update.isComplete) "análisis: TOTAL hasta tener resultado" else "análisis: TOTAL hasta primer resultado (parcial)",
                            (System.nanoTime() - startedAt) / 1_000_000,
                            host,
                        )
                    }
                    _uiState.update { current -> stateFor(current, update, settings, availableBytes) }
                }
                // El análisis terminó: ya no se esperan más calidades.
                _uiState.update { if (it is AnalysisUiState.Single) it.copy(loadingMoreQualities = false) else it }
                val totalMs = (System.nanoTime() - startedAt) / 1_000_000
                AnalisisPerf.duration("análisis: TOTAL completo", totalMs, host)
                AnalysisProgress.record(kind, totalMs)
                _progress.value = 1f
            } catch (error: CancellationException) {
                throw error
            } catch (error: MediaError) {
                failure = errorOrImage(error)
            } catch (error: Exception) {
                failure = errorOrImage(MediaError.Unknown(error.message))
            }
            preview.cancel()
            ticker.cancel()
            failure?.let { _uiState.value = it }
        }
    }

    // Convierte un avance del análisis en el estado de la hoja, conservando lo que el usuario ya eligió.
    private fun stateFor(
        current: AnalysisUiState,
        update: AnalysisUpdate,
        settings: UserSettings,
        availableBytes: Long?,
    ): AnalysisUiState = when (val result = update.result) {
        is AnalysisResult.Playlist -> initialPlaylist(result.playlist, settings)
        is AnalysisResult.Single -> {
            // Mientras llega el análisis completo se ofrecen 1080p, 720p, 360p, 240p y 144p para poder elegir ya.
            val info = if (update.isComplete) {
                result.info
            } else {
                result.info.copy(videoOptions = QualityPresets.forPartial(result.info.videoOptions))
            }
            val fresh = initialSingle(info, settings)
                .copy(availableBytes = availableBytes, loadingMoreQualities = !update.isComplete)
            (current as? AnalysisUiState.Single)?.let { mergeSingle(it, fresh) } ?: fresh
        }
    }

    private fun isImageSite(link: String): Boolean =
        UrlValidator.hostOf(link)?.let { DomainMatcher.matches(it, IMAGE_SITES) != null } == true

    // Agrega a la cola la imagen indicada y avisa para cerrar la hoja. Devuelve false si no se pudo.
    private suspend fun queueImageDownload(imageUrl: String): Boolean {
        val request = ImageDownloads.request(imageUrl, null) ?: return false
        return try {
            _events.send(AnalysisEvent.Queued(enqueueDownloads(listOf(request))))
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false // No se pudo agregar a la cola: se sigue con el análisis normal o se muestra el error.
        }
    }

    // Si el enlace no tiene video ni audio pero la página tiene una imagen principal, se descarga esa imagen.
    // Si no, se muestra el error original.
    private suspend fun errorOrImage(error: MediaError): AnalysisUiState {
        Log.w(TAG, "No se pudo analizar el enlace", error)
        // Los sitios de video (YouTube, TikTok, Facebook...) nunca se resuelven con su miniatura: se muestra el error real.
        val host = UrlValidator.hostOf(url)
        val isVideoSite = host?.let { DomainMatcher.matches(it, VIDEO_SITES) != null } == true
        if (!isVideoSite && (error is MediaError.Unsupported || (isImageSite(url) && error is MediaError.Unknown))) {
            val image = AnalisisPerf.measure("imagen: buscar imagen principal de la página", host) {
                runCatching { pageImages.mainImageOf(url) }.getOrNull()
            }
            if (image != null && queueImageDownload(image)) return AnalysisUiState.Loading
        }
        return AnalysisUiState.Error(error)
    }

    // --- Un solo video -----------------------------------------------------------------

    // Cambia entre Video y Audio.
    fun onTabSelected(kind: MediaKind) = updateSingle { it.copy(tab = kind, tabTouched = true) }

    // Elige una calidad de video.
    fun onVideoSelected(option: VideoOption) = updateSingle { it.copy(selectedVideo = option, videoTouched = true) }

    // Elige un formato de audio.
    fun onAudioSelected(format: AudioFormat) = updateSingle { it.copy(selectedAudio = format, audioTouched = true) }

    // Elige un subtítulo (o ninguno).
    fun onSubtitleSelected(track: SubtitleTrack?) = updateSingle { it.copy(subtitle = track) }

    // Decide si el subtítulo se incrusta dentro del video.
    fun onEmbedSubtitleChange(embed: Boolean) = updateSingle { it.copy(embedSubtitle = embed) }

    // Envía a la cola la descarga con las opciones elegidas.
    fun onDownload() {
        val state = _uiState.value as? AnalysisUiState.Single ?: return
        if (!state.canDownload) return
        submit { listOf(buildRequest(state)) }
    }

    // Descarga directa con la calidad por defecto, sin elegir nada.
    fun onQuickDownload() {
        val state = _uiState.value as? AnalysisUiState.Single ?: return
        val entry = state.info.toEntry()
        val request = when (val quick = state.quickDefault ?: return) {
            is QuickDefault.Video -> DownloadRequestFactory.video(entry, quick.quality)
            is QuickDefault.Audio -> DownloadRequestFactory.audio(entry, quick.format)
        }
        submit { listOf(request) }
    }

    // --- Lista de reproducción ---------------------------------------------------------

    // Marca o desmarca un elemento de la lista.
    fun onEntryToggled(id: String) = updatePlaylist {
        it.copy(selectedIds = if (id in it.selectedIds) it.selectedIds - id else it.selectedIds + id)
    }

    // Marca o desmarca todos los elementos de la lista.
    fun onSelectAll(selectAll: Boolean) = updatePlaylist {
        it.copy(selectedIds = if (selectAll) it.playlist.entries.map { e -> e.id }.toSet() else emptySet())
    }

    // Elige si la lista se descarga como video o como audio.
    fun onPlaylistKindSelected(kind: MediaKind) = updatePlaylist { it.copy(kind = kind) }

    // Calidad de video para toda la lista.
    fun onPlaylistVideoQuality(quality: VideoQuality) = updatePlaylist { it.copy(videoQuality = quality) }

    // Formato de audio para toda la lista.
    fun onPlaylistAudioFormat(format: AudioFormat) = updatePlaylist { it.copy(audioFormat = format) }

    // Envía a la cola todos los elementos marcados de la lista.
    fun onDownloadPlaylist() {
        val state = _uiState.value as? AnalysisUiState.Playlist ?: return
        if (!state.canDownload) return
        submit {
            state.playlist.entries
                .filter { it.id in state.selectedIds }
                .map { entry ->
                    when (state.kind) {
                        MediaKind.VIDEO -> DownloadRequestFactory.video(entry, state.videoQuality)
                        MediaKind.AUDIO -> DownloadRequestFactory.audio(entry, state.audioFormat)
                        MediaKind.IMAGE -> error("Las listas de reproducción no contienen imágenes")
                    }
                }
        }
    }

    // --- Interno -----------------------------------------------------------------------

    // Manda las descargas a la cola y avisa el resultado; mientras tanto los botones quedan desactivados.
    private fun submit(buildRequests: () -> List<DownloadRequest>) {
        setSubmitting(true)
        viewModelScope.launch {
            try {
                val count = enqueueDownloads(buildRequests())
                _events.send(AnalysisEvent.Queued(count))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _events.send(AnalysisEvent.Failed(error as? MediaError ?: MediaError.Unknown(error.message)))
            } finally {
                setSubmitting(false)
            }
        }
    }

    // Marca si hay un envío en curso (para desactivar los botones).
    private fun setSubmitting(value: Boolean) {
        when (val state = _uiState.value) {
            is AnalysisUiState.Single -> _uiState.value = state.copy(isSubmitting = value)
            is AnalysisUiState.Playlist -> _uiState.value = state.copy(isSubmitting = value)
            else -> Unit
        }
    }

    // Arma la petición de descarga con todo lo elegido para un solo elemento.
    private fun buildRequest(state: AnalysisUiState.Single): DownloadRequest {
        val info = state.info
        val fileName = FileNameSanitizer.sanitize(info.title)
        return when (state.tab) {
            MediaKind.VIDEO -> {
                val option = requireNotNull(state.selectedVideo)
                DownloadRequest(
                    url = info.url,
                    title = info.title,
                    uploader = info.uploader,
                    thumbnailUrl = info.thumbnailUrl,
                    durationSeconds = info.durationSeconds,
                    kind = MediaKind.VIDEO,
                    // Una calidad predefinida (aún sin confirmar) se pide "hasta esa altura" y el motor elige el formato.
                    video = VideoSelection(
                        formatId = option.formatId.takeUnless { option.isPreset },
                        maxHeight = option.height.takeIf { option.isPreset },
                        container = option.container,
                        formatHasAudio = option.hasAudio,
                    ),
                    audioFormat = null,
                    subtitle = state.subtitle?.let {
                        SubtitleSelection(it.languageCode, it.isAutomatic, embed = state.embedSubtitle)
                    },
                    fileName = fileName,
                    estimatedBytes = state.selectedBytes,
                )
            }

            MediaKind.IMAGE -> error("El análisis no ofrece imágenes")

            MediaKind.AUDIO -> DownloadRequest(
                url = info.url,
                title = info.title,
                uploader = info.uploader,
                thumbnailUrl = info.thumbnailUrl,
                durationSeconds = info.durationSeconds,
                kind = MediaKind.AUDIO,
                video = null,
                audioFormat = requireNotNull(state.selectedAudio),
                subtitle = null,
                fileName = fileName,
                estimatedBytes = state.selectedBytes,
            )
        }
    }

    private inline fun updateSingle(transform: (AnalysisUiState.Single) -> AnalysisUiState.Single) {
        val state = _uiState.value as? AnalysisUiState.Single ?: return
        _uiState.value = transform(state)
    }

    private inline fun updatePlaylist(transform: (AnalysisUiState.Playlist) -> AnalysisUiState.Playlist) {
        val state = _uiState.value as? AnalysisUiState.Playlist ?: return
        _uiState.value = transform(state)
    }

    companion object {
        private const val TAG = "AnalysisViewModel"
        private const val PROGRESS_TICK_MS = 150L
        private const val MAX_PROGRESS_TICKS = 800 // 2 minutos: después la barra deja de moverse
        private const val DEFAULT_MAX_HEIGHT = 1080
        private val VIDEO_SITES = listOf(
            "youtube.com", "youtu.be", "youtube-nocookie.com", "tiktok.com", "facebook.com", "fb.watch", "fb.com",
            "instagram.com", "twitter.com", "x.com", "vimeo.com", "dailymotion.com", "twitch.tv", "reddit.com",
        )
        // Páginas donde un enlace sin video suele ser una imagen (por ejemplo, un pin de Pinterest).
        private val IMAGE_SITES = listOf("pinterest.com", "pin.it", "pinimg.com", "imgur.com", "flickr.com")

        /**
         * Une el resultado nuevo (por ejemplo, el completo tras el rápido) con el estado actual: conserva lo que el
         * usuario ya eligió o escribió y solo reemplaza lo que no tocó (calidad por defecto, lista de calidades...).
         */
        internal fun mergeSingle(old: AnalysisUiState.Single, fresh: AnalysisUiState.Single): AnalysisUiState.Single {
            val videos = fresh.info.videoOptions
            val audioFormats = fresh.info.audioOptions.map { it.format }
            val video = if (old.videoTouched) {
                val chosen = old.selectedVideo
                // Una calidad predefinida se cambia por la real de la misma altura; si el video no la tiene, la de por defecto.
                val same = if (chosen?.isPreset == true) {
                    QualityPresets.sameHeight(videos, chosen.height)
                } else {
                    videos.firstOrNull { it.formatId == chosen?.formatId }
                }
                same ?: fresh.selectedVideo
            } else {
                fresh.selectedVideo
            }
            val audio = if (old.audioTouched && old.selectedAudio in audioFormats) old.selectedAudio else fresh.selectedAudio
            val tabStillValid = when (old.tab) {
                MediaKind.VIDEO -> videos.isNotEmpty()
                MediaKind.AUDIO -> audioFormats.isNotEmpty()
                MediaKind.IMAGE -> false
            }
            return fresh.copy(
                tab = if (old.tabTouched && tabStillValid) old.tab else fresh.tab,
                selectedVideo = video,
                selectedAudio = audio,
                subtitle = old.subtitle?.takeIf { it in fresh.info.subtitles },
                embedSubtitle = old.embedSubtitle,
                isSubmitting = old.isSubmitting,
                tabTouched = old.tabTouched,
                videoTouched = old.videoTouched,
                audioTouched = old.audioTouched,
            )
        }

        internal fun initialSingle(info: MediaInfo, settings: UserSettings): AnalysisUiState.Single {
            val maxHeight = settings.defaultVideoQuality.maxHeight ?: DEFAULT_MAX_HEIGHT
            val preferBest = settings.defaultVideoQuality == VideoQuality.BEST
            val video = info.videoOptions.let { options ->
                if (preferBest) {
                    options.firstOrNull()
                } else {
                    options.firstOrNull { it.height <= maxHeight && it.container == VideoContainer.MP4 }
                        ?: options.firstOrNull { it.height <= maxHeight }
                        ?: options.lastOrNull()
                }
            }
            val availableAudio = info.audioOptions.map { it.format }
            val audio = settings.defaultAudioFormat.takeIf { it in availableAudio }
                ?: AudioFormat.MP3_320.takeIf { it in availableAudio }
                ?: availableAudio.firstOrNull()

            val preferAudio = settings.defaultVideoQuality == VideoQuality.ASK &&
                settings.defaultAudioFormat != AudioFormat.ASK
            val tab = when {
                video == null -> MediaKind.AUDIO
                audio == null -> MediaKind.VIDEO
                preferAudio -> MediaKind.AUDIO
                else -> MediaKind.VIDEO
            }

            val quickDefault = when {
                settings.defaultVideoQuality != VideoQuality.ASK && video != null ->
                    QuickDefault.Video(settings.defaultVideoQuality)
                settings.defaultAudioFormat != AudioFormat.ASK && audio != null ->
                    QuickDefault.Audio(settings.defaultAudioFormat)
                else -> null
            }

            return AnalysisUiState.Single(
                info = info,
                tab = tab,
                selectedVideo = video,
                selectedAudio = audio,
                subtitle = null,
                embedSubtitle = false,
                quickDefault = quickDefault,
                isSubmitting = false,
            )
        }

        internal fun initialPlaylist(playlist: PlaylistInfo, settings: UserSettings) = AnalysisUiState.Playlist(
            playlist = playlist,
            selectedIds = playlist.entries.map { it.id }.toSet(),
            kind = if (settings.defaultVideoQuality == VideoQuality.ASK &&
                settings.defaultAudioFormat != AudioFormat.ASK
            ) {
                MediaKind.AUDIO
            } else {
                MediaKind.VIDEO
            },
            videoQuality = settings.defaultVideoQuality.takeIf { it != VideoQuality.ASK } ?: VideoQuality.P720,
            audioFormat = settings.defaultAudioFormat.takeIf { it != AudioFormat.ASK } ?: AudioFormat.MP3_320,
            isSubmitting = false,
        )
    }
}
