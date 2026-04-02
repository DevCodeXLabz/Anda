package com.example.anda

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.anda.feature.onboarding.LgpdConsentActivity
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupSmokeTest {

    @Test
    fun launchLgpdConsent_noCrash() {
        // Launch the onboarding LGPD consent activity — if initialization throws, this test will fail.
        ActivityScenario.launch(LgpdConsentActivity::class.java).use { scenario ->
            // Basic assertion: activity reaches resumed state without crashing.
            // ActivityScenario will throw if the activity fails to start.
        }
    }
}

