package com.libredescarga.core.testing

import com.libredescarga.core.domain.vault.StoredAuth
import com.libredescarga.core.domain.vault.VaultAuthStore
import com.libredescarga.core.domain.vault.VaultRepository
import com.libredescarga.core.domain.vault.VaultSession
import com.libredescarga.core.model.LibraryMedia
import com.libredescarga.core.model.MediaKind
import com.libredescarga.core.model.VaultItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

class FakeVaultAuthStore(initial: StoredAuth = StoredAuth()) : VaultAuthStore {
    private val state = MutableStateFlow(initial)
    override val data: Flow<StoredAuth> = state
    val current: StoredAuth get() = state.value

    override suspend fun read(): StoredAuth = state.value

    override suspend fun update(transform: (StoredAuth) -> StoredAuth) = state.update(transform)
}

class FakeVaultSession(initiallyUnlocked: Boolean = false) : VaultSession {
    private val state = MutableStateFlow(initiallyUnlocked)
    override val unlocked: StateFlow<Boolean> = state.asStateFlow()

    override fun unlock() {
        state.value = true
    }

    override fun lock() {
        state.value = false
    }
}

class FakeVaultRepository : VaultRepository {
    private val state = MutableStateFlow<List<VaultItem>>(emptyList())
    override val items: Flow<List<VaultItem>> = state
    val current: List<VaultItem> get() = state.value

    /** Si se asigna, la próxima importación falla con este error. */
    var importError: Exception? = null
    var restoreResult = true
    var erased = false
        private set

    private var counter = 0

    override suspend fun importFromLibrary(media: LibraryMedia): VaultItem {
        importError?.let { throw it }
        return add(media.title, media.kind, media.mimeType, media.sizeBytes, media.durationMs)
    }

    override suspend fun importFile(file: File, kind: MediaKind, name: String, mimeType: String?, durationMs: Long): VaultItem {
        importError?.let { throw it }
        return add(name, kind, mimeType, 0, durationMs)
    }

    override suspend fun restore(id: String): Boolean {
        if (restoreResult) state.update { list -> list.filterNot { it.id == id } }
        return restoreResult
    }

    override suspend fun delete(ids: List<String>) = state.update { list -> list.filterNot { it.id in ids } }

    override suspend fun rename(id: String, name: String) =
        state.update { list -> list.map { if (it.id == id) it.copy(name = name) else it } }

    override suspend fun eraseAll() {
        erased = true
        state.value = emptyList()
    }

    private fun add(name: String, kind: MediaKind, mime: String?, size: Long, duration: Long): VaultItem {
        val item = VaultItem(
            id = "item-${++counter}",
            name = name,
            kind = kind,
            mimeType = mime,
            sizeBytes = size,
            durationMs = duration,
            encrypted = true,
            addedAt = counter.toLong(),
        )
        state.update { it + item }
        return item
    }
}
