package com.libredescarga.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.libredescarga.core.model.AudioFormat
import com.libredescarga.core.model.MediaError
import com.libredescarga.core.model.VideoOption
import com.libredescarga.core.model.VideoQuality

/** "1080p60 HDR", o "Calidad original" si no se conoce la resolución. */
@Composable
fun VideoOption.qualityLabel(): String {
    if (height <= 0) return stringResource(R.string.core_ui_quality_original)
    val fpsSuffix = fps?.takeIf { it >= 48 }?.toString().orEmpty()
    val hdrSuffix = if (isHdr) " HDR" else ""
    return "${height}p$fpsSuffix$hdrSuffix"
}

@Composable
fun VideoQuality.label(): String = when (this) {
    VideoQuality.ASK -> stringResource(R.string.core_ui_quality_ask)
    VideoQuality.BEST -> stringResource(R.string.core_ui_quality_best)
    else -> "${maxHeight}p"
}

@Composable
fun AudioFormat.label(): String = when (this) {
    AudioFormat.ASK -> stringResource(R.string.core_ui_quality_ask)
    AudioFormat.MP3_128, AudioFormat.MP3_192, AudioFormat.MP3_320 ->
        stringResource(R.string.core_ui_audio_mp3, bitrateKbps!!)
    AudioFormat.M4A -> stringResource(R.string.core_ui_audio_m4a)
    AudioFormat.OPUS -> stringResource(R.string.core_ui_audio_opus)
}

/** Mensaje claro para cada error de análisis o descarga. */
@Composable
fun MediaError.userMessage(): String = when (this) {
    is MediaError.InvalidUrl -> stringResource(R.string.core_ui_error_invalid_url)
    is MediaError.ForbiddenScheme -> stringResource(R.string.core_ui_error_forbidden_scheme)
    is MediaError.BlockedSite -> stringResource(R.string.core_ui_error_blocked, domain)
    is MediaError.DrmProtected -> stringResource(R.string.core_ui_error_drm)
    is MediaError.LiveStream -> stringResource(R.string.core_ui_error_live)
    is MediaError.Unsupported -> stringResource(R.string.core_ui_error_unsupported)
    is MediaError.NotPublic -> stringResource(R.string.core_ui_error_not_public)
    is MediaError.AgeRestricted -> stringResource(R.string.core_ui_error_age_restricted)
    is MediaError.BotCheck -> stringResource(R.string.core_ui_error_bot_check)
    is MediaError.Unavailable -> stringResource(R.string.core_ui_error_unavailable)
    is MediaError.Network -> stringResource(R.string.core_ui_error_network)
    is MediaError.InsufficientStorage -> stringResource(R.string.core_ui_error_storage)
    is MediaError.Unknown -> details?.takeIf { it.isNotBlank() }
        ?.let { stringResource(R.string.core_ui_error_unknown_details, it) }
        ?: stringResource(R.string.core_ui_error_unknown)
}
