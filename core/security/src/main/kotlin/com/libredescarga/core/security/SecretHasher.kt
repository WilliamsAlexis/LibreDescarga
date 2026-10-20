package com.libredescarga.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Resultado de proteger un PIN, un patrón o una respuesta: nunca se guarda el original. */
data class HashedSecret(val saltHex: String, val hashHex: String, val iterations: Int)

/**
 * Protege PIN, patrones y respuestas de seguridad con PBKDF2-HMAC-SHA256 y una sal aleatoria.
 *
 * PBKDF2 se calcula a mano sobre HMAC-SHA256 porque la versión de la plataforma con SHA-256
 * no existe en Android 7 (minSdk 24).
 */
object SecretHasher {

    const val DEFAULT_ITERATIONS = 60_000
    private const val SALT_BYTES = 16
    private const val KEY_BYTES = 32
    private val random = SecureRandom()

    fun hash(secret: String, iterations: Int = DEFAULT_ITERATIONS): HashedSecret {
        require(secret.isNotEmpty()) { "El secreto no puede estar vacío" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return HashedSecret(Hex.encode(salt), Hex.encode(pbkdf2(secret, salt, iterations)), iterations)
    }

    fun verify(secret: String, stored: HashedSecret): Boolean {
        if (secret.isEmpty()) return false // HMAC no admite claves vacías; un secreto vacío nunca es válido.
        val salt = runCatching { Hex.decode(stored.saltHex) }.getOrNull() ?: return false
        val expected = runCatching { Hex.decode(stored.hashHex) }.getOrNull() ?: return false
        val actual = pbkdf2(secret, salt, stored.iterations)
        // Comparación en tiempo constante: no revela cuántos caracteres coinciden.
        return MessageDigest.isEqual(expected, actual)
    }

    internal fun pbkdf2(secret: String, salt: ByteArray, iterations: Int): ByteArray {
        require(iterations > 0) { "Las iteraciones deben ser positivas" }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))

        // Un solo bloque de 32 bytes: coincide con la longitud de salida de HMAC-SHA256.
        mac.update(salt)
        mac.update(byteArrayOf(0, 0, 0, 1))
        var u = mac.doFinal()
        val result = u.copyOf()
        repeat(iterations - 1) {
            u = mac.doFinal(u)
            for (i in result.indices) result[i] = (result[i].toInt() xor u[i].toInt()).toByte()
        }
        return result.copyOf(KEY_BYTES)
    }
}

object Hex {
    private const val DIGITS = "0123456789abcdef"

    fun encode(bytes: ByteArray): String = buildString(bytes.size * 2) {
        bytes.forEach { byte ->
            append(DIGITS[(byte.toInt() shr 4) and 0xF])
            append(DIGITS[byte.toInt() and 0xF])
        }
    }

    fun decode(text: String): ByteArray {
        require(text.length % 2 == 0) { "Longitud impar" }
        return ByteArray(text.length / 2) { index ->
            val high = DIGITS.indexOf(text[index * 2].lowercaseChar())
            val low = DIGITS.indexOf(text[index * 2 + 1].lowercaseChar())
            require(high >= 0 && low >= 0) { "Carácter no hexadecimal" }
            ((high shl 4) or low).toByte()
        }
    }
}
