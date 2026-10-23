package com.libredescarga.core.model

/**
 * Medición de tiempos del análisis de enlaces (solo diagnóstico, no cambia el comportamiento).
 *
 * La app conecta [sink] a logcat con la etiqueta "AnalisisPerf" (ver LibreDescargaApp). Mientras [sink] sea null no se
 * escribe nada y medir cuesta una lectura del reloj.
 *
 * Formato de cada línea: "<fase> | <ms> ms | <detalle>"  (o "<fase> | marca | <detalle>" para eventos sin duración).
 * Los detalles solo llevan el dominio (por ejemplo "tiktok.com"), nunca el enlace completo.
 */
object AnalisisPerf {

    @Volatile
    var sink: ((String) -> Unit)? = null

    /** Ejecuta [block] y registra cuánto tardó, aunque lance una excepción. Admite funciones suspend dentro. */
    inline fun <T> measure(phase: String, detail: String? = null, block: () -> T): T {
        val start = System.nanoTime()
        try {
            return block()
        } finally {
            duration(phase, (System.nanoTime() - start) / 1_000_000, detail)
        }
    }

    /** Registra una fase cuya duración ya se conoce. */
    fun duration(phase: String, millis: Long, detail: String? = null) {
        sink?.invoke(line(phase, "$millis ms", detail))
    }

    /** Registra un evento puntual (por ejemplo, cuándo llega el enlace); sirve para medir huecos entre marcas. */
    fun mark(phase: String, detail: String? = null) {
        sink?.invoke(line(phase, "marca", detail))
    }

    /** Dominio de un enlace para los registros, sin ruta ni parámetros. */
    fun hostOf(url: String): String =
        url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')

    private fun line(phase: String, value: String, detail: String?): String =
        if (detail.isNullOrBlank()) "$phase | $value" else "$phase | $value | $detail"
}
