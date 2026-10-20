package com.libredescarga.core.data.repository

import android.content.Context
import com.libredescarga.core.domain.repository.BlocklistRepository
import com.libredescarga.core.domain.repository.UserPreferencesRepository
import com.libredescarga.core.network.BlocklistParser
import com.libredescarga.core.network.RemoteBlocklistStore
import com.libredescarga.core.security.DomainMatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lista de sitios bloqueados. Se suman tres fuentes:
 * 1. la lista incluida en la app (assets/blocklist.json);
 * 2. la última lista descargada de GitHub (solicitudes de retiro por derechos de autor);
 * 3. los sitios que el usuario decidió bloquear.
 */
@Singleton
internal class AssetBlocklistRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val remote: RemoteBlocklistStore,
    private val preferences: UserPreferencesRepository,
) : BlocklistRepository {

    private val mutex = Mutex()
    private var bundled: Set<String>? = null

    override suspend fun blockedDomainFor(host: String): String? = DomainMatcher.matches(host, all())

    override suspend fun all(): Set<String> = withContext(Dispatchers.IO) {
        bundled().orEmpty() + remote.read()?.domains.orEmpty() + preferences.settings.first().userBlockedDomains
    }

    private suspend fun bundled(): Set<String> = mutex.withLock {
        bundled ?: runCatching {
            val text = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            BlocklistParser.parse(text).domains.toSet()
        }.getOrDefault(emptySet()).also { bundled = it }
    }

    private companion object {
        const val ASSET_NAME = "blocklist.json"
    }
}
