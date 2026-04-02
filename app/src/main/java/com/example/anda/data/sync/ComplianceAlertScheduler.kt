package com.example.anda.data.sync

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object ComplianceAlertScheduler {

    fun schedulePeriodicChecks(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<ComplianceAlertWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                ComplianceAlertWorker.UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
    }

    fun scheduleOneTimeCheck(context: Context) {
        val constraints = Constraints.Builder().build()
        val request = OneTimeWorkRequestBuilder<ComplianceAlertWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                ComplianceAlertWorker.ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
    }
}

