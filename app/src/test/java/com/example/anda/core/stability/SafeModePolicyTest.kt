package com.example.anda.core.stability

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeModePolicyTest {

    @Test
    fun evaluate_shouldEnableSafeMode_whenCrashThresholdReachedInsideWindow() {
        val now = 1_000_000L
        val decision = SafeModePolicy.evaluate(
            windowStartAt = now - 10_000L,
            crashCountInWindow = 2,
            now = now
        )

        assertTrue(decision.shouldEnable)
        assertNotNull(decision.safeModeUntil)
    }

    @Test
    fun evaluate_shouldNotEnableSafeMode_whenWindowExpired() {
        val now = 1_000_000L
        val decision = SafeModePolicy.evaluate(
            windowStartAt = now - (2L * 60L * 60L * 1000L),
            crashCountInWindow = 4,
            now = now
        )

        assertFalse(decision.shouldEnable)
    }

    @Test
    fun isSafeModeStillActive_shouldReturnFalse_whenExpired() {
        val now = 1_000_000L
        assertFalse(SafeModePolicy.isSafeModeStillActive(safeModeUntil = now - 1L, now = now))
    }
}

