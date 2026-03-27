package com.example.anda.data.requests

import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceRequestLifecyclePolicyExtraTest {

    @Test
    fun `draft with lowercase open moves to in_progress`() {
        val next = ServiceRequestLifecyclePolicy.nextStatus(
            "open",
            ServiceRequestLifecyclePolicy.DocumentEvent.DRAFT_SAVED
        )
        assertEquals(ServiceRequestStatus.IN_PROGRESS, next)
    }

    @Test
    fun `draft with spaces and done alias stays completed`() {
        val next = ServiceRequestLifecyclePolicy.nextStatus(
            "  DONE  ",
            ServiceRequestLifecyclePolicy.DocumentEvent.DRAFT_SAVED
        )
        assertEquals(ServiceRequestStatus.COMPLETED, next)
    }

    @Test
    fun `signed with garbage status finalizes`() {
        val next = ServiceRequestLifecyclePolicy.nextStatus(
            "\tunknown_status\n",
            ServiceRequestLifecyclePolicy.DocumentEvent.SIGNED
        )
        assertEquals(ServiceRequestStatus.COMPLETED, next)
    }
}


