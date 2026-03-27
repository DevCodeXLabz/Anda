package com.example.anda.feature.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.anda.R
import com.example.anda.core.profile.ProfileManager
import com.example.anda.core.profile.UserProfile
import com.example.anda.databinding.ActivityPinUnlockBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PinUnlockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPinUnlockBinding
    private var lockoutTicker: Job? = null
    private var hasBiometricAvailable = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!ProfileManager.isFullyConfigured(this)) {
            startActivity(Intent(this, ProfileSelectionActivity::class.java))
            finish()
            return
        }

        val selectedProfile = ProfileManager.getProfile(this) ?: run {
            startActivity(Intent(this, ProfileSelectionActivity::class.java))
            finish()
            return
        }
        binding = ActivityPinUnlockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.profileText.text = getString(
            R.string.profile_unlock_profile_template,
            profileLabel(selectedProfile)
        )

        setupBiometricUnlock(selectedProfile)
        renderLockState()

        binding.unlockButton.setOnClickListener {
            if (ProfileManager.isLockedOut(this)) {
                renderLockState()
                return@setOnClickListener
            }

            val pin = binding.pinInput.text?.toString().orEmpty().trim()
            val isValid = ProfileManager.verifyPin(this, pin)
            if (!isValid) {
                val remainingLockoutMs = ProfileManager.recordFailedUnlockAttempt(this)
                if (remainingLockoutMs > 0L) {
                    renderLockState()
                } else {
                    Toast.makeText(this, getString(R.string.profile_unlock_invalid_pin), Toast.LENGTH_SHORT).show()
                }
                return@setOnClickListener
            }

            onUnlockSuccess(selectedProfile)
        }

        binding.switchProfileButton.setOnClickListener {
            ProfileManager.clearAll(this)
            startActivity(
                Intent(this, ProfileSelectionActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        startLockoutTicker()
    }

    override fun onStop() {
        lockoutTicker?.cancel()
        lockoutTicker = null
        super.onStop()
    }

    private fun setupBiometricUnlock(selectedProfile: UserProfile) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val hardwareAvailable = BiometricManager.from(this)
            .canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
        val preferenceEnabled = ProfileManager.isBiometricEnabled(this)
        hasBiometricAvailable = hardwareAvailable && preferenceEnabled

        if (!hardwareAvailable || !preferenceEnabled) {
            binding.biometricUnlockButton.visibility = View.GONE
            return
        }

        binding.biometricUnlockButton.setOnClickListener {
            if (ProfileManager.isLockedOut(this)) {
                renderLockState()
                return@setOnClickListener
            }

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.profile_unlock_biometric_title))
                .setSubtitle(getString(R.string.profile_unlock_biometric_subtitle))
                .setDescription(getString(R.string.profile_unlock_biometric_description))
                .setAllowedAuthenticators(authenticators)
                .build()

            val prompt = BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onUnlockSuccess(selectedProfile)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                            errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_CANCELED
                        ) {
                            return
                        }
                        Toast.makeText(
                            this@PinUnlockActivity,
                            errString.toString(),
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    override fun onAuthenticationFailed() {
                        Toast.makeText(
                            this@PinUnlockActivity,
                            getString(R.string.profile_unlock_biometric_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )

            prompt.authenticate(promptInfo)
        }
    }

    private fun startLockoutTicker() {
        lockoutTicker?.cancel()
        lockoutTicker = lifecycleScope.launch {
            while (isActive) {
                renderLockState()
                delay(1000)
            }
        }
    }

    private fun renderLockState() {
        val remainingMs = ProfileManager.remainingLockoutMs(this)
        val isLocked = remainingMs > 0L

        binding.unlockButton.isEnabled = !isLocked
        binding.biometricUnlockButton.isEnabled = !isLocked

        if (!isLocked) {
            binding.unlockStatusText.visibility = View.VISIBLE
            binding.unlockStatusText.text = if (hasBiometricAvailable) {
                getString(R.string.profile_unlock_status_biometric_enabled)
            } else {
                getString(R.string.profile_unlock_status_pin_only)
            }
            binding.unlockStatusText.setTextColor(ContextCompat.getColor(this, R.color.status_neutral))
            return
        }

        binding.unlockStatusText.visibility = View.VISIBLE
        binding.unlockStatusText.text = getString(
            R.string.profile_unlock_locked_template,
            formatRemaining(remainingMs)
        )
    }

    private fun formatRemaining(remainingMs: Long): String {
        val totalSeconds = (remainingMs / 1000L).coerceAtLeast(1L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return if (minutes > 0L) {
            String.format("%dm %02ds", minutes, seconds)
        } else {
            String.format("%ds", seconds)
        }
    }

    private fun onUnlockSuccess(selectedProfile: UserProfile) {
        ProfileManager.resetUnlockFailures(this)
        startActivity(
            ProfileNavigation.homeIntent(this, selectedProfile)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    private fun profileLabel(profile: UserProfile): String {
        return when (profile) {
            UserProfile.TECHNICIAN -> getString(R.string.profile_technician_title)
            UserProfile.PRESTADORA,
            UserProfile.COMPANY,
            UserProfile.CLINIC -> getString(R.string.profile_prestadora_title)
        }
    }
}

