package com.example.anda.data.services

import com.example.anda.data.local.dao.DocumentDao
import com.example.anda.data.local.dao.SyncAttemptLogDao

/**
 * Service for monitoring sync queue health, failure rates, and retry strategies.
 * Provides insights into sync operations and recovery metrics.
 */
class SyncMonitoringService(
    private val documentDao: DocumentDao,
    private val syncAttemptLogDao: SyncAttemptLogDao
) {

    /**
     * Data class representing sync health metrics
     */
    data class SyncHealthStats(
        val totalDocuments: Int,
        val unsyncedDocuments: Int,
        val syncedDocuments: Int,
        val successRate: Float, // 0.0 to 1.0
        val averageRetries: Float,
        val lastSyncTime: Long?
    )

    /**
     * Data class representing retry attempt statistics
     */
    data class RetryStats(
        val totalAttempts: Int,
        val averageAttemptsPerDocument: Float,
        val maxAttemptsForDocument: Int
    )

    /**
     * Data class representing retry schedule
     */
    data class RetrySchedule(
        val attempt: Int,
        val delayMs: Long,
        val description: String
    )

    /**
     * Get overall sync health metrics
     */
    suspend fun getSyncHealthStats(): SyncHealthStats {
        val totalCount = runCatching { documentDao.countAll() }
            .getOrDefault(0)
            .coerceAtLeast(0)
        val rawUnsyncedCount = runCatching { documentDao.countUnsynced() }
            .getOrDefault(0)
            .coerceAtLeast(0)
        val unsyncedCount = minOf(rawUnsyncedCount, totalCount)
        val syncedCount = (totalCount - unsyncedCount).coerceAtLeast(0)
        val successRate = if (totalCount > 0) {
            (syncedCount.toFloat() / totalCount).coerceIn(0f, 1f)
        } else {
            1f
        }
        val lastSyncTime = sanitizeEpochMillis(runCatching {
            syncAttemptLogDao.getLatestSuccessTime()
        }.getOrNull())
        val averageRetries = sanitizeNonNegativeFloat(runCatching {
            syncAttemptLogDao.getAverageAttemptsPerItem() ?: 0f
        }.getOrDefault(0f))

        return SyncHealthStats(
            totalDocuments = totalCount,
            unsyncedDocuments = unsyncedCount,
            syncedDocuments = syncedCount,
            successRate = successRate,
            averageRetries = averageRetries,
            lastSyncTime = lastSyncTime
        )
    }

    /**
     * Get retry attempt statistics derived from the sync attempt log.
     */
    suspend fun getRetryStats(): RetryStats {
        val totalAttempts = runCatching { syncAttemptLogDao.countAll() }
            .getOrDefault(0)
            .coerceAtLeast(0)
        val averageAttemptsPerDocument = sanitizeNonNegativeFloat(runCatching {
            syncAttemptLogDao.getAverageAttemptsPerItem() ?: 0f
        }.getOrDefault(0f))
        val maxAttemptsForDocument = runCatching {
            syncAttemptLogDao.getMaxAttemptsForItem() ?: 0
        }.getOrDefault(0).coerceAtLeast(0)

        return RetryStats(
            totalAttempts = totalAttempts,
            averageAttemptsPerDocument = averageAttemptsPerDocument,
            maxAttemptsForDocument = maxAttemptsForDocument
        )
    }

    /**
     * Get exponential backoff retry schedule for sync attempts.
     * Formula: 2^(attempt - 1) * 5 seconds, capped at 1 hour
     */
    fun getRetrySchedule(maxAttempts: Int = 10): List<RetrySchedule> {
        if (maxAttempts <= 0) return emptyList()
        val boundedAttempts = minOf(maxAttempts, MAX_RETRY_SCHEDULE_ATTEMPTS)

        return (1..boundedAttempts).map { attempt ->
            val delaySeconds = minOf(
                (Math.pow(2.0, (attempt - 1).toDouble()) * 5).toLong(),
                3600 // Cap at 1 hour
            )
            val delayMs = delaySeconds * 1000

            val description = when (attempt) {
                1 -> "Immediate retry (5 seconds)"
                2 -> "Short delay (10 seconds)"
                3 -> "Medium delay (20 seconds)"
                4 -> "Longer delay (40 seconds)"
                else -> formatRetryDelayDescription(delaySeconds)
            }

            RetrySchedule(
                attempt = attempt,
                delayMs = delayMs,
                description = description
            )
        }
    }

    private fun sanitizeNonNegativeFloat(value: Float): Float {
        return if (value.isFinite() && value >= 0f) value else 0f
    }

    private fun sanitizeEpochMillis(value: Long?): Long? {
        return value?.takeIf { it >= 0L }
    }

    private fun formatRetryDelayDescription(delaySeconds: Long): String {
        if (delaySeconds >= 3600L) return "1 hour (max)"
        val minutes = delaySeconds / 60L
        val seconds = delaySeconds % 60L
        return when {
            minutes <= 0L -> "$seconds seconds"
            seconds == 0L -> "$minutes min"
            else -> "$minutes min $seconds sec"
        }
    }

    companion object {
        private const val MAX_RETRY_SCHEDULE_ATTEMPTS = 50
    }

}


