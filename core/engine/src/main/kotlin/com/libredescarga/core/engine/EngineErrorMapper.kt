package com.libredescarga.core.engine

import com.libredescarga.core.model.MediaError

/** Traduce los mensajes de error de yt-dlp a [MediaError] con un mensaje claro para el usuario. */
object EngineErrorMapper {

    // Videos con restricción de edad: YouTube exige iniciar sesión y la app no usa cuentas.
    private val AGE_RESTRICTED = listOf("confirm your age", "age-restricted", "age restricted", "inappropriate for some users")
    // "Sign in to confirm you're not a bot": bloqueo temporal de YouTube; se distingue de un contenido privado.
    private val BOT_CHECK = listOf("not a bot")
    private val NOT_PUBLIC = listOf(
        "private video", "login required", "sign in", "members-only", "members only",
        "requires authentication", "account is private", "this video is only available for",
    )
    private val UNAVAILABLE = listOf(
        "video unavailable", "has been removed", "http error 404", "not available in your country",
        "geo restricted", "geo-restricted", "no longer available", "does not exist",
    )
    private val NETWORK = listOf(
        "unable to download webpage", "timed out", "name resolution", "network is unreachable",
        "connection reset", "connection refused", "failed to resolve", "temporary failure",
        "no address associated", "unable to connect", "ssl:",
    )
    private val STORAGE = listOf("no space left", "enospc")

    fun map(raw: String?): MediaError {
        val text = raw.orEmpty()
        val lower = text.lowercase()
        return when {
            "drm" in lower -> MediaError.DrmProtected()
            "live event will begin" in lower || "is live" in lower || "premieres in" in lower ->
                MediaError.LiveStream()
            "unsupported url" in lower -> MediaError.Unsupported()
            "is not a valid url" in lower -> MediaError.InvalidUrl()
            STORAGE.any { it in lower } -> MediaError.InsufficientStorage()
            AGE_RESTRICTED.any { it in lower } -> MediaError.AgeRestricted()
            BOT_CHECK.any { it in lower } -> MediaError.BotCheck()
            NOT_PUBLIC.any { it in lower } -> MediaError.NotPublic()
            UNAVAILABLE.any { it in lower } -> MediaError.Unavailable()
            NETWORK.any { it in lower } -> MediaError.Network()
            else -> MediaError.Unknown(lastErrorLine(text))
        }
    }

    /** La última línea "ERROR: ..." de la salida, sin el prefijo. */
    fun lastErrorLine(text: String): String? {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        return lines.lastOrNull { it.startsWith("ERROR:") }?.removePrefix("ERROR:")?.trim()
            ?: lines.lastOrNull()
    }
}
