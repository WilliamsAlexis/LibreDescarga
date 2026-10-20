package com.libredescarga.core.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Archivo JSON de la copia de seguridad. Contiene historial, marcadores, listas, favoritos,
 * sitios propios y ajustes. Nunca incluye la carpeta privada ni sus archivos.
 */
@Serializable
internal data class BackupFile(
    val app: String = APP_ID,
    val format: Int = FORMAT,
    val createdAt: Long = 0,
    val settings: BackupSettings? = null,
    val searches: List<String> = emptyList(),
    val bookmarks: List<BackupBookmark> = emptyList(),
    val history: List<BackupHistory> = emptyList(),
    val playlists: List<BackupPlaylist> = emptyList(),
    val favorites: List<String> = emptyList(),
    val sites: List<BackupSite> = emptyList(),
    val blockedDomains: List<String> = emptyList(),
) {
    companion object {
        const val APP_ID = "LibreDescarga"
        const val FORMAT = 1
    }
}

@Serializable
internal data class BackupSettings(
    val themeMode: String? = null,
    val accentColor: String? = null,
    val useDynamicColor: Boolean? = null,
    val clipboardDetection: Boolean? = null,
    val wifiOnly: Boolean? = null,
    val maxConcurrentDownloads: Int? = null,
    val concurrentFragments: Int? = null,
    val speedLimitKbps: Int? = null,
    val defaultVideoQuality: String? = null,
    val defaultAudioFormat: String? = null,
    val hideDonationReminders: Boolean? = null,
)

@Serializable
internal data class BackupBookmark(val url: String, val title: String = "")

@Serializable
internal data class BackupHistory(val url: String, val title: String = "")

@Serializable
internal data class BackupPlaylist(val name: String, val items: List<BackupPlaylistItem> = emptyList())

@Serializable
internal data class BackupPlaylistItem(
    val uri: String,
    val title: String = "",
    val artist: String? = null,
    val durationMs: Long = 0,
    val kind: String = "AUDIO",
)

@Serializable
internal data class BackupSite(val name: String, val url: String)

internal object BackupCodec {

    /** Tope del archivo que se acepta al restaurar (una copia normal pesa unos pocos KB). */
    const val MAX_CHARS = 5_000_000

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    /** @throws IllegalArgumentException si el texto no es una copia válida de LibreDescarga. */
    fun decode(text: String): BackupFile {
        require(text.length <= MAX_CHARS) { "El archivo es demasiado grande" }
        // El marcador "app" debe venir escrito en el archivo: un "{}" vacío no es una copia.
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (error: Exception) {
            throw IllegalArgumentException("El archivo no es una copia de seguridad válida", error)
        }
        require(root != null && root.containsKey("app")) { "El archivo no es de LibreDescarga" }
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (error: Exception) {
            throw IllegalArgumentException("El archivo no es una copia de seguridad válida", error)
        }
        require(file.app == BackupFile.APP_ID) { "El archivo no es de LibreDescarga" }
        require(file.format in 1..BackupFile.FORMAT) { "Versión de copia no compatible" }
        return file
    }
}
