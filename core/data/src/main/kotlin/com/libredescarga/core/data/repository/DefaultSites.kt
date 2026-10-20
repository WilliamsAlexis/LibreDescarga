package com.libredescarga.core.data.repository

import com.libredescarga.core.database.entity.SiteEntity
import com.libredescarga.core.model.SearchProvider

/**
 * Accesos directos iniciales a sitios compatibles con yt-dlp. Se muestran con
 * una inicial y un color propio (sin logos de marcas). El usuario puede
 * quitar estos sitios y añadir los suyos.
 */
internal object DefaultSites {

    private data class Default(val name: String, val url: String, val provider: SearchProvider? = null)

    private val defaults = listOf(
        Default("YouTube", "https://m.youtube.com", SearchProvider.YOUTUBE),
        Default("Vimeo", "https://vimeo.com"),
        Default("Dailymotion", "https://www.dailymotion.com"),
        Default("SoundCloud", "https://soundcloud.com", SearchProvider.SOUNDCLOUD),
        Default("Bandcamp", "https://bandcamp.com"),
        Default("Internet Archive", "https://archive.org"),
        Default("Twitch", "https://m.twitch.tv"),
        Default("Bilibili", "https://m.bilibili.com", SearchProvider.BILIBILI),
        Default("Niconico", "https://www.nicovideo.jp", SearchProvider.NICONICO),
        Default("PeerTube", "https://joinpeertube.org"),
        Default("TED", "https://www.ted.com"),
        Default("Reddit", "https://www.reddit.com"),
    )

    val entities: List<SiteEntity> = defaults.mapIndexed { index, site ->
        SiteEntity(
            name = site.name,
            url = site.url,
            searchProvider = site.provider?.name,
            isCustom = false,
            enabled = true,
            position = index,
        )
    }
}
