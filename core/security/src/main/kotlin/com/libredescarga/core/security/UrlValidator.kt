package com.libredescarga.core.security

/**
 * Valida todo enlace antes de usarlo:
 * - solo se aceptan http y https (http se convierte a https, la app no usa tráfico sin cifrar);
 * - se bloquean file://, content://, javascript:, data:, intent: y cualquier otro esquema;
 * - se rechazan credenciales incrustadas (usuario:clave@host) para evitar enlaces engañosos.
 */
object UrlValidator {

    sealed interface Result {
        data class Valid(val url: String, val host: String) : Result
        data object ForbiddenScheme : Result
        data object NotAUrl : Result
    }

    private val SCHEME = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):")
    private val HTTP_URL = Regex("^https?://([^/?#\\s]+)([/?#][^\\s]*)?$", RegexOption.IGNORE_CASE)
    private val HOST = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")
    private val BARE_DOMAIN = Regex(
        "^(www\\.)?[a-z0-9-]+(\\.[a-z0-9-]+)+(:\\d{1,5})?([/?#][^\\s]*)?$",
        RegexOption.IGNORE_CASE,
    )
    private val URL_IN_TEXT = Regex("https?://[^\\s\"'<>]+", RegexOption.IGNORE_CASE)
    private const val TRAILING_PUNCTUATION = ".,;:!?)]}»"

    fun validate(input: String): Result {
        val text = input.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) {
            return if (SCHEME.containsMatchIn(text) && !text.startsWithHttp()) {
                Result.ForbiddenScheme
            } else {
                Result.NotAUrl
            }
        }

        val candidate = when {
            text.startsWith("https://", ignoreCase = true) -> text
            text.startsWith("http://", ignoreCase = true) -> "https://" + text.substring(7)
            SCHEME.containsMatchIn(text) && !looksLikeHostWithPort(text) -> return Result.ForbiddenScheme
            BARE_DOMAIN.matches(text) -> "https://$text"
            else -> return Result.NotAUrl
        }

        val match = HTTP_URL.matchEntire(candidate) ?: return Result.NotAUrl
        val authority = match.groupValues[1]
        if ('@' in authority) return Result.NotAUrl

        val host = authority.substringBefore(':').lowercase()
        if (!HOST.matches(host)) return Result.NotAUrl

        val port = authority.substringAfter(':', missingDelimiterValue = "")
        if (port.isNotEmpty() && (port.toIntOrNull() ?: -1) !in 1..65535) return Result.NotAUrl

        val rest = match.groupValues[2]
        return Result.Valid(url = "https://$authority$rest", host = host)
    }

    /** Devuelve la primera URL http(s) de un texto compartido (por ejemplo, "Mira esto: https://..."). */
    fun extractFirstUrl(text: String): String? =
        URL_IN_TEXT.find(text)?.value?.trimEnd { it in TRAILING_PUNCTUATION }

    /** Dominio del enlace, o null si no es válido. */
    fun hostOf(url: String): String? = (validate(url) as? Result.Valid)?.host

    private fun String.startsWithHttp(): Boolean =
        startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

    // "ejemplo.com:8080/ruta" parece un esquema ("ejemplo.com:") pero es un dominio con puerto.
    private fun looksLikeHostWithPort(text: String): Boolean = BARE_DOMAIN.matches(text)
}
