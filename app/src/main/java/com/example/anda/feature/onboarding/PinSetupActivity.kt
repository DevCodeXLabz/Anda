package com.example.anda.feature.onboarding

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import com.example.anda.R
import com.example.anda.core.profile.ProfileManager
import com.example.anda.core.profile.UserProfile
import com.example.anda.databinding.ActivityPinSetupBinding

class PinSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPinSetupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val selectedProfile = resolveProfile() ?: run {
            startActivity(Intent(this, ProfileSelectionActivity::class.java))
            finish()
            return
        }

        binding = ActivityPinSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.profileText.text = getString(
            R.string.profile_setup_selected_profile_template,
            profileLabel(selectedProfile)
        )

        binding.continueButton.setOnClickListener {
            val pin = binding.pinInput.text?.toString().orEmpty().trim()
            val confirmPin = binding.confirmPinInput.text?.toString().orEmpty().trim()

            if (!ProfileManager.isValidPin(pin)) {
                Toast.makeText(
                    this,
                    getString(R.string.profile_pin_invalid_length),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (pin != confirmPin) {
                Toast.makeText(this, getString(R.string.profile_pin_mismatch), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            ProfileManager.saveProfile(this, selectedProfile)
            val saved = ProfileManager.setPin(this, pin)
            if (!saved) {
                Toast.makeText(this, getString(R.string.profile_pin_save_error), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            askBiometricPreferenceAndContinue(selectedProfile)
        }
    }

    private fun askBiometricPreferenceAndContinue(selectedProfile: UserProfile) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val available = BiometricManager.from(this)
            .canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

        if (!available) {
            ProfileManager.setBiometricEnabled(this, false)
            continueToHome(selectedProfile)
            return
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.profile_setup_biometric_opt_in_title))
            .setMessage(getString(R.string.profile_setup_biometric_opt_in_message))
            .setPositiveButton(getString(R.string.profile_setup_biometric_opt_in_enable)) { _, _ ->
                ProfileManager.setBiometricEnabled(this, true)
                continueToHome(selectedProfile)
            }
            .setNegativeButton(getString(R.string.profile_setup_biometric_opt_in_skip)) { _, _ ->
                ProfileManager.setBiometricEnabled(this, false)
                continueToHome(selectedProfile)
            }
            .setCancelable(false)
            .show()
    }

    private fun continueToHome(selectedProfile: UserProfile) {
        startActivity(
            ProfileNavigation.homeIntent(this, selectedProfile)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    private fun resolveProfile(): UserProfile? {
        val extra = intent.getStringExtra(EXTRA_PROFILE)
        return UserProfile.fromStorage(extra) ?: ProfileManager.getProfile(this)
    }

    private fun profileLabel(profile: UserProfile): String {
        return when (profile) {
            UserProfile.TECHNICIAN -> getString(R.string.profile_technician_title)
            UserProfile.PRESTADORA,
            UserProfile.COMPANY,
            UserProfile.CLINIC -> getString(R.string.profile_prestadora_title)
        }
    }

    companion object {
        const val EXTRA_PROFILE = "extra_profile"
    }
}

