package com.example.anda.feature.home

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.example.anda.R
import com.example.anda.core.profile.ProfileManager
import com.example.anda.data.repository.DocumentLocalRepository
import com.example.anda.databinding.ActivityClinicHomeBinding
import com.example.anda.feature.aso.AsoActivity
import com.example.anda.feature.companies.CompanyManagementActivity
import com.example.anda.feature.documents.DocumentsActivity
import com.example.anda.feature.documents.GenericSstDocumentActivity
import com.example.anda.feature.employees.EmployeeManagementActivity
import com.example.anda.feature.epi.EpiManagementActivity
import com.example.anda.feature.onboarding.PinUnlockActivity
import com.example.anda.feature.pcmso.PcmsoActivity
import com.example.anda.feature.ppp.PppActivity
import com.example.anda.feature.scanner.CaScannerActivity
import com.example.anda.feature.settings.SettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ClinicHomeActivity : SecuredHomeActivity() {

    private lateinit var binding: ActivityClinicHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DocumentLocalRepository.initialize(this)

        binding = ActivityClinicHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        renderPersonalizedHeader()

        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // ── Medicina do Trabalho ────────────────────────────────────────
        binding.openAsoButton.setOnClickListener {
            startActivity(Intent(this, AsoActivity::class.java))
        }
        binding.openPcmsoButton.setOnClickListener {
            startActivity(Intent(this, PcmsoActivity::class.java))
        }
        binding.openPppButton.setOnClickListener {
            startActivity(Intent(this, PppActivity::class.java))
        }

        // ── Análises e Laudos ───────────────────────────────────────────
        binding.openAetButton.setOnClickListener {
            openGenericDocument("AET", "AET - Analise Ergonomica do Trabalho", "NR-17")
        }
        binding.openLtcatButton.setOnClickListener {
            openGenericDocument("LTCAT", "LTCAT - Laudo Tecnico de Condicoes Ambientais", "INSS / Previdenciario")
        }

        // ── Ferramentas ─────────────────────────────────────────────────
        binding.openScannerButton.setOnClickListener {
            startActivity(Intent(this, CaScannerActivity::class.java))
        }
        binding.openEpiButton.setOnClickListener {
            startActivity(Intent(this, EpiManagementActivity::class.java))
        }
        binding.openEmployeesButton.setOnClickListener {
            startActivity(Intent(this, EmployeeManagementActivity::class.java))
        }
        binding.openCompaniesButton.setOnClickListener {
            startActivity(Intent(this, CompanyManagementActivity::class.java))
        }
        binding.openDocumentsButton.setOnClickListener {
            startActivity(Intent(this, DocumentsActivity::class.java))
        }

        binding.lockButton.setOnClickListener {
            startActivity(
                Intent(this, PinUnlockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finish()
        }

        startStatsRefresh()
    }

    override fun onResume() {
        super.onResume()
        renderPersonalizedHeader()
    }

    private fun renderPersonalizedHeader() {
        val name = ProfileManager.getDisplayName(this)
        binding.subtitleText.text = if (name.isNotBlank()) {
            getString(R.string.home_welcome_name_template, name)
        } else {
            getString(R.string.clinic_home_subtitle)
        }
    }

    private fun startStatsRefresh() {
        lifecycleScope.launch {
            while (isActive) {
                val summary = withContext(Dispatchers.IO) {
                    runCatching { DocumentLocalRepository.loadOperationalSummary() }.getOrNull()
                }
                if (summary != null) {
                    binding.opsSummaryText.text = getString(
                        R.string.home_ops_summary_template,
                        summary.totalDocuments,
                        summary.pendingSync,
                        summary.failedSync,
                        summary.signedToday,
                        summary.expiringIn30Days,
                        summary.latestDocumentId ?: "-"
                    )
                }
                delay(5_000)
            }
        }
    }

    private fun openGenericDocument(type: String, title: String, subtitle: String) {
        startActivity(
            Intent(this, GenericSstDocumentActivity::class.java)
                .putExtra(GenericSstDocumentActivity.EXTRA_DOCUMENT_TYPE, type)
                .putExtra(GenericSstDocumentActivity.EXTRA_DOCUMENT_TITLE, title)
                .putExtra(GenericSstDocumentActivity.EXTRA_DOCUMENT_SUBTITLE, subtitle)
        )
    }
}
