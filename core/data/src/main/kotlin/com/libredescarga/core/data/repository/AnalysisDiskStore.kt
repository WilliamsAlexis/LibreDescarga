package com.libredescarga.core.data.repository

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.File
import java.security.MessageDigest

/**
 * Análisis guardados en disco (en la caché de la app: "Limpiar caché" en Ajustes los borra, igual que Android si le falta
 * espacio). Se guarda el JSON del análisis, no el resultado ya procesado: al leerlo se vuelve a procesar (~1 ms), así un
 * cambio en el procesamiento nunca deja resultados viejos con otro formato.
 *
 * Solo se usa en el teléfono y solo guarda lo que el usuario ya analizó; nada sale del dispositivo.
 */
internal class AnalysisDiskStore(
    private val dir: File,
    private val ttlMs: Long,
    private val maxEntries: Int = 100,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    class Saved(val url: String, val json: JsonObject)

    fun read(key: String): Saved? {
        val file = fileFor(key)
        if (!file.isFile) return null
        return try {
            val root = Json.parseToJsonElement(file.readText()).jsonObject
            val savedAt = root["savedAt"]?.jsonPrimitive?.long ?: return null
            if (clock() - savedAt > ttlMs) {
                file.delete()
                return null
            }
            Saved(
                url = root["url"]?.jsonPrimitive?.content ?: return null,
                json = root["data"]?.jsonObject ?: return null,
            )
        } catch (_: Exception) {
            file.delete() // Archivo dañado: se descarta.
            null
        }
    }

    fun write(key: String, url: String, json: JsonObject) {
        try {
            dir.mkdirs()
            val content = buildJsonObject {
                put("savedAt", clock())
                put("url", url)
                put("data", json)
            }
            // Se escribe a un temporal y se renombra: nunca queda un archivo a medias.
            val target = fileFor(key)
            val partial = File(dir, "${target.name}.tmp")
            partial.writeText(content.toString())
            partial.renameTo(target)
            prune()
        } catch (_: Exception) {
            // Sin espacio o sin permiso: la caché es opcional.
        }
    }

    /** Borra los vencidos y, si sobran, los más antiguos. */
    private fun prune() {
        val files = dir.listFiles { file -> file.extension == "json" }.orEmpty().sortedByDescending { it.lastModified() }
        files.forEachIndexed { index, file ->
            if (index >= maxEntries || clock() - file.lastModified() > ttlMs) file.delete()
        }
    }

    private fun fileFor(key: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$digest.json")
    }
}
