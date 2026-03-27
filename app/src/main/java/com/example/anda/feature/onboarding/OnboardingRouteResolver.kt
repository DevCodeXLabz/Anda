package com.example.anda.feature.onboarding

enum class OnboardingDestination {
    PROFILE_SELECTION,
    PIN_SETUP,
    PIN_UNLOCK
}

object OnboardingRouteResolver {

    fun resolve(hasProfile: Boolean, hasPin: Boolean): OnboardingDestination {
        return when {
            hasProfile && hasPin -> OnboardingDestination.PIN_UNLOCK
            hasProfile -> OnboardingDestination.PIN_SETUP
            else -> OnboardingDestination.PROFILE_SELECTION
        }
    }
}

