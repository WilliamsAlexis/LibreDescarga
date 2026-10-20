package com.libredescarga.core.data.repository

import com.libredescarga.core.database.dao.SiteDao
import com.libredescarga.core.database.entity.SiteEntity
import com.libredescarga.core.database.mapper.toModel
import com.libredescarga.core.domain.repository.SiteRepository
import com.libredescarga.core.model.MediaError
import com.libredescarga.core.model.Site
import com.libredescarga.core.security.UrlValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

internal class OfflineSiteRepository @Inject constructor(
    private val dao: SiteDao,
) : SiteRepository {

    override val sites: Flow<List<Site>> = dao.observeAll()
        .onStart { seedDefaultsIfEmpty() }
        .map { list -> list.map { it.toModel() } }

    override suspend fun addCustomSite(name: String, url: String) {
        val valid = UrlValidator.validate(url) as? UrlValidator.Result.Valid
            ?: throw MediaError.InvalidUrl()
        dao.insert(
            SiteEntity(
                name = name.trim().ifEmpty { valid.host },
                url = valid.url,
                searchProvider = null,
                isCustom = true,
                enabled = true,
                position = dao.maxPosition() + 1,
            ),
        )
    }

    override suspend fun removeSite(id: Long) = dao.delete(id)

    override suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)

    override suspend fun move(id: Long, up: Boolean) {
        val sites = dao.getAll()
        val index = sites.indexOfFirst { it.id == id }
        val target = if (up) index - 1 else index + 1
        if (index < 0 || target !in sites.indices) return
        // Se reasignan posiciones consecutivas, para que el orden sea siempre claro.
        val reordered = sites.toMutableList().apply { add(target, removeAt(index)) }
        reordered.forEachIndexed { position, site -> if (site.position != position) dao.setPosition(site.id, position) }
    }

    private suspend fun seedDefaultsIfEmpty() {
        if (dao.count() == 0) dao.insertAll(DefaultSites.entities)
    }
}
