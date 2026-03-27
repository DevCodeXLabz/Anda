package com.example.anda.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.anda.core.stability.CrashShield
import com.example.anda.data.repository.SstRepository

class SyncPendingRecordsWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return runCatching {
            val pending = SyncQueueRepository.pendingCount()
            if (pending == 0) {
                return Result.success()
            }
            val synced = SstRepository.syncPendingRecords()
            // synced == 0 with pending > 0 means the remote call failed silently;
            // ask WorkManager to retry with its exponential-backoff policy.
            if (synced > 0) Result.success() else Result.retry()
        }.getOrElse {
            CrashShield.recordRecoverableError("SyncPendingRecordsWorker/doWork", it)
            if (runAttemptCount >= MAX_RETRY_ATTEMPTS) {
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "sync_pending_records"
        const val MAX_RETRY_ATTEMPTS = 6
    }
}
