package com.example.anda.core.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPolicyTest {

    private val timeout = SessionPolicy.SESSION_TIMEOUT_MS

    @Test
    fun isSessionExpired_shouldReturnTrue_whenLastActiveMsIsZero() {
        assertTrue(SessionPolicy.isSessionExpired(lastActiveMs = 0L, nowMs = 1_000_000L))
    }

    @Test
    fun isSessionExpired_shouldReturnFalse_whenActiveJustNow() {
        val now = 1_000_000L
        assertFalse(SessionPolicy.isSessionExpired(lastActiveMs = now - 1_000L, nowMs = now))
    }

    @Test
    fun isSessionExpired_shouldReturnFalse_oneSecondBeforeTimeout() {
        val now = 1_000_000L
        assertFalse(
            SessionPolicy.isSessionExpired(
                lastActiveMs = now - timeout + 1_000L,
                nowMs = now
            )
        )
    }

    @Test
    fun isSessionExpired_shouldReturnTrue_exactlyAtTimeout() {
        val now = 1_000_000L
        assertTrue(
            SessionPolicy.isSessionExpired(
                lastActiveMs = now - timeout,
                nowMs = now
            )
        )
    }

    @Test
    fun isSessionExpired_shouldReturnTrue_afterTimeout() {
        val now = 1_000_000L
        assertTrue(
            SessionPolicy.isSessionExpired(
                lastActiveMs = now - timeout - 1L,
                nowMs = now
            )
        )
    }

    @Test
    fun remainingSessionMs_shouldBeZero_whenLastActiveMsIsZero() {
        assertEquals(0L, SessionPolicy.remainingSessionMs(lastActiveMs = 0L, nowMs = 1_000_000L))
    }

    @Test
    fun remainingSessionMs_shouldReturnCorrectValue_beforeTimeout() {
        val now = 1_000_000L
        val elapsed = 60_000L
        val expected = timeout - elapsed
        assertEquals(expected, SessionPolicy.remainingSessionMs(lastActiveMs = now - elapsed, nowMs = now))
    }

    @Test
    fun remainingSessionMs_shouldBeZero_afterTimeout() {
        val now = 1_000_000L
        assertEquals(0L, SessionPolicy.remainingSessionMs(lastActiveMs = now - timeout - 1L, nowMs = now))
    }
}

