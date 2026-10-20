package com.libredescarga.core.model

/** Errores conocidos al analizar o descargar. La interfaz muestra un mensaje para cada uno. */
sealed class MediaError(message: String? = null) : Exception(message) {
    /** El texto no es un enlace http/https válido. */
    class InvalidUrl : MediaError()

    /** Esquema prohibido (file://, content://, javascript:, ...). */
    class ForbiddenScheme : MediaError()

    /** Dominio retirado por una solicitud de derechos de autor. */
    class BlockedSite(val domain: String) : MediaError(domain)

    /** Contenido protegido con DRM: nunca se intenta descargar. */
    class DrmProtected : MediaError()

    /** Transmisión en directo: no se puede descargar. */
    class LiveStream : MediaError()

    /** El sitio no es compatible con el motor de descarga. */
    class Unsupported : MediaError()

    /** El contenido es privado o necesita iniciar sesión en el sitio. */
    class NotPublic : MediaError()

    /** Tiene restricción de edad: el sitio exige iniciar sesión para verlo y la app no usa cuentas. */
    class AgeRestricted : MediaError()

    /** El sitio pidió comprobar que no se es un robot (bloqueo temporal, suele pasar con la red o la hora). */
    class BotCheck : MediaError()

    /** El contenido ya no existe o no está disponible en tu región. */
    class Unavailable : MediaError()

    /** Sin conexión o el sitio no respondió. */
    class Network : MediaError()

    /** No hay espacio suficiente en el almacenamiento. */
    class InsufficientStorage : MediaError()

    class Unknown(val details: String?) : MediaError(details)
}
