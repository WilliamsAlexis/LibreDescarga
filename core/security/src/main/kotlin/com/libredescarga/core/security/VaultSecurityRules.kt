package com.libredescarga.core.security

import java.text.Normalizer
import java.util.Locale
import kotlin.math.min

/** Cuántos intentos fallidos se permiten y cuánto espera el usuario después. */
object LockoutPolicy {

    const val FREE_ATTEMPTS = 5
    private const val BASE_DELAY_MS = 30_000L
    private const val MAX_DELAY_MS = 15 * 60_000L

    /** Espera tras [failures] intentos fallidos seguidos: 0 hasta el 4.º; 30 s, 1 min, 2 min... hasta 15 min. */
    fun delayMs(failures: Int): Long {
        if (failures < FREE_ATTEMPTS) return 0
        val doublings = (failures - FREE_ATTEMPTS).coerceAtMost(10)
        return min(BASE_DELAY_MS shl doublings, MAX_DELAY_MS)
    }

    /** Intentos que quedan antes de la próxima espera. */
    fun attemptsBeforeLock(failures: Int): Int = (FREE_ATTEMPTS - failures).coerceAtLeast(0)
}

/** Normaliza la respuesta de seguridad para que "Pérez ", "perez" y "PEREZ" cuenten igual. */
object SecurityAnswer {
    fun normalize(answer: String): String =
        Normalizer.normalize(answer.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")

    const val MIN_LENGTH = 3
    fun isAcceptable(answer: String): Boolean = normalize(answer).length >= MIN_LENGTH
}

/** Reglas para elegir un PIN o un patrón. */
object CredentialRules {

    const val PIN_MIN = 4
    const val PIN_MAX = 12
    const val PATTERN_MIN_NODES = 4
    const val PATTERN_NODES = 9

    fun isValidPin(pin: String): Boolean = pin.length in PIN_MIN..PIN_MAX && pin.all { it in '0'..'9' }

    /** Los nodos van numerados del 0 al 8 (de izquierda a derecha y de arriba abajo), sin repetir. */
    fun isValidPattern(nodes: List<Int>): Boolean =
        nodes.size >= PATTERN_MIN_NODES && nodes.all { it in 0 until PATTERN_NODES } && nodes.toSet().size == nodes.size

    /** Un PIN evidente (0000, 1234, 1111...) se permite pero se avisa. */
    fun isWeakPin(pin: String): Boolean {
        if (pin.toSet().size == 1) return true
        val digits = pin.map { it - '0' }
        val ascending = digits.zipWithNext().all { (a, b) -> b - a == 1 }
        val descending = digits.zipWithNext().all { (a, b) -> a - b == 1 }
        return ascending || descending
    }

    fun patternToText(nodes: List<Int>): String = nodes.joinToString("")
}
