package com.libredescarga.core.domain.usecase

import java.util.Locale

/**
 * Clave para reconocer que dos enlaces apuntan al mismo contenido: en YouTube es el ID del video (youtu.be, watch, shorts,
 * embed, music.youtube.com...); en los demás sitios, el enlace sin parámetros de rastreo, sin "www." y sin "/" final.
 * Se usa para reutilizar un análisis reciente en lugar de repetirlo.
 */
object AnalysisCacheKey {

    private val YOUTUBE_HOSTS = listOf("youtube.com", "youtu.be", "youtube-nocookie.com")
    private val YOUTUBE_ID_PATHS = setOf("shorts", "embed", "live", "v")
    private val TRACKING_PARAMS = setOf(
        "si", "feature", "fbclid", "igshid", "igsh", "gclid", "mc_cid", "mc_eid", "ref", "ref_src", "ref_url", "share_id",
        "is_from_webapp", "sender_device", "web_id", "pp",
    )

    fun of(url: String): String {
        val withoutFragment = url.substringBefore('#')
        val afterScheme = withoutFragment.substringAfter("://", withoutFragment)
        val host = afterScheme.substringBefore('/').substringBefore('?').lowercase(Locale.ROOT).removePrefix("www.")
        val pathAndQuery = afterScheme.removePrefix(afterScheme.substringBefore('/').substringBefore('?'))
        val path = pathAndQuery.substringBefore('?').trimEnd('/')
        val params = pathAndQuery.substringAfter('?', "").split('&').filter { it.isNotBlank() }
            .associate { it.substringBefore('=') to it.substringAfter('=', "") }

        if (YOUTUBE_HOSTS.any { host == it || host.endsWith(".$it") }) {
            val segments = path.split('/').filter { it.isNotEmpty() }
            val id = when {
                host == "youtu.be" -> segments.firstOrNull()
                segments.firstOrNull() in YOUTUBE_ID_PATHS -> segments.getOrNull(1)
                else -> params["v"]
            }
            if (!id.isNullOrBlank()) return "youtube:$id"
        }

        val query = params.filterKeys { it !in TRACKING_PARAMS && !it.startsWith("utm_") }
            .toSortedMap().entries.joinToString("&") { "${it.key}=${it.value}" }
        return if (query.isEmpty()) "$host$path" else "$host$path?$query"
    }
}
