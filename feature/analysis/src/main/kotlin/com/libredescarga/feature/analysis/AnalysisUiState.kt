package com.libredescarga.feature.analysis

import com.libredescarga.core.model.AudioFormat
import com.libredescarga.core.model.LinkPreview
import com.libredescarga.core.model.MediaError
import com.libredescarga.core.model.MediaInfo
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.model.PlaylistInfo
import com.libredescarga.core.model.SubtitleTrack
import com.libredescarga.core.model.VideoOption
import com.libredescarga.core.model.VideoQuality

/** Calidad configurada para la descarga rápida (si el usuario eligió una en Ajustes). */
sealed interface QuickDefault {
    data class Video(val quality: VideoQuality) : QuickDefault
    data class Audio(val format: AudioFormat) : QuickDefault
}

sealed interface AnalysisUiState {

    data object Loading : AnalysisUiState

    /** Cargando, pero ya se conocen el título y la miniatura (vista previa casi instantánea de YouTube y TikTok). */
    data class Preview(val preview: LinkPreview) : AnalysisUiState

    data class Error(val error: MediaError) : AnalysisUiState

    data class Single(
        val info: MediaInfo,
        val tab: MediaKind,
        val selectedVideo: VideoOption?,
        val selectedAudio: AudioFormat?,
        val subtitle: SubtitleTrack?,
        val embedSubtitle: Boolean,
        val quickDefault: QuickDefault?,
        val isSubmitting: Boolean,
        /** Espacio libre en el teléfono, para avisar si la descarga no cabe. */
        val availableBytes: Long? = null,
        /** El análisis completo sigue en curso: se muestran las opciones rápidas y pronto llegan más calidades. */
        val loadingMoreQualities: Boolean = false,
        /** Lo que el usuario ya eligió a mano, para no pisarlo cuando llega el análisis completo. */
        val tabTouched: Boolean = false,
        val videoTouched: Boolean = false,
        val audioTouched: Boolean = false,
    ) : AnalysisUiState {
        /** La descarga elegida parece no caber (necesita su tamaño dos veces: descarga y copia). */
        val lowStorage: Boolean
            get() {
                val size = selectedBytes ?: return false
                val free = availableBytes ?: return false
                return size * 2 > free
            }

        val canDownload: Boolean
            get() = !isSubmitting && when (tab) {
                MediaKind.VIDEO -> selectedVideo != null
                MediaKind.AUDIO -> selectedAudio != null
                MediaKind.IMAGE -> false // El análisis solo ofrece video y audio.
            }

        val selectedBytes: Long?
            get() = when (tab) {
                MediaKind.VIDEO -> selectedVideo?.estimatedBytes
                MediaKind.AUDIO -> info.audioOptions.firstOrNull { it.format == selectedAudio }?.estimatedBytes
                MediaKind.IMAGE -> null
            }
    }

    data class Playlist(
        val playlist: PlaylistInfo,
        val selectedIds: Set<String>,
        val kind: MediaKind,
        val videoQuality: VideoQuality,
        val audioFormat: AudioFormat,
        val isSubmitting: Boolean,
    ) : AnalysisUiState {
        val allSelected: Boolean get() = selectedIds.size == playlist.entries.size
        val canDownload: Boolean get() = selectedIds.isNotEmpty() && !isSubmitting
    }
}

sealed interface AnalysisEvent {
    data class Queued(val count: Int) : AnalysisEvent
    data class Failed(val error: MediaError) : AnalysisEvent
}

/** Calidades ofrecidas para descargas por lotes. */
internal val PlaylistVideoQualities = listOf(
    VideoQuality.P360,
    VideoQuality.P480,
    VideoQuality.P720,
    VideoQuality.P1080,
    VideoQuality.P1440,
    VideoQuality.P2160,
    VideoQuality.BEST,
)

internal val PlaylistAudioFormats = AudioFormat.entries.filter { it != AudioFormat.ASK }
