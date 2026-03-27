package com.example.anda.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingRouteResolverTest {

    @Test
    fun resolve_shouldReturnProfileSelection_whenNoProfileAndNoPin() {
        val destination = OnboardingRouteResolver.resolve(hasProfile = false, hasPin = false)
        assertEquals(OnboardingDestination.PROFILE_SELECTION, destination)
    }

    @Test
    fun resolve_shouldReturnPinSetup_whenHasProfileWithoutPin() {
        val destination = OnboardingRouteResolver.resolve(hasProfile = true, hasPin = false)
        assertEquals(OnboardingDestination.PIN_SETUP, destination)
    }

    @Test
    fun resolve_shouldReturnPinUnlock_whenHasProfileAndPin() {
        val destination = OnboardingRouteResolver.resolve(hasProfile = true, hasPin = true)
        assertEquals(OnboardingDestination.PIN_UNLOCK, destination)
    }
}

