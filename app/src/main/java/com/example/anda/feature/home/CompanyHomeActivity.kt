package com.example.anda.feature.home

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.anda.R
import com.example.anda.core.profile.ProfileManager
import com.example.anda.data.repository.DocumentLocalRepository
import com.example.anda.data.local.AppDatabase
import com.example.anda.data.requests.ServiceRequestOrchestrator
import com.example.anda.databinding.ActivityCompanyHomeBinding
import com.example.anda.feature.companies.CompanyManagementActivity
import com.example.anda.feature.documents.DocumentsActivity
import com.example.anda.feature.documents.GenericSstDocumentActivity
import com.example.anda.feature.employees.EmployeeManagementActivity
import com.example.anda.feature.epi.EpiManagementActivity
import com.example.anda.feature.onboarding.PinUnlockActivity
import com.example.anda.feature.os.OsActivity
import com.example.anda.feature.scanner.CaScannerActivity
import com.example.anda.feature.settings.SettingsActivity
import com.example.anda.feature.requests.ServiceRequestsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CompanyHomeActivity : SecuredHomeActivity() {

    private lateinit var binding: ActivityCompanyHomeBinding
    private lateinit var orchestrator: ServiceRequestOrchestrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DocumentLocalRepository.initialize(this)

        binding = ActivityCompanyHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize service request orchestrator
        val db = AppDatabase.getInstance(this)
        orchestrator = ServiceRequestOrchestrator(this, db)

        renderPersonalizedHeader()

        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // ── Documentos SST ──────────────────────────────────────────────
        binding.openPgrButton.setOnClickListener {
            openGenericDocument("PGR", "PGR - Gerenciamento de Riscos", "NR-01")
        }
        binding.openAprButton.setOnClickListener {
            openGenericDocument("APR", "APR - Analise Preliminar de Risco", "Controle operacional")
        }
        binding.openCatButton.setOnClickListener {
            openGenericDocument("CAT", "CAT - Acidente do Trabalho", "Comunicacao oficial")
        }
        binding.openOsButton.setOnClickListener {
            startActivity(Intent(this, OsActivity::class.java))
        }

        // ── Laudos e Auditorias NR ──────────────────────────────────────
        binding.openNr10Button.setOnClickListener {
            openGenericDocument("LAUDO_NR10", "Laudo NR-10", "Seguranca em instalacoes eletricas")
        }
        binding.openNr12Button.setOnClickListener {
            openGenericDocument("LAUDO_NR12", "Laudo NR-12", "Seguranca em maquinas e equipamentos")
        }
        binding.openNr20Button.setOnClickListener {
            openGenericDocument("LAUDO_NR20", "Laudo NR-20", "Inflamaveis e combustiveis")
        }
        binding.openLtcatButton.setOnClickListener {
            openGenericDocument("LTCAT", "LTCAT - Laudo Tecnico de Condicoes Ambientais", "INSS / Previdenciario")
        }
        binding.openAetButton.setOnClickListener {
            openGenericDocument("AET", "AET - Analise Ergonomica do Trabalho", "NR-17")
        }

        // ── Laudos Especializados ───────────────────────────────────────
        binding.openInsalubridadeButton.setOnClickListener {
            openGenericDocument("INSALUBRIDADE", "Laudo de Insalubridade", "NR-15")
        }
        binding.openPericulosidadeButton.setOnClickListener {
            openGenericDocument("PERICULOSIDADE", "Laudo de Periculosidade", "NR-16")
        }
        binding.openPtButton.setOnClickListener {
            openGenericDocument("PT", "PT - Permissao de Trabalho", "Controle operacional")
        }

        // ── Programas SST ───────────────────────────────────────────────
        binding.openPcaButton.setOnClickListener {
            openGenericDocument("PCA", "PCA - Programa de Conservacao Auditiva", "Saude ocupacional")
        }
        binding.openPprButton.setOnClickListener {
            openGenericDocument("PPR", "PPR - Programa de Protecao Respiratoria", "Protecao respiratoria")
        }
        binding.openPlanoAcaoButton.setOnClickListener {
            openGenericDocument("PLANO_ACAO", "Plano de Acao SST", "Gestao corretiva e preventiva")
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

        // ── Service Requests ────────────────────────────────────────────
        binding.openServiceRequestsButton.setOnClickListener {
            startActivity(Intent(this, ServiceRequestsActivity::class.java))
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
            getString(R.string.company_home_subtitle)
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
