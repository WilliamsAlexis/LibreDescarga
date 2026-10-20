package com.libredescarga.core.domain.repository

/** Encuentra la imagen principal de una página web (por ejemplo, la foto de un pin de Pinterest). */
interface PageImageResolver {

    /**
     * Abre la página y devuelve la dirección de su imagen principal, o null si no tiene
     * o no se pudo leer (sin internet, sitio que lo bloquea...).
     */
    suspend fun mainImageOf(pageUrl: String): String?
}
