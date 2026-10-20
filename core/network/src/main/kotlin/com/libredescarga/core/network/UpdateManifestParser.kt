package com.libredescarga.core.network

import com.libredescarga.core.model.ApkAsset
import com.libredescarga.core.model.AppUpdate
import com.libredescarga.core.model.UpdateException
import com.libredescarga.core.model.UpdateFailure
import com.libredescarga.core.security.UpdateSecurity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Lee `update.json`, el archivo público que se publica junto a cada versión en GitHub Releases.
 *
 * ```json
 * {
 *   "versionCode": 2,
 *   "versionName": "1.0.1",
 *   "minSdk": 24,
 *   "notes": { "es": "Mejoras", "en": "Improvements" },
 *   "apks": [
 *     { "abi": "arm64-v8a", "url": "https://github.com/…/releases/download/v1.0.1/…-arm64-v8a.apk", "sha256": "…" }
 *   ],
 *   "apkUrl": "https://github.com/…/…-universal.apk",
 *   "sha256": "…"
 * }
 * ```
 */
object UpdateManifestParser {

    private const val MAX_NOTES = 4_000
    private const val MAX_VERSION_NAME = 32
    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    @Serializable
    private data class Manifest(
        val versionCode: Long = 0,
        val versionName: String = "",
        val minSdk: Int = 24,
        val notes: JsonElement? = null,
        val apkUrl: String? = null,
        val sha256: String? = null,
        val apks: List<Apk> = emptyList(),
    )

    @Serializable
    private data class Apk(val abi: String? = null, val url: String = "", val sha256: String = "")

    /** @param language idioma de las notas ("es", "en"...); si no hay, se usa español, inglés o la primera. */
    fun parse(text: String, language: String = "es"): AppUpdate {
        val manifest = try {
            json.decodeFromString<Manifest>(text)
        } catch (error: Exception) {
            throw UpdateException(UpdateFailure.INVALID_MANIFEST, "JSON no válido", error)
        }
        if (manifest.versionCode <= 0) invalid("versionCode no válido")
        val versionName = manifest.versionName.trim()
        if (versionName.isEmpty() || versionName.length > MAX_VERSION_NAME) invalid("versionName no válido")

        // Solo se conservan los APK con dirección permitida (https y GitHub) y resumen SHA-256 correcto.
        val candidates = manifest.apks.map { ApkAsset(it.abi?.trim()?.ifEmpty { null }, it.url.trim(), it.sha256.trim()) } +
            listOfNotNull(
                if (!manifest.apkUrl.isNullOrBlank()) ApkAsset(null, manifest.apkUrl.trim(), manifest.sha256.orEmpty().trim()) else null,
            )
        val assets = candidates.filter { UpdateSecurity.isAllowedDownloadUrl(it.url) && UpdateSecurity.isValidSha256(it.sha256) }
            .map { it.copy(sha256 = it.sha256.lowercase()) }
        if (assets.isEmpty()) invalid("Ningún APK válido")

        return AppUpdate(
            versionCode = manifest.versionCode,
            versionName = versionName,
            notes = notesFor(manifest.notes, language).take(MAX_NOTES),
            minSdk = manifest.minSdk.coerceAtLeast(1),
            assets = assets,
        )
    }

    private fun notesFor(notes: JsonElement?, language: String): String = when (notes) {
        null -> ""
        is JsonPrimitive -> notes.contentOrNull.orEmpty()
        is JsonObject -> listOf(language, "es", "en").firstNotNullOfOrNull { key ->
            (notes[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        } ?: notes.values.firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        else -> ""
    }.trim()

    private fun invalid(reason: String): Nothing = throw UpdateException(UpdateFailure.INVALID_MANIFEST, reason)
}
