package com.libredescarga.feature.player.video

/** Qué hace un gesto vertical según el lado de la pantalla donde empieza. */
enum class VerticalGestureTarget { BRIGHTNESS, VOLUME }

/** Lógica pura de los gestos del reproductor de video (se prueba sin Android). */
object GesturePolicy {

    /** Mitad izquierda: brillo. Mitad derecha: volumen. */
    fun verticalTarget(startX: Float, width: Float): VerticalGestureTarget =
        if (startX < width / 2f) VerticalGestureTarget.BRIGHTNESS else VerticalGestureTarget.VOLUME

    /** Deslizar toda la altura de la pantalla cambia el nivel completo; hacia arriba sube. */
    fun levelAfterDrag(current: Float, dragDeltaY: Float, height: Float): Float {
        if (height <= 0f) return current
        return (current - dragDeltaY / height).coerceIn(0f, 1f)
    }

    /** Doble toque: tercio izquierdo retrocede, tercio derecho avanza, el centro no hace nada. */
    fun doubleTapSeekMs(tapX: Float, width: Float, stepMs: Long = SEEK_STEP_MS): Long = when {
        width <= 0f -> 0
        tapX < width / 3f -> -stepMs
        tapX > width * 2f / 3f -> stepMs
        else -> 0
    }

    /** Pellizcar: se acumula el zoom; por encima del umbral se llena la pantalla, por debajo se ajusta. */
    fun zoomFilled(accumulatedScale: Float): Boolean = accumulatedScale > ZOOM_THRESHOLD

    /** Proporción para la ventana flotante (Picture-in-Picture), acotada a lo que admite Android. */
    fun pipAspectRatio(width: Int?, height: Int?): Pair<Int, Int> {
        val w = width?.takeIf { it > 0 } ?: return 16 to 9
        val h = height?.takeIf { it > 0 } ?: return 16 to 9
        val ratio = w.toFloat() / h
        return when {
            ratio > MAX_PIP_RATIO -> 239 to 100
            ratio < 1f / MAX_PIP_RATIO -> 100 to 239
            else -> w to h
        }
    }

    const val SEEK_STEP_MS = 10_000L
    const val ZOOM_THRESHOLD = 1.1f
    private const val MAX_PIP_RATIO = 2.39f
}
