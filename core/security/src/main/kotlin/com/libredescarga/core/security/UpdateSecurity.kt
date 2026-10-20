package com.libredescarga.core.security

import java.io.InputStream
import java.security.MessageDigest

/** Comprobaciones de seguridad de las actualizaciones (sin Android, para poder probarlas). */
object UpdateSecurity {

    private val SHA256 = Regex("^[0-9a-fA-F]{64}$")

    fun isValidSha256(text: String): Boolean = SHA256.matches(text.trim())

    /**
     * Solo se descarga desde GitHub y siempre por https:
     * github.com (las versiones publicadas) y githubusercontent.com (donde GitHub guarda los archivos).
     */
    fun isAllowedDownloadUrl(url: String): Boolean {
        val valid = UrlValidator.validate(url) as? UrlValidator.Result.Valid ?: return false
        if (!url.trim().startsWith("https://", ignoreCase = true)) return false
        val host = valid.host
        return host == "github.com" || host.endsWith(".github.com") || host.endsWith(".githubusercontent.com") ||
            host == "githubusercontent.com"
    }

    /** Resumen SHA-256 de un flujo, en hexadecimal minúscula. */
    fun sha256Hex(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return Hex.encode(digest.digest())
    }

    fun hashMatches(actualHex: String, expectedHex: String): Boolean =
        MessageDigest.isEqual(
            actualHex.trim().lowercase().toByteArray(Charsets.US_ASCII),
            expectedHex.trim().lowercase().toByteArray(Charsets.US_ASCII),
        )

    /**
     * El APK nuevo debe estar firmado por el mismo certificado que la app instalada.
     * Cada conjunto reúne los certificados actuales y, si los hay, los anteriores (rotación de claves).
     * Android vuelve a comprobarlo al instalar; esta es una barrera previa para no descargar de más.
     */
    fun sameSigner(installedCerts: Set<String>, candidateCerts: Set<String>): Boolean {
        if (installedCerts.isEmpty() || candidateCerts.isEmpty()) return false
        val installed = installedCerts.map { it.lowercase() }.toSet()
        return candidateCerts.any { it.lowercase() in installed }
    }

    /** Un APK de la misma versión o anterior nunca se instala (evita bajar de versión). */
    fun isNewer(installedVersionCode: Long, candidateVersionCode: Long): Boolean = candidateVersionCode > installedVersionCode
}

/** Nombres de dominio para la lista negra del usuario. */
object DomainRules {

    /** "https://www.Ejemplo.com/ruta" → "ejemplo.com". Devuelve null si no es un dominio válido. */
    fun normalize(input: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return null
        val host = (UrlValidator.validate(text) as? UrlValidator.Result.Valid)?.host ?: return null
        return host.removePrefix("www.").trimEnd('.').takeIf { it.contains('.') }
    }
}
