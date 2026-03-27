package com.example.anda.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity for tracking document versions and revision history.
 * Each edit creates a new version entry for audit trail purposes.
 */
@Entity(
    tableName = "document_versions",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("documentId"),
        Index("createdAt"),
        Index("documentId", "createdAt")
    ]
)
data class DocumentVersionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Reference to the original document
    val documentId: Long,

    // Version number (1, 2, 3, etc.)
    val versionNumber: Int,

    // Snapshot of document data at this version (JSON)
    val contentSnapshot: String,

    // What changed in this version (JSON map of field changes)
    val changesSummary: String = "{}",

    // Who made the change
    val editedBy: String = "Unknown",

    // Why they made the change (optional)
    val editReason: String = "",

    // Type of change (CREATE, EDIT, APPROVED, SIGNED, etc.)
    val changeType: String = "EDIT",

    // Timestamps
    val createdAt: Long = System.currentTimeMillis(),

    // Whether this version is the current/active one
    val isCurrent: Boolean = false,

    // Whether this version can be restored (false for immutable versions)
    val isRestorable: Boolean = true,

    // Hash for integrity verification (SHA-256 of content)
    val contentHash: String = "",

    // Associated metadata (signatures, approval notes, etc.)
    val metadata: String = "{}"
)

