package com.example.anda.feature.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.anda.core.profile.ProfileManager
import com.example.anda.core.profile.UserProfile
import com.example.anda.databinding.ActivityProfileSelectionBinding

class ProfileSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileSelectionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ProfileManager.isFullyConfigured(this)) {
            startActivity(Intent(this, PinUnlockActivity::class.java))
            finish()
            return
        }

        binding = ActivityProfileSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.technicianCard.setOnClickListener {
            onProfileSelected(UserProfile.TECHNICIAN)
        }
        binding.companyCard.setOnClickListener {
            onProfileSelected(UserProfile.PRESTADORA)
        }
        binding.clinicCard.visibility = View.GONE
    }

    private fun onProfileSelected(profile: UserProfile) {
        ProfileManager.saveProfile(this, profile)
        startActivity(
            Intent(this, PinSetupActivity::class.java)
                .putExtra(PinSetupActivity.EXTRA_PROFILE, profile.storageValue)
        )
        finish()
    }
}

