package com.example.anda.data.requests

import java.util.Locale

/**
 * Canonical lifecycle states for service requests.
 *
 * Older app builds used `DONE`; this helper normalizes all legacy values to
 * `COMPLETED` so UI, notifications and assignment logic stay consistent.
 */
object ServiceRequestStatus {
    const val OPEN = "OPEN"
    const val ASSIGNED = "ASSIGNED"
    const val IN_PROGRESS = "IN_PROGRESS"
    const val COMPLETED = "COMPLETED"

    val ACTIVE = setOf(ASSIGNED, IN_PROGRESS)

    fun normalize(rawStatus: String?): String {
        return when (rawStatus.orEmpty().trim().uppercase(Locale.ROOT)) {
            OPEN -> OPEN
            ASSIGNED -> ASSIGNED
            IN_PROGRESS, "INPROGRESS" -> IN_PROGRESS
            COMPLETED, "DONE" -> COMPLETED
            else -> OPEN
        }
    }

    fun isOpen(rawStatus: String?): Boolean = normalize(rawStatus) == OPEN

    fun isAssigned(rawStatus: String?): Boolean = normalize(rawStatus) == ASSIGNED

    fun isInProgress(rawStatus: String?): Boolean = normalize(rawStatus) == IN_PROGRESS

    fun isCompleted(rawStatus: String?): Boolean = normalize(rawStatus) == COMPLETED

    fun isActive(rawStatus: String?): Boolean = normalize(rawStatus) in ACTIVE
}

