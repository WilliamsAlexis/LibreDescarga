@file:OptIn(UnstableApi::class)

package com.libredescarga.core.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import com.libredescarga.core.model.VaultItem
import com.libredescarga.core.vault.VaultSource
import com.libredescarga.core.vault.VaultStreams
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.min

/**
 * Lee `vault://<id>` descifrando al vuelo: el video nunca se escribe sin cifrar en el disco
 * y se puede adelantar y atrasar sin descifrar todo el archivo.
 */
internal class VaultDataSource(private val streams: VaultStreams) : BaseDataSource(/* isNetwork = */ false) {

    private var source: VaultSource? = null
    private var currentUri: Uri? = null
    private var bytesRemaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        currentUri = dataSpec.uri
        transferInitializing(dataSpec)

        val id = VaultItem.idOf(dataSpec.uri.toString()) ?: throw IOException("Dirección de la carpeta privada no válida")
        val opened = streams.open(id) ?: throw FileNotFoundException("Archivo no encontrado en la carpeta privada")
        source = opened

        if (dataSpec.position > opened.sizeBytes) {
            close()
            throw IOException("Posición fuera del archivo")
        }
        opened.channel.position(dataSpec.position)
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            min(dataSpec.length, opened.sizeBytes - dataSpec.position)
        } else {
            opened.sizeBytes - dataSpec.position
        }
        this.opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val channel = source?.channel ?: return C.RESULT_END_OF_INPUT

        val toRead = min(bytesRemaining, length.toLong()).toInt()
        val read = channel.read(ByteBuffer.wrap(buffer, offset, toRead))
        if (read == -1) return C.RESULT_END_OF_INPUT
        bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        try {
            source?.channel?.close()
        } finally {
            source = null
            currentUri = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }
}

/** Elige el lector según la dirección: `vault://` va descifrado; el resto, como siempre. */
internal class VaultAwareDataSourceFactory(
    context: Context,
    private val streams: VaultStreams,
) : DataSource.Factory {

    private val defaultFactory = DefaultDataSource.Factory(context)

    override fun createDataSource(): DataSource = RoutingDataSource(defaultFactory.createDataSource(), streams)
}

private class RoutingDataSource(
    private val default: DataSource,
    private val streams: VaultStreams,
) : DataSource {

    private val listeners = mutableListOf<TransferListener>()
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        default.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val delegate = if (dataSpec.uri.scheme == VaultItem.URI_SCHEME) {
            VaultDataSource(streams).also { source -> listeners.forEach(source::addTransferListener) }
        } else {
            default
        }
        active = delegate
        return delegate.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        active?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders.orEmpty()

    override fun close() {
        try {
            active?.close()
        } finally {
            active = null
        }
    }
}
