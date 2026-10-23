package com.libredescarga.core.domain.repository

import com.libredescarga.core.model.LinkPreview

/** Encuentra la imagen principal de una página web (por ejemplo, la foto de un pin de Pinterest). */
interface PageImageResolver {

    /**
     * Abre la página y devuelve la dirección de su imagen principal, o null si no tiene
     * o no se pudo leer (sin internet, sitio que lo bloquea...).
     */
    suspend fun mainImageOf(pageUrl: String): String?

    /**
     * Como [mainImageOf], pero solo si la página es una imagen (sin video): devuelve null si la página declara un video,
     * para que lo analice el motor de video. Permite saltarse yt-dlp en páginas de imágenes.
     */
    suspend fun imageOnlyOf(pageUrl: String): String? = null

    /** Título, autor y miniatura de un enlace de YouTube o TikTok (oEmbed público); null si no hay o falla. */
    suspend fun previewOf(url: String): LinkPreview? = null
}
