package com.example.anda.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the exponential-backoff retry-delay formula in [SyncQueueRepository].
 *
 * Formula: `5_000 ms × 2^min(attempts, 6)`
 *
 * Tests verify:
 *  - Each attempt in the ramp-up range doubles the previous delay.
 *  - Attempts ≥ 6 are capped at the maximum (320 s).
 *  - Boundary cases (attempt 0, negative, very large).
 */
class SyncBackoffPolicyTest {

    // ── Ramp-up table ──────────────────────────────────────────────────────────

    @Test
    fun `attempt 1 yields 10 seconds`() {
        assertEquals(10_000L, SyncQueueRepository.computeRetryDelayMs(1))
    }

    @Test
    fun `attempt 2 yields 20 seconds`() {
        assertEquals(20_000L, SyncQueueRepository.computeRetryDelayMs(2))
    }

    @Test
    fun `attempt 3 yields 40 seconds`() {
        assertEquals(40_000L, SyncQueueRepository.computeRetryDelayMs(3))
    }

    @Test
    fun `attempt 4 yields 80 seconds`() {
        assertEquals(80_000L, SyncQueueRepository.computeRetryDelayMs(4))
    }

    @Test
    fun `attempt 5 yields 160 seconds`() {
        assertEquals(160_000L, SyncQueueRepository.computeRetryDelayMs(5))
    }

    @Test
    fun `attempt 6 yields cap of 320 seconds`() {
        assertEquals(320_000L, SyncQueueRepository.computeRetryDelayMs(6))
    }

    // ── Cap enforcement ────────────────────────────────────────────────────────

    @Test
    fun `attempt 7 is still capped at 320 seconds`() {
        assertEquals(320_000L, SyncQueueRepository.computeRetryDelayMs(7))
    }

    @Test
    fun `attempt 100 is still capped at 320 seconds`() {
        assertEquals(320_000L, SyncQueueRepository.computeRetryDelayMs(100))
    }

    @Test
    fun `attempt Int_MAX_VALUE is still capped at 320 seconds`() {
        assertEquals(320_000L, SyncQueueRepository.computeRetryDelayMs(Int.MAX_VALUE))
    }

    // ── Lower boundary ─────────────────────────────────────────────────────────

    @Test
    fun `attempt 0 is clamped up to attempt 1 - delay is 10 seconds`() {
        // coerceIn(1, 6) ensures we never shift by 0 (which would return BASE_DELAY × 1 = 5 s).
        // The minimum meaningful attempt count after a failure is 1.
        assertEquals(10_000L, SyncQueueRepository.computeRetryDelayMs(0))
    }

    @Test
    fun `negative attempt count is clamped to minimum delay`() {
        assertEquals(10_000L, SyncQueueRepository.computeRetryDelayMs(-99))
    }

    // ── Monotonicity ───────────────────────────────────────────────────────────

    @Test
    fun `delays are non-decreasing from attempt 1 to 10`() {
        val delays = (1..10).map { SyncQueueRepository.computeRetryDelayMs(it) }
        val nonDecreasing = delays.zipWithNext().all { (a, b) -> b >= a }
        assertTrue("Retry delays must be non-decreasing: $delays", nonDecreasing)
    }

    @Test
    fun `delays are strictly increasing from attempt 1 to 6 then flat`() {
        val rampUp   = (1..6).map { SyncQueueRepository.computeRetryDelayMs(it) }
        val flatPart = (6..9).map { SyncQueueRepository.computeRetryDelayMs(it) }

        assertTrue(
            "Delays 1..6 must be strictly increasing: $rampUp",
            rampUp.zipWithNext().all { (a, b) -> b > a }
        )
        assertTrue(
            "Delays 6..9 must be equal (cap): $flatPart",
            flatPart.all { it == 320_000L }
        )
    }

    // ── Delay is always positive ───────────────────────────────────────────────

    @Test
    fun `all delays for attempts 0 to 20 are positive`() {
        for (attempt in 0..20) {
            assertTrue(
                "Expected positive delay for attempt $attempt",
                SyncQueueRepository.computeRetryDelayMs(attempt) > 0L
            )
        }
    }
}


