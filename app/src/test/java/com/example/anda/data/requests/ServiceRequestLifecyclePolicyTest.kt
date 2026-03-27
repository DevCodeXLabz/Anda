package com.example.anda.data.requests

import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceRequestLifecyclePolicyTest {

    @Test
    fun `draft moves open request to in progress`() {
        val next = ServiceRequestLifecyclePolicy.nextStatus(
            ServiceRequestStatus.OPEN,
            ServiceRequestLifecyclePolicy.DocumentEvent.DRAFT_SAVED
        )
        assertEquals(ServiceRequestStatus.IN_PROGRESS, next)
    }

    @Test
    fun `draft keeps completed request completed`() {
        val next = ServiceRequestLifecyclePolicy.nextStatus(
            ServiceRequestStatus.COMPLETED,
            ServiceRequestLifecyclePolicy.DocumentEvent.DRAFT_SAVED
        )
        assertEquals(ServiceRequestStatus.COMPLETED, next)
    }

    @Test
    fun `signed finalizes request`() {
        val next = ServiceRequestLifecyclePolicy.nextStatus(
            ServiceRequestStatus.IN_PROGRESS,
            ServiceRequestLifecyclePolicy.DocumentEvent.SIGNED
        )
        assertEquals(ServiceRequestStatus.COMPLETED, next)
    }

    @Test
    fun `pdf exported also finalizes request`() {
        val next = ServiceRequestLifecyclePolicy.nextStatus(
            ServiceRequestStatus.ASSIGNED,
            ServiceRequestLifecyclePolicy.DocumentEvent.PDF_EXPORTED
        )
        assertEquals(ServiceRequestStatus.COMPLETED, next)
    }

    @Test
    fun `pdf export keeps completed request completed`() {
        val next = ServiceRequestLifecyclePolicy.nextStatus(
            ServiceRequestStatus.COMPLETED,
            ServiceRequestLifecyclePolicy.DocumentEvent.PDF_EXPORTED
        )
        assertEquals(ServiceRequestStatus.COMPLETED, next)
    }

    // ── full event × status matrix ────────────────────────────────────────

    @Test
    fun `draft on assigned moves to in progress`() {
        val next = ServiceRequestLifecyclePolicy.nextStatus(
            ServiceRequestStatus.ASSIGNED,
            ServiceRequestLifecyclePolicy.DocumentEvent.DRAFT_SAVED
        )
        assertEquals(ServiceRequestStatus.IN_PROGRESS, next)
    }

    @Test
    fun `draft on in_progress keeps in_progress`() {
        val next = ServiceRequestLifecyclePolicy.nextStatus(
            ServiceRequestStatus.IN_PROGRESS,
            ServiceRequestLifecyclePolicy.DocumentEvent.DRAFT_SAVED
        )
        assertEquals(ServiceRequestStatus.IN_PROGRESS, next)
    }

    @Test
    fun `signed on open request finalizes immediately`() {
        val next = ServiceRequestLifecyclePolicy.nextStatus(
            ServiceRequestStatus.OPEN,
            ServiceRequestLifecyclePolicy.DocumentEvent.SIGNED
        )
        assertEquals(ServiceRequestStatus.COMPLETED, next)
    }

    @Test
    fun `signed on completed stays completed`() {
        val next = ServiceRequestLifecyclePolicy.nextStatus(
            ServiceRequestStatus.COMPLETED,
            ServiceRequestLifecyclePolicy.DocumentEvent.SIGNED
        )
        assertEquals(ServiceRequestStatus.COMPLETED, next)
    }

    @Test
    fun `pdf export on open finalizes`() {
        val next = ServiceRequestLifecyclePolicy.nextStatus(
            ServiceRequestStatus.OPEN,
            ServiceRequestLifecyclePolicy.DocumentEvent.PDF_EXPORTED
        )
        assertEquals(ServiceRequestStatus.COMPLETED, next)
    }

    @Test
    fun `pdf export on in_progress finalizes`() {
        val next = ServiceRequestLifecyclePolicy.nextStatus(
            ServiceRequestStatus.IN_PROGRESS,
            ServiceRequestLifecyclePolicy.DocumentEvent.PDF_EXPORTED
        )
        assertEquals(ServiceRequestStatus.COMPLETED, next)
    }

    // ── legacy alias ──────────────────────────────────────────────────────

    @Test
    fun `legacy DONE alias is treated as completed for draft event`() {
        val next = ServiceRequestLifecyclePolicy.nextStatus(
            "DONE",
            ServiceRequestLifecyclePolicy.DocumentEvent.DRAFT_SAVED
        )
        // DONE normalizes to COMPLETED; draft on COMPLETED stays COMPLETED
        assertEquals(ServiceRequestStatus.COMPLETED, next)
    }

    @Test
    fun `legacy DONE alias is treated as completed for signed event`() {
        val next = ServiceRequestLifecyclePolicy.nextStatus(
            "DONE",
            ServiceRequestLifecyclePolicy.DocumentEvent.SIGNED
        )
        assertEquals(ServiceRequestStatus.COMPLETED, next)
    }

    // ── unknown status fallback ───────────────────────────────────────────

    @Test
    fun `unknown status with draft event treats as open and moves to in_progress`() {
        val next = ServiceRequestLifecyclePolicy.nextStatus(
            "GARBAGE_STATUS",
            ServiceRequestLifecyclePolicy.DocumentEvent.DRAFT_SAVED
        )
        // normalize("GARBAGE_STATUS") → OPEN → DRAFT_SAVED → IN_PROGRESS
        assertEquals(ServiceRequestStatus.IN_PROGRESS, next)
    }
}
