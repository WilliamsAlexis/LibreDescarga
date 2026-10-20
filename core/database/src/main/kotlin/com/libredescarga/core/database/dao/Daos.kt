package com.libredescarga.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.libredescarga.core.database.entity.DownloadEntity
import com.libredescarga.core.database.entity.SearchHistoryEntity
import com.libredescarga.core.database.entity.SiteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {

    @Query(
        """
        SELECT `query` FROM search_history
        WHERE `query` LIKE :prefix || '%' ESCAPE '\'
        ORDER BY lastUsedAt DESC
        LIMIT :limit
        """,
    )
    fun observeMatching(prefix: String, limit: Int): Flow<List<String>>

    @Upsert
    suspend fun upsert(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE `query` = :query")
    suspend fun delete(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clear()

    /** Conserva solo las [keep] búsquedas más recientes. */
    @Query(
        """
        DELETE FROM search_history WHERE `query` NOT IN (
            SELECT `query` FROM search_history ORDER BY lastUsedAt DESC LIMIT :keep
        )
        """,
    )
    suspend fun trim(keep: Int)
}

@Dao
interface SiteDao {

    @Query("SELECT * FROM sites ORDER BY position ASC, id ASC")
    fun observeAll(): Flow<List<SiteEntity>>

    @Query("SELECT COUNT(*) FROM sites")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(position), -1) FROM sites")
    suspend fun maxPosition(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(sites: List<SiteEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(site: SiteEntity): Long

    @Query("DELETE FROM sites WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE sites SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE sites SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)

    @Query("SELECT * FROM sites ORDER BY position ASC, id ASC")
    suspend fun getAll(): List<SiteEntity>
}

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: String): DownloadEntity?

    @Query("SELECT * FROM downloads")
    suspend fun getAll(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE status IN (:statuses)")
    suspend fun getByStatuses(statuses: List<String>): List<DownloadEntity>

    @Query("UPDATE downloads SET priority = :priority WHERE id = :id")
    suspend fun setPriority(id: String, priority: Int)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: String)

    /** Vuelve a la cola desde cero (los archivos parciales se conservan para reanudar). */
    @Query(
        """
        UPDATE downloads SET
            status = :status,
            errorMessage = NULL,
            speedBytesPerSecond = NULL,
            etaSeconds = NULL
        WHERE id = :id
        """,
    )
    suspend fun requeue(id: String, status: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(downloads: List<DownloadEntity>)

    @Query(
        """
        UPDATE downloads SET
            progressPercent = :progress,
            speedBytesPerSecond = :speed,
            etaSeconds = :eta,
            totalBytes = COALESCE(:totalBytes, totalBytes),
            downloadedBytes = COALESCE(:downloadedBytes, downloadedBytes)
        WHERE id = :id
        """,
    )
    suspend fun updateProgress(
        id: String,
        progress: Float,
        speed: Long?,
        eta: Long?,
        totalBytes: Long?,
        downloadedBytes: Long?,
    )

    @Query("UPDATE downloads SET status = :status, errorMessage = :error WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, error: String?)

    @Query(
        """
        UPDATE downloads SET
            status = :status,
            outputUri = :outputUri,
            progressPercent = 100,
            speedBytesPerSecond = NULL,
            etaSeconds = 0,
            errorMessage = NULL,
            completedAt = :completedAt
        WHERE id = :id
        """,
    )
    suspend fun markCompleted(id: String, status: String, outputUri: String, completedAt: Long)
}
