package com.libredescarga.feature.analysis

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.libredescarga.core.domain.repository.PageImageResolver
import com.libredescarga.core.domain.repository.StorageRepository
import com.libredescarga.core.domain.repository.UserPreferencesRepository
import com.libredescarga.core.domain.usecase.AnalyzeUrlUseCase
import com.libredescarga.core.domain.usecase.DownloadRequestFactory
import com.libredescarga.core.domain.usecase.DownloadRequestFactory.toEntry
import com.libredescarga.core.domain.usecase.EnqueueDownloadsUseCase
import com.libredescarga.core.domain.usecase.ImageDownloads
import com.libredescarga.core.model.AnalysisResult
import com.libredescarga.core.model.AudioFormat
import com.libredescarga.core.model.DownloadRequest
import com.libredescarga.core.model.MediaError
import com.libredescarga.core.model.MediaInfo
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.model.PlaylistInfo
import com.libredescarga.core.model.SubtitleSelection
import com.libredescarga.core.model.SubtitleTrack
import com.libredescarga.core.model.TrimRange
import com.libredescarga.core.model.UserSettings
import com.libredescarga.core.model.VideoContainer
import com.libredescarga.core.model.VideoOption
import com.libredescarga.core.model.VideoQuality
import com.libredescarga.core.model.VideoSelection
import com.libredescarga.core.security.DomainMatcher
import com.libredescarga.core.security.FileNameSanitizer
import com.libredescarga.core.security.UrlValidator
import com.libredescarga.core.ui.parseTimeInput
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
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

    init {
        load()
    }

    // Vuelve a intentar el análisis después de un error.
    fun retry() = load()

    // Analiza el enlace y prepara las opciones iniciales según las preferencias del usuario; si falla, muestra el error.
    private fun load() {
        _uiState.value = AnalysisUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                val result = analyzeUrl(url)
                val settings = preferences.settings.first()
                when (result) {
                    is AnalysisResult.Single -> initialSingle(result.info, settings)
                        .copy(availableBytes = runCatching { storage.availableBytes() }.getOrNull())
                    is AnalysisResult.Playlist -> initialPlaylist(result.playlist, settings)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: MediaError) {
                errorOrImage(error)
            } catch (error: Exception) {
                errorOrImage(MediaError.Unknown(error.message))
            }
        }
    }

    // Si el enlace no tiene video ni audio pero la página tiene una imagen principal, se descarga esa imagen.
    // Si no, se muestra el error original.
    private suspend fun errorOrImage(error: MediaError): AnalysisUiState {
        Log.w(TAG, "No se pudo analizar el enlace", error)
        // Los sitios de video (YouTube...) nunca se resuelven con su miniatura: se muestra el error real.
        val isVideoSite = UrlValidator.hostOf(url)?.let { DomainMatcher.matches(it, VIDEO_SITES) != null } == true
        if (!isVideoSite && (error is MediaError.Unsupported || error is MediaError.Unknown)) {
            val image = runCatching { pageImages.mainImageOf(url) }.getOrNull()
            val request = image?.let { ImageDownloads.request(it, null) }
            if (request != null) {
                try {
                    _events.send(AnalysisEvent.Queued(enqueueDownloads(listOf(request))))
                    return AnalysisUiState.Loading
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // No se pudo agregar a la cola: se muestra el error del análisis.
                }
            }
        }
        return AnalysisUiState.Error(error)
    }

    // --- Un solo video -----------------------------------------------------------------

    // Cambia entre Video y Audio.
    fun onTabSelected(kind: MediaKind) = updateSingle { it.copy(tab = kind) }

    // Elige una calidad de video.
    fun onVideoSelected(option: VideoOption) = updateSingle { it.copy(selectedVideo = option) }

    // Elige un formato de audio.
    fun onAudioSelected(format: AudioFormat) = updateSingle { it.copy(selectedAudio = format) }

    // Elige un subtítulo (o ninguno).
    fun onSubtitleSelected(track: SubtitleTrack?) = updateSingle { it.copy(subtitle = track) }

    // Decide si el subtítulo se incrusta dentro del video.
    fun onEmbedSubtitleChange(embed: Boolean) = updateSingle { it.copy(embedSubtitle = embed) }

    // Cambia el nombre del archivo.
    fun onFileNameChange(name: String) = updateSingle { it.copy(fileName = name) }

    // Cambia el inicio del recorte y lo valida.
    fun onTrimStartChange(text: String) = updateSingle { validateTrim(it.copy(trimStart = text)) }

    // Cambia el final del recorte y lo valida.
    fun onTrimEndChange(text: String) = updateSingle { validateTrim(it.copy(trimEnd = text)) }

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
        val trim = trimRange(state)
        val fileName = FileNameSanitizer.sanitize(
            name = state.fileName,
            fallback = FileNameSanitizer.sanitize(info.title),
        )
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
                    video = VideoSelection(
                        formatId = option.formatId,
                        maxHeight = null,
                        container = option.container,
                        formatHasAudio = option.hasAudio,
                    ),
                    audioFormat = null,
                    subtitle = state.subtitle?.let {
                        SubtitleSelection(it.languageCode, it.isAutomatic, embed = state.embedSubtitle)
                    },
                    trim = trim,
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
                trim = trim,
                fileName = fileName,
                estimatedBytes = state.selectedBytes,
            )
        }
    }

    // Convierte el inicio y fin escritos en un rango de recorte; null si no se pidió recorte.
    private fun trimRange(state: AnalysisUiState.Single): TrimRange? {
        val start = parseTimeInput(state.trimStart)
        val end = parseTimeInput(state.trimEnd)
        if (start == null && end == null) return null
        return TrimRange(startSeconds = start ?: 0, endSeconds = end)
    }

    // Marca error si el recorte es inválido (por ejemplo, el final antes del inicio).
    private fun validateTrim(state: AnalysisUiState.Single): AnalysisUiState.Single {
        val startText = state.trimStart.trim()
        val endText = state.trimEnd.trim()
        val start = parseTimeInput(startText)
        val end = parseTimeInput(endText)
        val duration = state.info.durationSeconds
        val invalid = (startText.isNotEmpty() && start == null) ||
            (endText.isNotEmpty() && end == null) ||
            (start != null && end != null && start >= end) ||
            (duration != null && start != null && start >= duration) ||
            (duration != null && end != null && end > duration)
        return state.copy(trimError = invalid)
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
        private const val DEFAULT_MAX_HEIGHT = 1080
        private val VIDEO_SITES = listOf("youtube.com", "youtu.be", "youtube-nocookie.com")

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
                fileName = FileNameSanitizer.sanitize(info.title),
                trimStart = "",
                trimEnd = "",
                trimError = false,
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
