package com.libredescarga.core.security

/** Comprueba si un dominio (o cualquiera de sus subdominios) está en una lista. */
object DomainMatcher {

    fun matches(host: String, domains: Collection<String>): String? {
        val normalized = host.lowercase().trimEnd('.').removePrefix("www.")
        return domains.firstOrNull { raw ->
            val domain = raw.lowercase().trim().trimEnd('.').removePrefix("www.")
            domain.isNotEmpty() && (normalized == domain || normalized.endsWith(".$domain"))
        }
    }
}
