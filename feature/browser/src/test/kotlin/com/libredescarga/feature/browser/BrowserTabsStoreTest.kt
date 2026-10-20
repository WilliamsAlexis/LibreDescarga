package com.libredescarga.feature.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserTabsStoreTest {

    private val store = BrowserTabsStore()
    private val state get() = store.state.value

    @Test
    fun `siempre hay al menos una pestaña`() {
        assertTrue(state.tabs.isEmpty())
        store.ensureTab()
        assertEquals(1, state.tabs.size)
        store.ensureTab()
        assertEquals(1, state.tabs.size)
        assertNotNull(state.selected)
    }

    @Test
    fun `abrir una dirección ya abierta selecciona la pestaña existente`() {
        val first = store.open("https://example.com/a")
        store.open("https://example.com/b")
        assertEquals(2, state.tabs.size)

        val again = store.open("https://example.com/a")
        assertEquals(first, again)
        assertEquals(2, state.tabs.size)
        assertEquals(first, state.selectedId)
    }

    @Test
    fun `las pestañas en blanco y de incógnito siempre son nuevas`() {
        store.open("")
        store.open("")
        store.open("https://example.com/a", incognito = true)
        store.open("https://example.com/a", incognito = true)
        assertEquals(4, state.tabs.size)
    }

    @Test
    fun `cerrar la seleccionada elige la vecina`() {
        val a = store.open("https://example.com/a")
        val b = store.open("https://example.com/b")
        val c = store.open("https://example.com/c")

        store.close(c)
        assertEquals(b, state.selectedId)

        store.select(a)
        store.close(a)
        assertEquals(b, state.selectedId)
        assertEquals(listOf(b), state.tabs.map { it.id })
    }

    @Test
    fun `cerrar la última deja una pestaña en blanco`() {
        val only = store.open("https://example.com/a")
        store.close(only)
        assertEquals(1, state.tabs.size)
        assertEquals("", state.selected?.url)
        assertFalse(state.tabs.first().id == only)
    }

    @Test
    fun `salir del navegador cierra solo las pestañas de incógnito`() {
        val normal = store.open("https://example.com/a")
        store.open("https://example.com/secreto", incognito = true)
        assertTrue(store.hasIncognito)

        store.closeIncognito()

        assertFalse(store.hasIncognito)
        assertEquals(listOf(normal), state.tabs.map { it.id })
        assertEquals(normal, state.selectedId)
    }

    @Test
    fun `actualizar una pestaña cambia solo esa`() {
        val a = store.open("https://example.com/a")
        val b = store.open("https://example.com/b")
        store.update(a) { it.copy(title = "A") }
        assertEquals("A", state.tabs.first { it.id == a }.title)
        assertEquals("", state.tabs.first { it.id == b }.title)
    }
}
