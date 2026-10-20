package com.libredescarga.feature.player

import com.libredescarga.feature.player.video.GesturePolicy
import com.libredescarga.feature.player.video.VerticalGestureTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GesturePolicyTest {

    @Test
    fun `la mitad izquierda cambia el brillo y la derecha el volumen`() {
        assertEquals(VerticalGestureTarget.BRIGHTNESS, GesturePolicy.verticalTarget(100f, 1000f))
        assertEquals(VerticalGestureTarget.VOLUME, GesturePolicy.verticalTarget(900f, 1000f))
    }

    @Test
    fun `deslizar hacia arriba sube el nivel y se queda entre 0 y 1`() {
        assertEquals(0.75f, GesturePolicy.levelAfterDrag(0.5f, dragDeltaY = -250f, height = 1000f), 0.001f)
        assertEquals(0.25f, GesturePolicy.levelAfterDrag(0.5f, dragDeltaY = 250f, height = 1000f), 0.001f)
        assertEquals(1f, GesturePolicy.levelAfterDrag(0.9f, dragDeltaY = -900f, height = 1000f), 0.001f)
        assertEquals(0f, GesturePolicy.levelAfterDrag(0.1f, dragDeltaY = 900f, height = 1000f), 0.001f)
        assertEquals(0.4f, GesturePolicy.levelAfterDrag(0.4f, dragDeltaY = 10f, height = 0f), 0.001f)
    }

    @Test
    fun `doble toque retrocede, avanza o no hace nada en el centro`() {
        assertEquals(-10_000L, GesturePolicy.doubleTapSeekMs(100f, 900f))
        assertEquals(10_000L, GesturePolicy.doubleTapSeekMs(800f, 900f))
        assertEquals(0L, GesturePolicy.doubleTapSeekMs(450f, 900f))
        assertEquals(0L, GesturePolicy.doubleTapSeekMs(450f, 0f))
    }

    @Test
    fun `el pellizco llena la pantalla pasado el umbral`() {
        assertTrue(GesturePolicy.zoomFilled(1.4f))
        assertFalse(GesturePolicy.zoomFilled(0.8f))
    }

    @Test
    fun `la ventana flotante acota proporciones extremas`() {
        assertEquals(16 to 9, GesturePolicy.pipAspectRatio(null, null))
        assertEquals(1920 to 1080, GesturePolicy.pipAspectRatio(1920, 1080))
        assertEquals(239 to 100, GesturePolicy.pipAspectRatio(4000, 1000))
        assertEquals(100 to 239, GesturePolicy.pipAspectRatio(1000, 4000))
    }
}
