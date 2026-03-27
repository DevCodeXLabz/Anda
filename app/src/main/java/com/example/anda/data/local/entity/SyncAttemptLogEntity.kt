package com.example.anda.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_attempt_log",
    indices = [Index(value = ["itemRef"]), Index(value = ["createdAt"])]
)
data class SyncAttemptLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val itemRef: String,
    val status: String,
    val attemptNumber: Int,
    val message: String,
    val createdAt: Long = System.currentTimeMillis()
)

