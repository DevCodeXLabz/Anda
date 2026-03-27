package com.example.anda.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.anda.data.requests.ServiceRequestStatus

@Entity(
    tableName = "service_requests",
    indices = [
        Index(value = ["requestCode"], unique = true),
        Index(value = ["status"]),
        Index(value = ["contractorCnpj"]),
        Index(value = ["assignedEmployeeCpf"])
    ]
)
data class ServiceRequestEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val requestCode: String,
    val contractorName: String,
    val contractorCnpj: String,
    val requestedDocumentType: String,
    val preferredContactChannel: String = "app",
    val contactWhatsapp: String = "",
    val status: String = ServiceRequestStatus.OPEN,
    val assignedEmployeeCpf: String = "",
    val assignedEmployeeName: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

