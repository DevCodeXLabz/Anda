package com.example.anda.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local employee record — used for autofill in ASO/OS forms and
 * for tracking ASO validity per worker.
 */
@Entity(
    tableName = "employees",
    indices = [
        Index(value = ["cpf"], unique = true),
        Index(value = ["companyCnpj"])
    ]
)
data class EmployeeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** CPF (11 digits, stored as digits only). */
    val cpf: String,
    val name: String,
    /** Date of birth in "dd/MM/yyyy" format. */
    val birthDate: String = "",
    /** Job role / cargo. */
    val role: String = "",
    /** CNPJ of the employing company (14 digits). */
    val companyCnpj: String = "",
    val companyName: String = "",
    /** internal or freelancer */
    val collaboratorType: String = "internal",
    /** approved or pending */
    val approvalStatus: String = "approved",
    val approvedBy: String = "",
    val approvedAt: Long? = null,
    /** Unix timestamp (ms) of the last issued ASO, or null if never. */
    val lastAsoDate: Long? = null,
    /** Unix timestamp (ms) when next periodic ASO is due, or null. */
    val nextAsoDate: Long? = null,
    /** Optional free-text notes (health restrictions, observations). */
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

