package com.libredescarga.core.vault

import android.content.Context
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.channels.SeekableByteChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cifrado AES-256-GCM por segmentos (Tink, AES256_GCM_HKDF_4KB). Permite leer cualquier
 * punto del archivo sin descifrarlo entero, así que los videos se pueden adelantar y atrasar.
 *
 * La clave se guarda envuelta con una clave del Android Keystore: no sale del dispositivo
 * y es independiente del PIN (por eso cambiar el PIN no obliga a volver a cifrar los archivos).
 */
@Singleton
class VaultCrypto @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val streamingAead: StreamingAead by lazy {
        StreamingAeadConfig.register()
        val handle = AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM_HKDF_4KB"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
        handle.getPrimitive(RegistryConfiguration.get(), StreamingAead::class.java)
    }

    /** Cifra [source] en [target]. El id del archivo va como dato asociado: un archivo no se puede cambiar por otro. */
    fun encrypt(source: InputStream, target: File, id: String) {
        streamingAead.newEncryptingStream(target.outputStream().buffered(), associatedData(id)).use { out ->
            source.copyTo(out, BUFFER_SIZE)
        }
    }

    /** Copia sin cifrar (cuando el usuario desactivó el cifrado). */
    fun copyPlain(source: InputStream, target: File) {
        target.outputStream().buffered().use { out -> source.copyTo(out, BUFFER_SIZE) }
    }

    /** Descifra un archivo completo hacia [target] (por ejemplo, al sacarlo de la carpeta privada). */
    fun decryptTo(encrypted: File, target: File, id: String) {
        val channel = openDecryptingChannel(encrypted, id)
        channel.use {
            java.nio.channels.Channels.newInputStream(it).use { input ->
                target.outputStream().buffered().use { out -> input.copyTo(out, BUFFER_SIZE) }
            }
        }
    }

    /** Canal de lectura con acceso aleatorio sobre el contenido descifrado. */
    fun openDecryptingChannel(encrypted: File, id: String): SeekableByteChannel =
        streamingAead.newSeekableDecryptingChannel(RandomAccessFile(encrypted, "r").channel, associatedData(id))

    fun openPlainChannel(file: File): SeekableByteChannel = RandomAccessFile(file, "r").channel

    private fun associatedData(id: String): ByteArray = id.toByteArray(Charsets.UTF_8)

    private companion object {
        const val KEYSET_NAME = "vault_keyset"
        const val KEYSET_PREFS = "vault_keyset_prefs"
        const val MASTER_KEY_URI = "android-keystore://libredescarga_vault_master_key"
        const val BUFFER_SIZE = 64 * 1024
    }
}
