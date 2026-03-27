package com.example.anda.feature.home

import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.anda.R
import com.example.anda.core.profile.ProfileManager
import com.example.anda.feature.onboarding.PinUnlockActivity

/**
 * Base class for all profile-gated home screens.
 *
 * Responsibilities:
 *  - Verify profile/PIN configuration exists; redirect to onboarding if not.
 *  - Check session timeout on every resume; redirect to PinUnlockActivity if expired.
 *  - Record every user interaction to keep the session alive.
 */
abstract class SecuredHomeActivity : AppCompatActivity() {

    override fun onResume() {
        super.onResume()
        enforceSession()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        ProfileManager.touchSession(this)
    }

    private fun enforceSession() {
        if (!ProfileManager.isFullyConfigured(this)) {
            redirectToOnboarding()
            return
        }
        if (ProfileManager.isSessionExpired(this)) {
            Toast.makeText(this, getString(R.string.session_timeout_message), Toast.LENGTH_SHORT).show()
            startActivity(
                Intent(this, PinUnlockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finish()
        }
    }

    private fun redirectToOnboarding() {
        val intent = Intent(this, com.example.anda.feature.onboarding.ProfileSelectionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }
}

