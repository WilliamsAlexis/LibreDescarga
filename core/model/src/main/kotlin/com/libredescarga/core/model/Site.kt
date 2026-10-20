package com.libredescarga.core.model

/** Sitios con búsqueda por palabras clave soportada por yt-dlp. */
enum class SearchProvider(val displayName: String, val searchPrefix: String) {
    YOUTUBE("YouTube", "ytsearch"),
    SOUNDCLOUD("SoundCloud", "scsearch"),
    BILIBILI("Bilibili", "bilisearch"),
    NICONICO("Niconico", "nicosearch"),
}

/** Acceso directo de la cuadrícula de Inicio (predefinido o marcador propio). */
data class Site(
    val id: Long,
    val name: String,
    val url: String,
    val searchProvider: SearchProvider?,
    val isCustom: Boolean,
    val enabled: Boolean,
    val position: Int,
)
