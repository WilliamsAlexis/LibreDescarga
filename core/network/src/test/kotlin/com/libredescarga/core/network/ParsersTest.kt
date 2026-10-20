package com.libredescarga.core.network

import com.libredescarga.core.model.UpdateException
import com.libredescarga.core.model.UpdateFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ParsersTest {

    private val hash = "a".repeat(64)
    private val arm64Url = "https://github.com/LibreDescarga/LibreDescarga/releases/download/v1.0.1/arm64.apk"

    private fun manifest(extra: String = "", apks: String = """[{"abi":"arm64-v8a","url":"$arm64Url","sha256":"$hash"}]""") =
        """{"versionCode":2,"versionName":"1.0.1","minSdk":24,"apks":$apks $extra}"""

    private fun failure(text: String): UpdateFailure? = try {
        UpdateManifestParser.parse(text)
        null
    } catch (error: UpdateException) {
        error.failure
    }

    @Test
    fun `lee un manifiesto completo`() {
        val update = UpdateManifestParser.parse(
            """
            {"versionCode":7,"versionName":"1.2.0","minSdk":26,
             "notes":{"es":"Mejoras","en":"Improvements"},
             "apks":[{"abi":"arm64-v8a","url":"$arm64Url","sha256":"${hash.uppercase()}"}],
             "apkUrl":"https://github.com/o/r/releases/download/v1.2.0/universal.apk","sha256":"${"b".repeat(64)}"}
            """.trimIndent(),
            language = "en",
        )
        assertEquals(7L, update.versionCode)
        assertEquals("1.2.0", update.versionName)
        assertEquals(26, update.minSdk)
        assertEquals("Improvements", update.notes)
        assertEquals(2, update.assets.size)
        assertEquals(hash, update.assets.first().sha256) // Se guarda en minúscula.
        assertNull(update.assets.last().abi)
    }

    @Test
    fun `las notas pueden ser un texto simple o caer al idioma disponible`() {
        assertEquals("Texto", UpdateManifestParser.parse(manifest(""","notes":"Texto""""), "es").notes)
        assertEquals("Mejoras", UpdateManifestParser.parse(manifest(""","notes":{"es":"Mejoras"}"""), "fr").notes)
        assertEquals("", UpdateManifestParser.parse(manifest()).notes)
    }

    @Test
    fun `un manifiesto que no es JSON o está incompleto se rechaza`() {
        assertEquals(UpdateFailure.INVALID_MANIFEST, failure("esto no es json"))
        assertEquals(UpdateFailure.INVALID_MANIFEST, failure("{}"))
        assertEquals(UpdateFailure.INVALID_MANIFEST, failure("""{"versionCode":0,"versionName":"1","apks":[]}"""))
        assertEquals(UpdateFailure.INVALID_MANIFEST, failure("""{"versionCode":1,"versionName":" ","apks":[]}"""))
    }

    @Test
    fun `se descartan los APK con dirección o resumen no válidos`() {
        // Dirección fuera de GitHub, http, y resumen corto: ninguno sirve.
        val bad = """[
            {"url":"https://evil.example.com/app.apk","sha256":"$hash"},
            {"url":"http://github.com/o/r/releases/download/v1/app.apk","sha256":"$hash"},
            {"url":"$arm64Url","sha256":"abc123"}
        ]"""
        assertEquals(UpdateFailure.INVALID_MANIFEST, failure(manifest(apks = bad)))

        // Si queda al menos uno bueno, se usa solo ese.
        val mixed = """[
            {"abi":"x86","url":"https://evil.example.com/app.apk","sha256":"$hash"},
            {"abi":"arm64-v8a","url":"$arm64Url","sha256":"$hash"}
        ]"""
        val update = UpdateManifestParser.parse(manifest(apks = mixed))
        assertEquals(listOf("arm64-v8a"), update.assets.map { it.abi })
    }

    @Test
    fun `el manifiesto simple con apkUrl y sha256 también sirve`() {
        val update = UpdateManifestParser.parse(
            """{"versionCode":3,"versionName":"1.0.2","apkUrl":"$arm64Url","sha256":"$hash"}""",
        )
        assertEquals(1, update.assets.size)
        assertNull(update.assets.single().abi)
        assertEquals(24, update.minSdk)
    }

    @Test
    fun `la lista negra descarta lo que no son dominios`() {
        val list = BlocklistParser.parse(
            """{"version":4,"domains":["Ejemplo.com","https://www.otro.org/ruta","javascript:alert(1)","","sinpunto","ejemplo.com"]}""",
        )
        assertEquals(4, list.version)
        assertEquals(listOf("ejemplo.com", "otro.org"), list.domains)
    }

    @Test
    fun `una lista negra dañada se rechaza`() {
        try {
            BlocklistParser.parse("[1,2,3]")
            fail("Debía fallar")
        } catch (error: UpdateException) {
            assertTrue(error.failure == UpdateFailure.INVALID_MANIFEST)
        }
    }
}
