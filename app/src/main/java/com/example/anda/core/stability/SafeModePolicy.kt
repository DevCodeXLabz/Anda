package com.example.anda.core.stability

data class SafeModeDecision(
    val shouldEnable: Boolean,
    val safeModeUntil: Long?
)

object SafeModePolicy {

    private const val CRASH_WINDOW_MS = 60L * 60L * 1000L
    private const val SAFE_MODE_DURATION_MS = 30L * 60L * 1000L
    private const val CRASH_THRESHOLD = 2

    fun evaluate(
        windowStartAt: Long,
        crashCountInWindow: Int,
        now: Long = System.currentTimeMillis()
    ): SafeModeDecision {
        if (crashCountInWindow >= CRASH_THRESHOLD && now - windowStartAt <= CRASH_WINDOW_MS) {
            return SafeModeDecision(
                shouldEnable = true,
                safeModeUntil = now + SAFE_MODE_DURATION_MS
            )
        }
        return SafeModeDecision(
            shouldEnable = false,
            safeModeUntil = null
        )
    }

    fun isSafeModeStillActive(safeModeUntil: Long, now: Long = System.currentTimeMillis()): Boolean {
        return safeModeUntil > now
    }
}

