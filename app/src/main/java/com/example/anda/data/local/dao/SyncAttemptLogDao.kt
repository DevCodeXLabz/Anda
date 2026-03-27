package com.example.anda.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.anda.data.local.entity.SyncAttemptLogEntity

@Dao
interface SyncAttemptLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SyncAttemptLogEntity)

    @Query("SELECT * FROM sync_attempt_log WHERE itemRef = :itemRef ORDER BY createdAt DESC LIMIT :limit")
    suspend fun listByItemRef(itemRef: String, limit: Int = 30): List<SyncAttemptLogEntity>

    @Query("SELECT MAX(createdAt) FROM sync_attempt_log WHERE status = 'SUCCESS'")
    suspend fun getLatestSuccessTime(): Long?

    @Query("SELECT COUNT(*) FROM sync_attempt_log")
    suspend fun countAll(): Int

    /** Average number of attempts recorded per distinct itemRef. Returns null when the table is empty. */
    @Query("""
        SELECT AVG(attempt_count)
        FROM (SELECT COUNT(*) AS attempt_count FROM sync_attempt_log GROUP BY itemRef)
    """)
    suspend fun getAverageAttemptsPerItem(): Float?

    /** Maximum number of attempts recorded for any single itemRef. Returns null when the table is empty. */
    @Query("""
        SELECT MAX(attempt_count)
        FROM (SELECT COUNT(*) AS attempt_count FROM sync_attempt_log GROUP BY itemRef)
    """)
    suspend fun getMaxAttemptsForItem(): Int?
}

