package com.example.anda.data.requests

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceRequestStatusTest {

    @Test
    fun `normalizes legacy done to completed`() {
        assertEquals(ServiceRequestStatus.COMPLETED, ServiceRequestStatus.normalize("DONE"))
        assertTrue(ServiceRequestStatus.isCompleted("DONE"))
    }

    @Test
    fun `recognizes active states consistently`() {
        assertTrue(ServiceRequestStatus.isActive(ServiceRequestStatus.ASSIGNED))
        assertTrue(ServiceRequestStatus.isActive(ServiceRequestStatus.IN_PROGRESS))
        assertFalse(ServiceRequestStatus.isActive(ServiceRequestStatus.OPEN))
        assertFalse(ServiceRequestStatus.isActive(ServiceRequestStatus.COMPLETED))
    }

    @Test
    fun `falls back unknown states to open`() {
        assertEquals(ServiceRequestStatus.OPEN, ServiceRequestStatus.normalize("unexpected"))
    }
}

