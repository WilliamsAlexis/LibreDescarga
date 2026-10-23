package com.libredescarga.core.model

/** Todo lo necesario para ejecutar (y reintentar) una descarga. */
data class DownloadRequest(
    val url: String,
    val title: String,
    val uploader: String?,
    val thumbnailUrl: String?,
    val durationSeconds: Long?,
    val kind: MediaKind,
    val video: VideoSelection?,
    val audioFormat: AudioFormat?,
    val subtitle: SubtitleSelection?,
    val fileName: String,
    /** Tamaño estimado en el análisis (para avisar si falta espacio). */
    val estimatedBytes: Long? = null,
)

/**
 * Calidad de video elegida: un formato concreto ([formatId]) desde la hoja de
 * formatos, o una altura máxima ([maxHeight]) para descargas rápidas y por lotes.
 */
data class VideoSelection(
    val formatId: String?,
    val maxHeight: Int?,
    val container: VideoContainer,
    val formatHasAudio: Boolean,
)

data class SubtitleSelection(
    val languageCode: String,
    val isAutomatic: Boolean,
    /** true = incrustar en el video; false = archivo SRT aparte. */
    val embed: Boolean,
)

/**
 * - [QUEUED]: en la cola de la app, esperando un hueco (límite de descargas simultáneas).
 * - [WAITING]: ya asignada al sistema, esperando red (o Wi-Fi) para empezar.
 * - [RUNNING]: descargando.
 */
enum class DownloadStatus { QUEUED, WAITING, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED;

    val isActive: Boolean get() = this == QUEUED || this == WAITING || this == RUNNING
}

data class DownloadTask(
    val id: String,
    val request: DownloadRequest,
    val status: DownloadStatus,
    val progressPercent: Float,
    val speedBytesPerSecond: Long?,
    val etaSeconds: Long?,
    val totalBytes: Long?,
    val downloadedBytes: Long?,
    val outputUri: String?,
    val errorMessage: String?,
    val priority: Int,
    val createdAt: Long,
    val completedAt: Long?,
)
