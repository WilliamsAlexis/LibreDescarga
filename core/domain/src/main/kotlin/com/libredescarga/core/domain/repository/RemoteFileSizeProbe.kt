package com.libredescarga.core.domain.repository

/** Consulta cuánto pesa un archivo de internet sin descargarlo. */
interface RemoteFileSizeProbe {

    /** Tamaño en bytes que informa el servidor, o null si no lo informa o no se pudo consultar. */
    suspend fun sizeOf(url: String): Long?
}
