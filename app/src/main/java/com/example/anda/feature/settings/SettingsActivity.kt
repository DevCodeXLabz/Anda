package com.example.anda.feature.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import com.example.anda.BuildConfig
import com.example.anda.R
import com.example.anda.core.profile.ProfileManager
import com.example.anda.core.profile.UserProfile
import com.example.anda.core.telemetry.CrashReporter
import com.example.anda.databinding.ActivitySettingsBinding
import com.example.anda.feature.onboarding.LgpdConsentActivity
import com.example.anda.feature.onboarding.ProfileSelectionActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        renderProfileSection()
        renderBiometricToggle()
        setupCrashlyticsConsent()
        setupChangePinFlow()
        setupSwitchProfile()
        renderAboutSection()
    }

    // ── Profile section ────────────────────────────────────────────

    private fun renderProfileSection() {
        val profile = ProfileManager.getProfile(this)
        binding.currentProfileText.text = profile?.let { profileLabel(it) } ?: "—"
        binding.displayNameInput.setText(ProfileManager.getDisplayName(this))

        binding.saveNameButton.setOnClickListener {
            val name = binding.displayNameInput.text?.toString().orEmpty().trim()
            ProfileManager.saveDisplayName(this, name)
            Toast.makeText(this, getString(R.string.settings_name_saved), Toast.LENGTH_SHORT).show()
        }
    }

    // ── Biometric toggle ────────────────────────────────────────────

    private fun renderBiometricToggle() {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val available = BiometricManager.from(this)
            .canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

        if (!available) {
            binding.biometricSwitch.isEnabled = false
            binding.biometricSwitch.isChecked = false
            return
        }

        binding.biometricSwitch.isChecked = ProfileManager.isBiometricEnabled(this)
        binding.biometricSwitch.setOnCheckedChangeListener { _, isChecked ->
            ProfileManager.setBiometricEnabled(this, isChecked)
            val msg = if (isChecked) R.string.settings_biometric_enabled_toast
                      else R.string.settings_biometric_disabled_toast
            Toast.makeText(this, getString(msg), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupCrashlyticsConsent() {
        binding.crashlyticsConsentSwitch.isChecked = CrashReporter.isOptIn(this)
        binding.crashlyticsConsentSwitch.setOnCheckedChangeListener { _, isChecked ->
            CrashReporter.setOptIn(this, isChecked)
            val msg = if (isChecked) {
                R.string.settings_crashlytics_enabled_toast
            } else {
                R.string.settings_crashlytics_disabled_toast
            }
            Toast.makeText(this, getString(msg), Toast.LENGTH_SHORT).show()
        }
    }

    // ── Change PIN ─────────────────────────────────────────────────

    private fun setupChangePinFlow() {
        binding.changePinButton.setOnClickListener {
            val visible = binding.changePinSection.visibility == View.VISIBLE
            binding.changePinSection.visibility = if (visible) View.GONE else View.VISIBLE
            binding.changePinButton.text = getString(
                if (visible) R.string.settings_change_pin_button
                else R.string.settings_change_pin_cancel_label
            )
        }

        binding.cancelChangePinButton.setOnClickListener {
            collapseChangePinSection()
        }

        binding.confirmChangePinButton.setOnClickListener {
            commitPinChange()
        }
    }

    private fun commitPinChange() {
        val currentPin = binding.currentPinInput.text?.toString().orEmpty().trim()
        val newPin     = binding.newPinInput.text?.toString().orEmpty().trim()
        val confirmPin = binding.confirmNewPinInput.text?.toString().orEmpty().trim()

        if (!ProfileManager.verifyPin(this, currentPin)) {
            Toast.makeText(this, getString(R.string.settings_pin_current_wrong), Toast.LENGTH_SHORT).show()
            return
        }
        if (!ProfileManager.isValidPin(newPin)) {
            Toast.makeText(this, getString(R.string.profile_pin_invalid_length), Toast.LENGTH_SHORT).show()
            return
        }
        if (newPin != confirmPin) {
            Toast.makeText(this, getString(R.string.profile_pin_mismatch), Toast.LENGTH_SHORT).show()
            return
        }
        ProfileManager.setPin(this, newPin)
        collapseChangePinSection()
        Toast.makeText(this, getString(R.string.settings_pin_changed_toast), Toast.LENGTH_SHORT).show()
    }

    private fun collapseChangePinSection() {
        binding.changePinSection.visibility = View.GONE
        binding.changePinButton.text = getString(R.string.settings_change_pin_button)
        binding.currentPinInput.text?.clear()
        binding.newPinInput.text?.clear()
        binding.confirmNewPinInput.text?.clear()
    }

    // ── Switch profile ─────────────────────────────────────────────

    private fun setupSwitchProfile() {
        binding.switchProfileButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.settings_switch_profile_confirm_title))
                .setMessage(getString(R.string.settings_switch_profile_confirm_message))
                .setPositiveButton(getString(R.string.settings_switch_profile_confirm_ok)) { _, _ ->
                    ProfileManager.clearAll(this)
                    startActivity(
                        Intent(this, ProfileSelectionActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                    finish()
                }
                .setNegativeButton(getString(R.string.settings_cancel_button), null)
                .show()
        }
    }

    private fun profileLabel(profile: UserProfile): String = when (profile) {
        UserProfile.TECHNICIAN -> getString(R.string.profile_technician_title)
        UserProfile.PRESTADORA -> getString(R.string.profile_prestadora_title)
        UserProfile.COMPANY    -> getString(R.string.profile_company_title)
        UserProfile.CLINIC     -> getString(R.string.profile_clinic_title)
    }

    // ── About / version section ─────────────────────────────────────

    private fun renderAboutSection() {
        val version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        binding.appVersionText.text = version

        binding.privacyPolicyButton.setOnClickListener {
            startActivity(Intent(this, LgpdConsentActivity::class.java))
        }
    }
}

