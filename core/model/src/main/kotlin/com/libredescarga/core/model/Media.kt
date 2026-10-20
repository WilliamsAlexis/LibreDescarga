package com.libredescarga.core.model

/** Tipo de archivo que se descarga: video, audio o imagen. */
enum class MediaKind { VIDEO, AUDIO, IMAGE }

/** Elemento breve: resultado de búsqueda o elemento de una lista de reproducción. */
data class MediaEntry(
    val id: String,
    val url: String,
    val title: String,
    val uploader: String?,
    val durationSeconds: Long?,
    val thumbnailUrl: String?,
)

/** Información completa de un video o audio analizado. */
data class MediaInfo(
    val id: String,
    val url: String,
    val title: String,
    val uploader: String?,
    val durationSeconds: Long?,
    val viewCount: Long?,
    val thumbnailUrl: String?,
    val extractor: String,
    val videoOptions: List<VideoOption>,
    val audioOptions: List<AudioOption>,
    val subtitles: List<SubtitleTrack>,
)

/** Una calidad de video disponible (por ejemplo, 1080p60 HDR en MP4). */
data class VideoOption(
    val formatId: String,
    val height: Int,
    val fps: Int?,
    val isHdr: Boolean,
    val container: VideoContainer,
    val hasAudio: Boolean,
    val estimatedBytes: Long?,
)

enum class VideoContainer(val extension: String) { MP4("mp4"), WEBM("webm") }

data class AudioOption(
    val format: AudioFormat,
    val estimatedBytes: Long?,
)

data class SubtitleTrack(
    val languageCode: String,
    val name: String?,
    val isAutomatic: Boolean,
)

data class PlaylistInfo(
    val id: String?,
    val url: String,
    val title: String,
    val uploader: String?,
    val entries: List<MediaEntry>,
)

sealed interface AnalysisResult {
    data class Single(val info: MediaInfo) : AnalysisResult
    data class Playlist(val playlist: PlaylistInfo) : AnalysisResult
}
