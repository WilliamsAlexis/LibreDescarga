package com.libredescarga.core.model

/**
 * Las descargas fallidas guardan un código estable (no un texto traducido),
 * para que la interfaz muestre el mensaje en el idioma actual.
 */
object DownloadErrorCodes {
    const val DRM = "drm"
    const val LIVE = "live"
    const val UNSUPPORTED = "unsupported"
    const val NOT_PUBLIC = "not_public"
    const val AGE_RESTRICTED = "age_restricted"
    const val BOT_CHECK = "bot_check"
    const val UNAVAILABLE = "unavailable"
    const val NETWORK = "network"
    const val STORAGE = "storage"
    const val BLOCKED = "blocked"
    const val INVALID_URL = "invalid_url"
    const val UNKNOWN_PREFIX = "unknown:"

    fun fromError(error: Throwable): String = when (error) {
        is MediaError.DrmProtected -> DRM
        is MediaError.LiveStream -> LIVE
        is MediaError.Unsupported -> UNSUPPORTED
        is MediaError.NotPublic -> NOT_PUBLIC
        is MediaError.AgeRestricted -> AGE_RESTRICTED
        is MediaError.BotCheck -> BOT_CHECK
        is MediaError.Unavailable -> UNAVAILABLE
        is MediaError.Network -> NETWORK
        is MediaError.InsufficientStorage -> STORAGE
        is MediaError.BlockedSite -> BLOCKED + ":" + error.domain
        is MediaError.InvalidUrl, is MediaError.ForbiddenScheme -> INVALID_URL
        is MediaError.Unknown -> UNKNOWN_PREFIX + error.details.orEmpty()
        else -> UNKNOWN_PREFIX + (error.message ?: error::class.java.simpleName)
    }

    fun toError(code: String?): MediaError = when {
        code == null -> MediaError.Unknown(null)
        code == DRM -> MediaError.DrmProtected()
        code == LIVE -> MediaError.LiveStream()
        code == UNSUPPORTED -> MediaError.Unsupported()
        code == NOT_PUBLIC -> MediaError.NotPublic()
        code == AGE_RESTRICTED -> MediaError.AgeRestricted()
        code == BOT_CHECK -> MediaError.BotCheck()
        code == UNAVAILABLE -> MediaError.Unavailable()
        code == NETWORK -> MediaError.Network()
        code == STORAGE -> MediaError.InsufficientStorage()
        code.startsWith(BLOCKED) -> MediaError.BlockedSite(code.substringAfter(':', ""))
        code == INVALID_URL -> MediaError.InvalidUrl()
        code.startsWith(UNKNOWN_PREFIX) -> MediaError.Unknown(code.removePrefix(UNKNOWN_PREFIX).ifBlank { null })
        else -> MediaError.Unknown(code)
    }
}
