package com.example.anda.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_queue",
    indices = [
        Index(value = ["status"]),
        Index(value = ["nextAttemptAt"]),
        Index(value = ["itemType", "itemRef"], unique = true)
    ]
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val itemType: String,
    val itemRef: String,
    val payloadJson: String,
    val status: String = STATUS_PENDING,
    val attempts: Int = 0,
    val nextAttemptAt: Long = System.currentTimeMillis(),
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_PROCESSING = "PROCESSING"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_DONE = "DONE"
    }
}

