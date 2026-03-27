package com.example.anda.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for configuration constants in the sync subsystem.
 *
 * These tests guard against accidental changes to:
 *  - [SyncPendingRecordsWorker.UNIQUE_WORK_NAME] — used as a stable key in WorkManager;
 *    changing it creates a duplicate, orphaned work chain in production.
 *  - [SyncPendingRecordsWorker.MAX_RETRY_ATTEMPTS] — controls how many times WorkManager
 *    retries before marking the work as FAILED permanently.
 *  - [SyncQueueRepository.MAX_BATCH_SIZE] — limits the number of items per sync batch.
 *
 * Note: WorkManager scheduling parameters (period, network constraints) are validated
 * in the instrumented SyncSchedulerTest (androidTest source set).
 */
class SyncWorkerConfigTest {

    // ── SyncPendingRecordsWorker constants ────────────────────────────────────

    @Test
    fun `UNIQUE_WORK_NAME is defined and not blank`() {
        assertNotNull(SyncPendingRecordsWorker.UNIQUE_WORK_NAME)
        assertTrue(
            "UNIQUE_WORK_NAME must not be blank",
            SyncPendingRecordsWorker.UNIQUE_WORK_NAME.isNotBlank()
        )
    }

    @Test
    fun `UNIQUE_WORK_NAME has stable value`() {
        // CHANGING THIS VALUE will orphan any existing WorkManager periodic jobs
        // in production — update this test only after a migration strategy is in place.
        assertEquals("sync_pending_records", SyncPendingRecordsWorker.UNIQUE_WORK_NAME)
    }

    @Test
    fun `MAX_RETRY_ATTEMPTS is between 3 and 10`() {
        // Too low → transient network errors give up too fast.
        // Too high → exhausted items log too much noise before moving to FAILED.
        val max = SyncPendingRecordsWorker.MAX_RETRY_ATTEMPTS
        assertTrue(
            "MAX_RETRY_ATTEMPTS ($max) should be in [3, 10] for a sensible retry budget",
            max in 3..10
        )
    }

    @Test
    fun `MAX_RETRY_ATTEMPTS is 6`() {
        assertEquals(6, SyncPendingRecordsWorker.MAX_RETRY_ATTEMPTS)
    }

    // ── SyncQueueRepository batch size ────────────────────────────────────────

    @Test
    fun `MAX_BATCH_SIZE is positive`() {
        assertTrue(
            "MAX_BATCH_SIZE must be positive",
            SyncQueueRepository.MAX_BATCH_SIZE > 0
        )
    }

    @Test
    fun `MAX_BATCH_SIZE does not exceed 100`() {
        // Very large batches in a single transaction increase SQLite lock time and
        // may push the WorkManager doWork() call past Android's 10-minute deadline.
        assertTrue(
            "MAX_BATCH_SIZE (${SyncQueueRepository.MAX_BATCH_SIZE}) must be ≤ 100",
            SyncQueueRepository.MAX_BATCH_SIZE <= 100
        )
    }

    @Test
    fun `MAX_BATCH_SIZE is 20`() {
        assertEquals(20, SyncQueueRepository.MAX_BATCH_SIZE)
    }

    // ── Relationship between retry attempts and backoff cap ───────────────────

    @Test
    fun `max retry delay at MAX_RETRY_ATTEMPTS is under 10 minutes`() {
        // Ensures the backoff cap stays within a reasonable window so a stuck item
        // doesn't block the queue for too long.
        val maxDelay = SyncQueueRepository.computeRetryDelayMs(SyncPendingRecordsWorker.MAX_RETRY_ATTEMPTS)
        val tenMinutesMs = 10 * 60 * 1_000L
        assertTrue(
            "Max retry delay ($maxDelay ms) should be under 10 minutes",
            maxDelay < tenMinutesMs
        )
    }
}

