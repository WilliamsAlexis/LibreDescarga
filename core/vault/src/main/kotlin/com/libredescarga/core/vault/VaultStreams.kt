package com.libredescarga.core.vault

import android.content.Context
import com.libredescarga.core.database.dao.VaultItemDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.channels.SeekableByteChannel
import javax.inject.Inject
import javax.inject.Singleton

/** Dónde viven los archivos de la carpeta privada: almacenamiento interno, invisible para la galería y otras apps. */
@Singleton
class VaultFiles @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val directory: File get() = File(context.filesDir, "vault").apply { mkdirs() }

    fun fileFor(id: String): File = File(directory, "$id.bin")

    /** Carpeta temporal para sacar un archivo; se vacía en cada uso. */
    fun tempFile(name: String): File = File(File(context.cacheDir, "vault-tmp").apply { mkdirs() }, name)

    fun clearTemp() {
        File(context.cacheDir, "vault-tmp").deleteRecursively()
    }
}

/** Contenido de un archivo listo para leer (la carpeta se lee sin descifrarlo entero). */
class VaultSource(val channel: SeekableByteChannel, val sizeBytes: Long, val mimeType: String?)

/**
 * Abre archivos de la carpeta privada para el reproductor.
 * La dirección es `vault://<id>`; el reproductor la pide al servicio de reproducción.
 */
@Singleton
class VaultStreams @Inject constructor(
    private val dao: VaultItemDao,
    private val files: VaultFiles,
    private val crypto: VaultCrypto,
) {
    /** Bloquea el hilo que llama; úsalo desde el hilo de carga del reproductor. */
    fun open(id: String): VaultSource? {
        val item = kotlinx.coroutines.runBlocking { dao.get(id) } ?: return null
        val file = files.fileFor(id).takeIf { it.exists() } ?: return null
        val channel = if (item.encrypted) crypto.openDecryptingChannel(file, id) else crypto.openPlainChannel(file)
        return VaultSource(channel, channel.size(), item.mimeType)
    }
}
