package com.libredescarga.core.network

import android.content.Context
import com.libredescarga.core.config.AppConfig
import com.libredescarga.core.domain.settings.BlocklistUpdater
import com.libredescarga.core.model.BlocklistRefresh
import com.libredescarga.core.model.UpdateException
import com.libredescarga.core.model.UpdateFailure
import com.libredescarga.core.security.DomainRules
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Contenido de `blocklist.json` (el mismo formato en la app y en GitHub). */
@Serializable
data class BlocklistFile(
    val version: Int = 0,
    val domains: List<String> = emptyList(),
)

object BlocklistParser {
    private const val MAX_DOMAINS = 10_000
    private val json = Json { ignoreUnknownKeys = true }

    /** Lee la lista y descarta cualquier entrada que no sea un dominio válido. */
    fun parse(text: String): BlocklistFile {
        val file = try {
            json.decodeFromString<BlocklistFile>(text)
        } catch (error: Exception) {
            throw UpdateException(UpdateFailure.INVALID_MANIFEST, "Lista no válida", error)
        }
        return BlocklistFile(
            version = file.version,
            domains = file.domains.mapNotNull(DomainRules::normalize).distinct().take(MAX_DOMAINS),
        )
    }
}

/** Última lista descargada de GitHub, guardada en el almacenamiento interno de la app. */
@Singleton
class RemoteBlocklistStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val file: File get() = File(context.filesDir, "blocklist-remote.json")

    fun read(): BlocklistFile? = runCatching {
        file.takeIf { it.exists() }?.readText()?.let(BlocklistParser::parse)
    }.getOrNull()

    fun write(list: BlocklistFile) {
        // Se escribe en un archivo temporal y se renombra: una descarga a medias nunca deja la lista dañada.
        val temp = File(file.parentFile, "blocklist-remote.json.tmp")
        temp.writeText(Json.encodeToString(BlocklistFile.serializer(), list))
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }
}

@Singleton
internal class GithubBlocklistUpdater @Inject constructor(
    private val client: GithubClient,
    private val store: RemoteBlocklistStore,
) : BlocklistUpdater {

    override suspend fun refresh(): BlocklistRefresh {
        val remote = BlocklistParser.parse(client.fetchText(AppConfig.BLOCKLIST_URL))
        val current = withContext(Dispatchers.IO) { store.read() }
        if (current != null && current.version >= remote.version) return BlocklistRefresh.AlreadyCurrent
        withContext(Dispatchers.IO) { store.write(remote) }
        return BlocklistRefresh.Updated(remote.version, remote.domains.size)
    }
}
