package com.libredescarga.core.data.repository

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AnalysisDiskStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private var now = 1_000_000L
    private val json = buildJsonObject { put("id", "abc"); put("title", "Video") }

    private fun store(ttl: Long = 60_000, max: Int = 100) = AnalysisDiskStore(folder.root, ttl, max) { now }

    @Test
    fun `guarda y lee el analisis aunque sea otra instancia (la app se cerro)`() {
        store().write("youtube:abc", "https://youtu.be/abc", json)

        val saved = store().read("youtube:abc")!!
        assertEquals("https://youtu.be/abc", saved.url)
        assertEquals(json, saved.json)
    }

    @Test
    fun `un analisis vencido no se usa y se borra`() {
        store(ttl = 60_000).write("k", "https://a/b", json)
        now += 61_000

        assertNull(store(ttl = 60_000).read("k"))
        assertEquals(0, folder.root.listFiles { f -> f.extension == "json" }!!.size)
    }

    @Test
    fun `un archivo danado se descarta`() {
        val s = store()
        s.write("k", "https://a/b", json)
        folder.root.listFiles { f -> f.extension == "json" }!!.single().writeText("{roto")

        assertNull(s.read("k"))
    }

    @Test
    fun `una clave sin guardar no existe`() {
        assertNull(store().read("nada"))
    }
}
