package com.libredescarga.core.vault

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.libredescarga.core.database.dao.VaultItemDao
import com.libredescarga.core.database.entity.VaultItemEntity
import com.libredescarga.core.domain.vault.VaultAuthRepository
import com.libredescarga.core.domain.vault.VaultRepository
import com.libredescarga.core.downloader.DownloadStorage
import com.libredescarga.core.model.LibraryMedia
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.model.VaultItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bóveda privada: guarda, cifra, restaura y borra los archivos que el usuario protege.
 * Cada archivo se guarda con un identificador aleatorio (no con su nombre real) y, si el cifrado está activo,
 * solo se puede leer desde dentro de la app.
 */
@Singleton
internal class LocalVaultRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: VaultItemDao,
    private val files: VaultFiles,
    private val crypto: VaultCrypto,
    private val auth: VaultAuthRepository,
    private val storage: DownloadStorage,
) : VaultRepository {

    // Lista de elementos de la bóveda; se actualiza sola cuando cambia la base de datos.
    override val items: Flow<List<VaultItem>> = dao.observe().map { list -> list.map { it.toModel() } }

    // Mueve a la bóveda un archivo que ya está en la biblioteca del teléfono.
    override suspend fun importFromLibrary(media: LibraryMedia): VaultItem = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(Uri.parse(media.uri))
            ?: throw IOException("No se pudo leer ${media.displayName}")
        input.use { store(it, media.displayName, media.kind, media.mimeType, media.durationMs) }
    }

    // Mete en la bóveda un archivo de la carpeta de trabajo y luego borra el original sin cifrar.
    override suspend fun importFile(
        file: File,
        kind: MediaKind,
        name: String,
        mimeType: String?,
        durationMs: Long,
    ): VaultItem = withContext(Dispatchers.IO) {
        val item = file.inputStream().use {
            store(it, name, kind, mimeType, durationMs.takeIf { d -> d > 0 } ?: readDuration(file))
        }
        file.delete() // El archivo en claro no debe quedarse en la carpeta de trabajo.
        item
    }

    // Guarda los datos en la bóveda (cifrados o no, según el ajuste) y registra el elemento en la base de datos.
    private suspend fun store(
        input: java.io.InputStream,
        name: String,
        kind: MediaKind,
        mimeType: String?,
        durationMs: Long,
    ): VaultItem {
        val id = UUID.randomUUID().toString()
        val encrypt = auth.settings.first().encryptNewFiles
        val target = files.fileFor(id)
        try {
            if (encrypt) crypto.encrypt(input, target, id) else crypto.copyPlain(input, target)
        } catch (error: Exception) {
            target.delete() // Nunca queda un archivo a medias.
            throw error
        }
        // El tamaño que se muestra es el del archivo original, no el del archivo cifrado.
        val plainSize = if (encrypt) {
            crypto.openDecryptingChannel(target, id).use { it.size() }
        } else {
            target.length()
        }
        val entity = VaultItemEntity(
            id = id,
            name = name,
            kind = kind.name,
            mimeType = mimeType,
            sizeBytes = plainSize,
            durationMs = durationMs,
            encrypted = encrypt,
            addedAt = System.currentTimeMillis(),
        )
        dao.upsert(entity)
        return entity.toModel()
    }

    // Saca un elemento de la bóveda y lo devuelve a la biblioteca (descifrándolo). Devuelve false si algo falla.
    override suspend fun restore(id: String): Boolean = withContext(Dispatchers.IO) {
        val entity = dao.get(id) ?: return@withContext false
        val source = files.fileFor(id).takeIf { it.exists() } ?: return@withContext false
        val temp = files.tempFile(entity.name)
        try {
            if (entity.encrypted) crypto.decryptTo(source, temp, id) else source.copyTo(temp, overwrite = true)
            storage.publishMedia(temp, MediaKind.valueOf(entity.kind), treeUri = null)
            source.delete()
            dao.delete(id)
            true
        } catch (_: Exception) {
            false
        } finally {
            temp.delete()
        }
    }

    // Borra definitivamente los elementos indicados: el archivo y su registro.
    override suspend fun delete(ids: List<String>) = withContext(Dispatchers.IO) {
        ids.forEach { id ->
            files.fileFor(id).delete()
            dao.delete(id)
        }
    }

    // Cambia el nombre que se ve en la bóveda (el archivo guardado no cambia).
    override suspend fun rename(id: String, name: String) = dao.rename(id, name)

    // Borra todo el contenido de la bóveda y los archivos temporales. Se usa al olvidar el acceso.
    override suspend fun eraseAll() = withContext(Dispatchers.IO) {
        files.directory.listFiles()?.forEach { it.delete() }
        files.clearTemp()
        dao.clear()
    }

    // Lee la duración de un video o audio; si no se puede, devuelve 0.
    private fun readDuration(file: File): Long = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }
    }.getOrDefault(0L)

    // Convierte el registro de la base de datos en el modelo que usa la pantalla.
    private fun VaultItemEntity.toModel() = VaultItem(
        id = id,
        name = name,
        kind = MediaKind.entries.firstOrNull { it.name == kind } ?: MediaKind.VIDEO,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        durationMs = durationMs,
        encrypted = encrypted,
        addedAt = addedAt,
    )
}
