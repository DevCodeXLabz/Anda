package com.example.anda.core.profile

object SessionPolicy {

    /** Re-authenticate after 5 minutes of inactivity. */
    const val SESSION_TIMEOUT_MS = 5L * 60_000L

    /**
     * Returns true if the user should be asked to unlock again.
     * @param lastActiveMs timestamp of the last recorded user activity, 0 = never recorded.
     * @param nowMs        current time in milliseconds.
     */
    fun isSessionExpired(lastActiveMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (lastActiveMs <= 0L) return true
        return (nowMs - lastActiveMs) >= SESSION_TIMEOUT_MS
    }

    fun remainingSessionMs(lastActiveMs: Long, nowMs: Long = System.currentTimeMillis()): Long {
        if (lastActiveMs <= 0L) return 0L
        return (SESSION_TIMEOUT_MS - (nowMs - lastActiveMs)).coerceAtLeast(0L)
    }
}

