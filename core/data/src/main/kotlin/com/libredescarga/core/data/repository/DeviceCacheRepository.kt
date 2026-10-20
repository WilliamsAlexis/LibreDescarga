package com.libredescarga.core.data.repository

import android.content.Context
import com.libredescarga.core.domain.settings.CacheRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Caché de la app: miniaturas, páginas del navegador y actualizaciones descargadas. No toca descargas ni la carpeta privada. */
internal class DeviceCacheRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : CacheRepository {

    private val roots: List<File>
        get() = listOfNotNull(context.cacheDir, context.externalCacheDir)

    override suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) { roots.sumOf(::sizeOf) }

    override suspend fun clear(): Long = withContext(Dispatchers.IO) {
        val before = roots.sumOf(::sizeOf)
        roots.forEach { root -> root.listFiles()?.forEach { it.deleteRecursively() } }
        (before - roots.sumOf(::sizeOf)).coerceAtLeast(0)
    }

    private fun sizeOf(file: File): Long = if (file.isFile) file.length() else file.listFiles()?.sumOf(::sizeOf) ?: 0L
}
