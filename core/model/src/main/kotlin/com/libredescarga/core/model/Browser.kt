package com.libredescarga.core.model

data class Bookmark(val url: String, val title: String, val createdAt: Long)

data class HistoryEntry(val url: String, val title: String, val visitedAt: Long)

/** Buscador del navegador integrado para las palabras que no son un enlace. */
object WebSearch {
    private const val ENGINE = "https://duckduckgo.com/?q="

    /** DuckDuckGo no rastrea las búsquedas. El texto se codifica para ir seguro en la URL. */
    fun url(query: String): String = ENGINE + java.net.URLEncoder.encode(query.trim(), "UTF-8")
}

/** Dirección de búsqueda en el propio sitio, para las categorías de Inicio. */
fun SearchProvider.webSearchUrl(query: String): String {
    val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
    return when (this) {
        SearchProvider.YOUTUBE -> "https://m.youtube.com/results?search_query=$encoded"
        SearchProvider.SOUNDCLOUD -> "https://soundcloud.com/search?q=$encoded"
        SearchProvider.BILIBILI -> "https://m.bilibili.com/search?keyword=$encoded"
        SearchProvider.NICONICO -> "https://www.nicovideo.jp/search/$encoded"
    }
}
