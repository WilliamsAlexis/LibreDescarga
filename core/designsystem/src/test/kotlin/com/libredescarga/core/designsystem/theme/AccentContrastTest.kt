package com.libredescarga.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.libredescarga.core.model.AccentColor
import org.junit.Assert.assertTrue
import org.junit.Test

/** Comprueba que todos los colores de acento se leen bien (contraste mínimo de accesibilidad) en modo claro y oscuro. */
class AccentContrastTest {

    private fun contrast(a: Color, b: Color): Double {
        val l1 = a.luminance().toDouble()
        val l2 = b.luminance().toDouble()
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    @Test
    fun `el texto sobre el color de acento y su contenedor tiene contraste AA`() {
        for (accent in AccentColor.entries) {
            for (dark in listOf(false, true)) {
                val scheme = libreColorScheme(accent, dark)
                val modo = if (dark) "oscuro" else "claro"
                val botones = contrast(scheme.onPrimary, scheme.primary)
                val contenedores = contrast(scheme.onPrimaryContainer, scheme.primaryContainer)
                assertTrue("$accent ($modo): texto sobre el acento $botones", botones >= 4.5)
                assertTrue("$accent ($modo): texto sobre el contenedor $contenedores", contenedores >= 4.5)
            }
        }
    }

    @Test
    fun `el color de acento se distingue del fondo`() {
        for (accent in AccentColor.entries) {
            for (dark in listOf(false, true)) {
                val scheme = libreColorScheme(accent, dark)
                val ratio = contrast(scheme.primary, scheme.surface)
                assertTrue("$accent (oscuro=$dark): acento contra el fondo $ratio", ratio >= 3.0)
            }
        }
    }

    @Test
    fun `cada acento tiene un color de muestra distinto`() {
        for (dark in listOf(false, true)) {
            val muestras = AccentColor.entries.map { it.swatch(dark) }
            assertTrue("Hay colores de muestra repetidos", muestras.toSet().size == muestras.size)
        }
    }
}
