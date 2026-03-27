package com.example.anda.data.requests

/**
 * Central lifecycle rules for request status transitions driven by document events.
 */
object ServiceRequestLifecyclePolicy {

    enum class DocumentEvent {
        DRAFT_SAVED,
        SIGNED,
        PDF_EXPORTED
    }

    fun nextStatus(currentStatus: String, event: DocumentEvent): String {
        val normalized = ServiceRequestStatus.normalize(currentStatus)
        return transitionFrom(normalized, event)
    }

    private fun transitionFrom(current: String, event: DocumentEvent): String {
        return when (event) {
            DocumentEvent.DRAFT_SAVED -> when (current) {
                ServiceRequestStatus.OPEN,
                ServiceRequestStatus.ASSIGNED -> ServiceRequestStatus.IN_PROGRESS
                else -> current
            }

            DocumentEvent.SIGNED,
            DocumentEvent.PDF_EXPORTED -> ServiceRequestStatus.COMPLETED
        }
    }
}

