package com.example.anda.data.sync

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.anda.R
import com.example.anda.data.repository.DocumentLocalRepository

class ComplianceAlertWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    companion object {
        const val UNIQUE_WORK_NAME = "com.anda.work.COMPLIANCE_ALERTS_PERIODIC"
        const val ONE_TIME_WORK_NAME = "com.anda.work.COMPLIANCE_ALERTS_ONE_TIME"
        const val CHANNEL_ID = "anda_doc_expiry_channel"
        const val NOTIF_ID = 1500
        // Test hook: tests can set this to override repository counts and capture notification counts.
        // It is internal API intended only for unit tests.
        internal var testHook: TestHook? = null
    }

    override suspend fun doWork(): Result {
        return runCatching {
            val now = System.currentTimeMillis()

            // If a test hook provides counts, use it and skip repository initialization to avoid
            // touching Android/Room in unit tests.
            val (expired, expiring30d) = testHook?.countsProvider?.invoke(now) ?: run {
                DocumentLocalRepository.initialize(applicationContext)
                Pair(
                    DocumentLocalRepository.countExpiredBefore(now),
                    DocumentLocalRepository.countExpiringBetween(now, now + 30L * 24L * 60L * 60L * 1000L)
                )
            }

            if (expired > 0 || expiring30d > 0) {
                showNotification(expired, expiring30d)
            }

            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    private fun showNotification(expired: Int, expiring: Int) {
        // If tests provided a counts captor, capture counts and return early (no Android resources used).
        val hook = testHook
        if (hook?.notificationCountsCaptor != null) {
            hook.notificationCountsCaptor.add(NotificationCountsRecord(expired, expiring))
            return
        }

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val title = when {
            expired > 0 -> applicationContext.getString(R.string.notif_expiry_critical_title, expired)
            else -> applicationContext.getString(R.string.notif_expiry_warning_title, expiring)
        }

        val body = when {
            expired > 0 -> if (expiring > 0) applicationContext.getString(R.string.notif_expiry_critical_body_with_warning, expired, expiring) else applicationContext.getString(R.string.notif_expiry_critical_body, expired)
            else -> applicationContext.getString(R.string.notif_expiry_warning_body, expiring)
        }

        val deepLink = Uri.parse("anda://app/compliance_alerts")
        val intent = Intent(Intent.ACTION_VIEW, deepLink).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(NOTIF_ID, notif)
    }
}

// Internal test-only utilities -------------------------------------------------
internal data class NotificationCountsRecord(val expired: Int, val expiring: Int)

/**
 * Test hook allows unit tests to override counts and capture notification counts.
 * This is intentionally minimal and internal to avoid expanding production API surface.
 */
internal class TestHook(
    val countsProvider: ((now: Long) -> Pair<Int, Int>)? = null,
    val notificationCountsCaptor: MutableList<NotificationCountsRecord>? = null
)

/**
 * A pure, test-oriented helper that performs the compliance count check using only
 * the test hook (no Android resources). Tests can call this from a coroutine scope.
 */
internal suspend fun runComplianceCheckForTest(now: Long): androidx.work.ListenableWorker.Result {
    val hook = ComplianceAlertWorker.testHook ?: return androidx.work.ListenableWorker.Result.failure()
    val (expired, expiring) = hook.countsProvider?.invoke(now) ?: Pair(0, 0)
    if (expired > 0 || expiring > 0) {
        hook.notificationCountsCaptor?.add(NotificationCountsRecord(expired, expiring))
    }
    return androidx.work.ListenableWorker.Result.success()
}


