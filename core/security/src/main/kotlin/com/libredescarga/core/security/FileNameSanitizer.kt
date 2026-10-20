package com.libredescarga.core.security

/**
 * Convierte cualquier título en un nombre de archivo seguro:
 * sin separadores de ruta (evita path traversal), sin caracteres de control,
 * sin caracteres prohibidos en FAT32/exFAT (tarjetas SD) y con longitud limitada.
 */
object FileNameSanitizer {

    const val MAX_LENGTH = 120
    const val DEFAULT_NAME = "LibreDescarga"

    private val FORBIDDEN = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
    private val WHITESPACE = Regex("\\s+")
    private val RESERVED = Regex("^(CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9])(\\..*)?$", RegexOption.IGNORE_CASE)

    fun sanitize(name: String, fallback: String = DEFAULT_NAME): String {
        var result = name
            .replace(WHITESPACE, " ")
            .replace(FORBIDDEN, "_")
            .trim()
            .trimStart('.', ' ', '_')

        result = result.take(MAX_LENGTH).trimEnd('.', ' ')
        // Evita cortar un emoji (par sustituto) por la mitad.
        if (result.isNotEmpty() && result.last().isHighSurrogate()) result = result.dropLast(1)

        if (result.isBlank() || result.all { it == '_' || it == '.' }) return fallback
        if (RESERVED.matches(result)) result = "_$result"
        return result
    }
}
