package com.libredescarga.core.data.repository

import com.libredescarga.core.domain.repository.StorageRepository
import com.libredescarga.core.downloader.DownloadStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Consulta el espacio libre y el nombre de la carpeta de descargas del teléfono. */
internal class DeviceStorageRepository @Inject constructor(
    private val storage: DownloadStorage,
) : StorageRepository {

    // Espacio libre en bytes (se calcula fuera del hilo principal para no congelar la pantalla).
    override suspend fun availableBytes(): Long = withContext(Dispatchers.IO) { storage.availableBytes() }

    // Nombre legible de la carpeta elegida por el usuario.
    override suspend fun folderName(treeUri: String): String? =
        withContext(Dispatchers.IO) { storage.folderName(treeUri) }
}
