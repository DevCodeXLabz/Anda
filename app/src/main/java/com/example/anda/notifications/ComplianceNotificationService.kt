package com.example.anda.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.example.anda.R
import com.example.anda.data.sync.ComplianceAlertWorker

class ComplianceNotificationService(private val context: Context) {

    fun createNotificationChannel() {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            ComplianceAlertWorker.CHANNEL_ID,
            context.getString(R.string.notif_channel_doc_expiry_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_doc_expiry_desc)
            enableVibration(true)
        }
        mgr.createNotificationChannel(channel)
    }
}

