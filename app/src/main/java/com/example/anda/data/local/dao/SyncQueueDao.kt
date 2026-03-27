package com.example.anda.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.anda.data.local.entity.SyncQueueEntity

@Dao
interface SyncQueueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: SyncQueueEntity)

    @Query(
        """
        SELECT * FROM sync_queue
        WHERE itemType = :itemType AND itemRef = :itemRef
        LIMIT 1
        """
    )
    suspend fun findByTypeAndRef(itemType: String, itemRef: String): SyncQueueEntity?

    @Query(
        """
        UPDATE sync_queue
        SET payloadJson = :payloadJson,
            status = 'PENDING',
            nextAttemptAt = :now,
            lastError = null,
            updatedAt = :now
        WHERE id = :id
        """
    )
    suspend fun refreshAsPending(id: Long, payloadJson: String, now: Long = System.currentTimeMillis()): Int

    @Query(
        """
        SELECT * FROM sync_queue
        WHERE status IN ('PENDING', 'FAILED') AND nextAttemptAt <= :now
        ORDER BY createdAt ASC
        LIMIT :limit
        """
    )
    suspend fun nextBatch(now: Long = System.currentTimeMillis(), limit: Int = 20): List<SyncQueueEntity>

    @Query(
        """
        UPDATE sync_queue
        SET status = :status, attempts = :attempts, nextAttemptAt = :nextAttemptAt,
            lastError = :lastError, updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateAttempt(
        id: Long,
        status: String,
        attempts: Int,
        nextAttemptAt: Long,
        lastError: String?,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status IN ('PENDING', 'FAILED')")
    suspend fun pendingCount(): Int

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'FAILED'")
    suspend fun failedCount(): Int

    @Query(
        """
        UPDATE sync_queue
        SET nextAttemptAt = :now,
            status = 'FAILED',
            updatedAt = :now
        WHERE status = 'FAILED'
        """
    )
    suspend fun retryAllFailedNow(now: Long = System.currentTimeMillis()): Int
}

