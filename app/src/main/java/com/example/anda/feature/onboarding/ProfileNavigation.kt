package com.example.anda.feature.onboarding

import android.content.Context
import android.content.Intent
import com.example.anda.core.profile.UserProfile
import com.example.anda.feature.home.CompanyHomeActivity
import com.example.anda.feature.home.TechnicianHomeActivity

object ProfileNavigation {

    fun homeIntent(context: Context, profile: UserProfile): Intent {
        val destination = when (profile) {
            UserProfile.TECHNICIAN -> TechnicianHomeActivity::class.java
            UserProfile.PRESTADORA,
            UserProfile.COMPANY,
            UserProfile.CLINIC -> CompanyHomeActivity::class.java
        }
        return Intent(context, destination)
    }
}

