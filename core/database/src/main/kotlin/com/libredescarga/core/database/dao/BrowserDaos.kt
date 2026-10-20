package com.libredescarga.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.libredescarga.core.database.entity.BookmarkEntity
import com.libredescarga.core.database.entity.BrowserHistoryEntity
import com.libredescarga.core.database.entity.VaultItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowserHistoryDao {

    @Query("SELECT * FROM browser_history ORDER BY visitedAt DESC LIMIT :limit")
    fun observe(limit: Int): Flow<List<BrowserHistoryEntity>>

    @Upsert
    suspend fun upsert(entry: BrowserHistoryEntity)

    @Query("DELETE FROM browser_history WHERE url = :url")
    suspend fun delete(url: String)

    @Query("DELETE FROM browser_history")
    suspend fun clear()

    /** Conserva solo las [keep] visitas más recientes. */
    @Query(
        """
        DELETE FROM browser_history WHERE url NOT IN (
            SELECT url FROM browser_history ORDER BY visitedAt DESC LIMIT :keep
        )
        """,
    )
    suspend fun trim(keep: Int)
}

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observe(): Flow<List<BookmarkEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url)")
    suspend fun exists(url: String): Boolean

    @Upsert
    suspend fun upsert(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun delete(url: String)
}

@Dao
interface VaultItemDao {

    @Query("SELECT * FROM vault_items ORDER BY addedAt DESC")
    fun observe(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE id = :id")
    suspend fun get(id: String): VaultItemEntity?

    @Query("SELECT * FROM vault_items")
    suspend fun getAll(): List<VaultItemEntity>

    @Upsert
    suspend fun upsert(item: VaultItemEntity)

    @Query("UPDATE vault_items SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM vault_items")
    suspend fun clear()
}
