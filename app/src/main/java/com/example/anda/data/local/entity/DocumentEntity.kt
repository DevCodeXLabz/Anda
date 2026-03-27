package com.example.anda.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "documents",
    indices = [
        Index(value = ["documentId"], unique = true),
        Index(value = ["isSynced"]),
        Index(value = ["sourceRequestCode"])
    ]
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: String,
    val documentType: String,
    val companyCnpj: String,
    val title: String,
    val payloadJson: String,
    val contentHash: String,
    val sourceRequestCode: String? = null,
    val validUntil: Long? = null,
    val pdfPath: String? = null,
    val auditTxtPath: String? = null,
    val signedAt: Long? = null,
    val signedBy: String? = null,
    /** Base64 da assinatura RSA-SHA256 gerada no AndroidKeyStore (Lei 14.063/2020). */
    val signatureB64: String? = null,
    /** Fingerprint SHA-256 da chave pública usada na assinatura. */
    val publicKeyFingerprint: String? = null,
    /** Algoritmo de assinatura (ex.: SHA256withRSA). */
    val signatureAlgorithm: String? = null,
    val isSynced: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

