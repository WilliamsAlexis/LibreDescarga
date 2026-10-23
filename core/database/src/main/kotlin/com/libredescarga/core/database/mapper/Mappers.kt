package com.libredescarga.core.database.mapper

import com.libredescarga.core.database.entity.DownloadEntity
import com.libredescarga.core.database.entity.SiteEntity
import com.libredescarga.core.model.AudioFormat
import com.libredescarga.core.model.DownloadRequest
import com.libredescarga.core.model.DownloadStatus
import com.libredescarga.core.model.DownloadTask
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.model.SearchProvider
import com.libredescarga.core.model.Site
import com.libredescarga.core.model.SubtitleSelection
import com.libredescarga.core.model.VideoContainer
import com.libredescarga.core.model.VideoSelection

// Convierte un texto guardado en la base de datos a su valor de lista; devuelve null si no existe o es desconocido.
private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
    name?.let { value -> enumValues<T>().firstOrNull { it.name == value } }

// De registro de base de datos a la petición original de descarga (lo que el usuario eligió).
fun DownloadEntity.toRequest(): DownloadRequest = DownloadRequest(
    url = url,
    title = title,
    uploader = uploader,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    kind = enumOrNull<MediaKind>(kind) ?: MediaKind.VIDEO,
    video = videoContainer?.let { container ->
        VideoSelection(
            formatId = videoFormatId,
            maxHeight = videoMaxHeight,
            container = enumOrNull<VideoContainer>(container) ?: VideoContainer.MP4,
            formatHasAudio = videoFormatHasAudio,
        )
    },
    audioFormat = enumOrNull<AudioFormat>(audioFormat),
    subtitle = subtitleLanguage?.let {
        SubtitleSelection(languageCode = it, isAutomatic = subtitleAutomatic, embed = subtitleEmbed)
    },
    fileName = fileName,
    estimatedBytes = estimatedBytes,
)

// De registro de base de datos a descarga completa, con su progreso y estado.
fun DownloadEntity.toModel(): DownloadTask = DownloadTask(
    id = id,
    request = toRequest(),
    status = enumOrNull<DownloadStatus>(status) ?: DownloadStatus.FAILED,
    progressPercent = progressPercent,
    speedBytesPerSecond = speedBytesPerSecond,
    etaSeconds = etaSeconds,
    totalBytes = totalBytes,
    downloadedBytes = downloadedBytes,
    outputUri = outputUri,
    errorMessage = errorMessage,
    priority = priority,
    createdAt = createdAt,
    completedAt = completedAt,
)

// De petición de descarga a registro nuevo de base de datos (empieza en cola, sin progreso).
fun DownloadRequest.toEntity(id: String, createdAt: Long, priority: Int = 0): DownloadEntity = DownloadEntity(
    id = id,
    url = url,
    title = title,
    uploader = uploader,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    kind = kind.name,
    videoFormatId = video?.formatId,
    videoMaxHeight = video?.maxHeight,
    videoContainer = video?.container?.name,
    videoFormatHasAudio = video?.formatHasAudio ?: false,
    audioFormat = audioFormat?.name,
    subtitleLanguage = subtitle?.languageCode,
    subtitleAutomatic = subtitle?.isAutomatic ?: false,
    subtitleEmbed = subtitle?.embed ?: false,
    // Columnas heredadas del antiguo recorte: ya no se usan (quitarlas exige una migración de la base de datos).
    trimStartSeconds = null,
    trimEndSeconds = null,
    fileName = fileName,
    estimatedBytes = estimatedBytes,
    status = DownloadStatus.QUEUED.name,
    progressPercent = 0f,
    speedBytesPerSecond = null,
    etaSeconds = null,
    totalBytes = null,
    downloadedBytes = null,
    outputUri = null,
    errorMessage = null,
    priority = priority,
    createdAt = createdAt,
    completedAt = null,
)

// De registro de base de datos a sitio que se muestra en la pantalla.
fun SiteEntity.toModel(): Site = Site(
    id = id,
    name = name,
    url = url,
    searchProvider = enumOrNull<SearchProvider>(searchProvider),
    isCustom = isCustom,
    enabled = enabled,
    position = position,
)
