package com.example.anda.core.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileManagerPolicyTest {

    @Test
    fun lockoutDuration_shouldBeZero_beforeThirdAttempt() {
        assertEquals(0L, ProfileManager.lockoutDurationMsForAttemptCount(1))
        assertEquals(0L, ProfileManager.lockoutDurationMsForAttemptCount(2))
    }

    @Test
    fun lockoutDuration_shouldBeThirtySeconds_fromThirdToFourthAttempt() {
        assertEquals(30_000L, ProfileManager.lockoutDurationMsForAttemptCount(3))
        assertEquals(30_000L, ProfileManager.lockoutDurationMsForAttemptCount(4))
    }

    @Test
    fun lockoutDuration_shouldBeTwoMinutes_fromFifthToSixthAttempt() {
        assertEquals(120_000L, ProfileManager.lockoutDurationMsForAttemptCount(5))
        assertEquals(120_000L, ProfileManager.lockoutDurationMsForAttemptCount(6))
    }

    @Test
    fun lockoutDuration_shouldBeTenMinutes_fromSeventhAttemptOnward() {
        assertEquals(600_000L, ProfileManager.lockoutDurationMsForAttemptCount(7))
        assertEquals(600_000L, ProfileManager.lockoutDurationMsForAttemptCount(8))
    }
}

