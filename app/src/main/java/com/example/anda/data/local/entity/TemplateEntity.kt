package com.example.anda.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * Template entity for storing document templates.
 * Templates can be used to pre-fill documents based on document type, CNAE, or user preference.
 */
@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Template metadata
    val name: String,
    val description: String = "",
    val documentType: String, // ASO, PCMSO, PT, etc.
    val category: String = "General", // Custom category for organization

    // CNAE applicability (comma-separated list of CNAEs, or "*" for all)
    val applicableCnaes: String = "*",

    // Template content (JSON or structured text)
    val contentJson: String,

    // Version tracking
    val version: Int = 1,
    val isActive: Boolean = true,

    // Timestamps
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    // User who created/owns this template
    val createdBy: String = "System",

    // Whether this is a system template (can't be deleted)
    val isSystemTemplate: Boolean = false,

    // Usage statistics
    val usageCount: Int = 0,
    val lastUsedAt: Long? = null,

    // Icon/color for UI display
    val iconId: String = "ic_document",
    val colorHex: String = "#2196F3"
)

