package com.libredescarga.core.config

/**
 * Configuración central de LibreDescarga.
 *
 * Todas las URL y los datos de contacto están fijos en el código: nunca se
 * descargan de internet, para que nadie pueda reemplazarlos por otros.
 */
object AppConfig {

    // ---------------------------------------------------------------------
    // Donaciones voluntarias (no desbloquean nada)
    // ---------------------------------------------------------------------

    /** Enlace que se muestra y se abre en el navegador externo o en la app de PayPal. */
    const val DONATION_URL = "https://paypal.me/alexis031298"

    /** Descargas completadas tras las que se muestra (una sola vez) el mensaje de apoyo. */
    const val DONATION_PROMPT_THRESHOLD = 20

    // ---------------------------------------------------------------------
    // GitHub Releases (actualizaciones de la app y lista negra)
    // ---------------------------------------------------------------------

    const val GITHUB_OWNER = "WilliamsAlexis"
    const val GITHUB_REPO = "LibreDescarga"
    const val GITHUB_REPO_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO"

    /** JSON público con la versión, la URL del APK, el SHA-256 y las notas. */
    const val UPDATE_MANIFEST_URL = "$GITHUB_REPO_URL/releases/latest/download/update.json"

    /** Lista negra de dominios para atender solicitudes de retiro (DMCA o equivalentes). */
    const val BLOCKLIST_URL =
        "https://raw.githubusercontent.com/$GITHUB_OWNER/$GITHUB_REPO/main/blocklist.json"

    // ---------------------------------------------------------------------
    // Aviso legal
    // ---------------------------------------------------------------------

    /** Correo para reclamos de derechos de autor; se muestra en los términos y en el aviso legal. */
    const val LEGAL_CONTACT_EMAIL = "w_alexis0312@hotmail.com"

    /** Súbelo cuando cambien los términos: se volverá a pedir la aceptación. */
    const val TERMS_VERSION = 1

    // ---------------------------------------------------------------------
    // Almacenamiento
    // ---------------------------------------------------------------------

    /** Subcarpeta dentro de Movies/ y Music/ donde se guardan las descargas. */
    const val DOWNLOAD_FOLDER_NAME = "LibreDescarga"
}
