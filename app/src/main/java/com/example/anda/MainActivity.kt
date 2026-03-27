package com.example.anda

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.anda.data.repository.DocumentLocalRepository
import com.example.anda.core.stability.CrashShield
import com.example.anda.databinding.ActivityMainBinding
import com.example.anda.feature.aso.AsoActivity
import com.example.anda.feature.apr.AprActivity
import com.example.anda.feature.cat.CatActivity
import com.example.anda.feature.consent.CrashReporterConsentDialog
import com.example.anda.feature.diagnostics.DiagnosticsActivity
import com.example.anda.feature.documents.DocumentDetailActivity
import com.example.anda.feature.documents.DocumentsActivity
import com.example.anda.feature.documents.GenericSstDocumentActivity
import com.example.anda.feature.home.SecuredHomeActivity
import com.example.anda.feature.os.OsActivity
import com.example.anda.feature.pcmso.PcmsoActivity
import com.example.anda.feature.pgr.PgrActivity
import com.example.anda.feature.ppp.PppActivity
import com.example.anda.feature.scanner.CaScannerActivity
import com.example.anda.feature.epi.EpiManagementActivity
import com.example.anda.feature.employees.EmployeeManagementActivity
import com.example.anda.feature.settings.SettingsActivity
import com.example.anda.core.profile.ProfileManager
import com.example.anda.presentation.BackendFallbackLevel
import com.example.anda.presentation.MainViewModel
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : SecuredHomeActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private val uiErrorHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("MainActivity", "Erro na coleta de estado da UI", throwable)
        showRecoverableError(throwable)
    }
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.forLanguageTag("pt-BR"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { initializeUi() }
            .onFailure {
                Log.e("MainActivity", "Falha ao inicializar tela principal", it)
                showRecoverableError(it)
            }

        // Show Crashlytics consent dialog on first app launch
        // (when user hasn't made a choice yet)
        showCrashlyticsConsentIfNeeded()
    }

    override fun onStart() {
        super.onStart()
        runCatching {
            viewModel.startBackendHealthMonitoring()
        }.onFailure {
            CrashShield.recordRecoverableError("MainActivity/onStart", it)
            showRecoverableError(it)
        }
    }

    override fun onResume() {
        super.onResume()
        renderPersonalizedSubtitle()
    }

    override fun onStop() {
        runCatching {
            viewModel.stopBackendHealthMonitoring()
        }.onFailure {
            CrashShield.recordRecoverableError("MainActivity/onStop", it)
        }
        super.onStop()
    }

    private fun initializeUi() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        runCatching { DocumentLocalRepository.initialize(this) }
            .onFailure { CrashShield.recordRecoverableError("MainActivity/DocumentLocalRepository.initialize", it) }

        CrashShield.consumeLastFatalReport(this)?.let {
            binding.statusText.text = "Recuperado apos falha anterior. Operacao em modo seguro."
            Toast.makeText(this, "Falha anterior detectada e registrada", Toast.LENGTH_LONG).show()
            Log.w("MainActivity", "Relatorio de falha anterior: ${it.take(700)}")
        }
        renderSafeModeStatus()

        binding.developerToolsSection.visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE

        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.seedDemoButton.setOnClickListener {
            viewModel.seedAndPropagateDemo()
        }

        binding.syncButton.setOnClickListener {
            viewModel.runManualSync()
        }

        binding.lookupCnpjButton.setOnClickListener {
            viewModel.lookupCompany(binding.cnpjInput.text.toString())
        }


        binding.openScannerButton.setOnClickListener {
            startActivity(Intent(this, CaScannerActivity::class.java))
        }

        binding.openEpiButton.setOnClickListener {
            startActivity(Intent(this, EpiManagementActivity::class.java))
        }
        binding.openEmployeesButton.setOnClickListener {
            startActivity(Intent(this, EmployeeManagementActivity::class.java))
        }

        binding.openAsoButton.setOnClickListener {
            startActivity(Intent(this, AsoActivity::class.java))
        }

        binding.openPgrButton.setOnClickListener {
            startActivity(Intent(this, PgrActivity::class.java))
        }

        binding.openCatButton.setOnClickListener {
            startActivity(Intent(this, CatActivity::class.java))
        }

        binding.openAprButton.setOnClickListener {
            startActivity(Intent(this, AprActivity::class.java))
        }

        binding.openOsButton.setOnClickListener {
            startActivity(Intent(this, OsActivity::class.java))
        }

        binding.openPcmsoButton.setOnClickListener {
            startActivity(Intent(this, PcmsoActivity::class.java))
        }

        binding.openPppButton.setOnClickListener {
            startActivity(Intent(this, PppActivity::class.java))
        }

        binding.openDocumentsButton.setOnClickListener {
            startActivity(Intent(this, DocumentsActivity::class.java))
        }

        binding.openDiagnosticsButton.setOnClickListener {
            if (!BuildConfig.DEBUG) {
                Toast.makeText(this, "Diagnostico disponivel apenas em modo de desenvolvimento", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }

        binding.openLatestDocumentButton.setOnClickListener {
            lifecycleScope.launch(uiErrorHandler) {
                val latestId = withContext(Dispatchers.IO) { DocumentLocalRepository.findLatestDocumentId() }
                if (latestId.isNullOrBlank()) {
                    Toast.makeText(this@MainActivity, "Nenhum documento local encontrado", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                startActivity(
                    Intent(this@MainActivity, DocumentDetailActivity::class.java)
                        .putExtra(DocumentDetailActivity.EXTRA_DOCUMENT_ID, latestId)
                )
            }
        }

        binding.openAetButton.setOnClickListener {
            openGenericDocument("AET", "AET - Analise Ergonomica do Trabalho", "NR-17")
        }
        binding.openLtcatButton.setOnClickListener {
            openGenericDocument("LTCAT", "LTCAT - Laudo Tecnico de Condicoes Ambientais", "INSS / Previdenciario")
        }
        binding.openInventarioButton.setOnClickListener {
            openGenericDocument("INVENTARIO", "Inventario de Riscos", "NR-01 GRO/PGR")
        }
        binding.openMapaButton.setOnClickListener {
            openGenericDocument("MAPA_RISCOS", "Mapa de Riscos (NR-5)", "CIPA - Comissao Interna de Prevencao de Acidentes")
        }
        binding.openNr10Button.setOnClickListener {
            openGenericDocument("LAUDO_NR10", "Laudo NR-10", "Seguranca em instalacoes eletricas")
        }
        binding.openNr12Button.setOnClickListener {
            openGenericDocument("LAUDO_NR12", "Laudo NR-12", "Seguranca em maquinas e equipamentos")
        }
        binding.openNr20Button.setOnClickListener {
            openGenericDocument("LAUDO_NR20", "Laudo NR-20", "Inflamaveis e combustiveis")
        }
        binding.openPcaButton.setOnClickListener {
            openGenericDocument("PCA", "PCA - Programa de Conservacao Auditiva", "Saude ocupacional")
        }
        binding.openPprButton.setOnClickListener {
            openGenericDocument("PPR", "PPR - Programa de Protecao Respiratoria", "Protecao respiratoria")
        }
        binding.openPtButton.setOnClickListener {
            openGenericDocument("PT", "PT - Permissao de Trabalho", "Controle operacional")
        }
        binding.openInsalubridadeButton.setOnClickListener {
            openGenericDocument("INSALUBRIDADE", "Laudo de Insalubridade", "NR-15")
        }
        binding.openPericulosidadeButton.setOnClickListener {
            openGenericDocument("PERICULOSIDADE", "Laudo de Periculosidade", "NR-16")
        }
        binding.openResgateButton.setOnClickListener {
            openGenericDocument("RESGATE", "Plano de Resgate", "Trabalho em altura e espaco confinado")
        }
        binding.openPlanoAcaoButton.setOnClickListener {
            openGenericDocument("PLANO_ACAO", "Plano de Acao SST", "Gestao corretiva e preventiva")
        }
        binding.openPgrtrButton.setOnClickListener {
            openGenericDocument("PGRTR", "PGRTR", "Gerenciamento de riscos especifico")
        }

        lifecycleScope.launch(uiErrorHandler) {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch(uiErrorHandler) {
                    viewModel.dashboardCounts.collect { counts ->
                        binding.countsText.text = getString(
                            R.string.dashboard_counts_template,
                            counts.clinicCount,
                            counts.companyCount,
                            counts.employeeCount,
                            counts.employeeRiskLinks,
                            counts.pendingSyncItems
                        )
                    }
                }
                launch(uiErrorHandler) {
                    viewModel.statusMessage.collect { message ->
                        binding.statusText.text = message
                    }
                }
                launch(uiErrorHandler) {
                    viewModel.backendHealth.collect { health ->
                        binding.backendHealthText.text = health
                    }
                }
                launch(uiErrorHandler) {
                    viewModel.backendFallbackBadge.collect { badge ->
                        binding.backendFallbackBadgeText.text = badge
                    }
                }
                launch(uiErrorHandler) {
                    viewModel.backendFallbackLevel.collect { level ->
                        val colorRes = when (level) {
                            BackendFallbackLevel.NORMAL -> R.color.status_normal
                            BackendFallbackLevel.MODERATE,
                            BackendFallbackLevel.LOW_SAMPLE -> R.color.status_moderate
                            BackendFallbackLevel.HIGH -> R.color.status_high
                            BackendFallbackLevel.CRITICAL -> R.color.status_critical
                            BackendFallbackLevel.UNKNOWN -> R.color.status_neutral
                        }

                        binding.backendFallbackBadgeText.setTextColor(
                            ContextCompat.getColor(this@MainActivity, colorRes)
                        )
                    }
                }
                launch(uiErrorHandler) {
                    viewModel.companyLookupState.collect { lookupState ->
                        binding.cnpjProgress.visibility =
                            if (lookupState.isLoading) View.VISIBLE else View.GONE

                        val company = lookupState.company
                        binding.companyResultText.text = when {
                            company != null -> getString(
                                R.string.company_result_template,
                                company.legalName,
                                if (company.tradeName.isBlank()) "-" else company.tradeName,
                                company.cnpj,
                                if (company.cnae.isBlank()) "-" else company.cnae,
                                lookupState.riskGrade ?: 2,
                                if (company.city.isBlank()) "-" else company.city,
                                if (company.state.isBlank()) "-" else company.state,
                                if (company.postalCode.isBlank()) "-" else company.postalCode
                            )

                            !lookupState.errorMessage.isNullOrBlank() -> getString(
                                R.string.company_lookup_error_template,
                                lookupState.errorMessage
                            )

                            else -> getString(R.string.company_result_placeholder)
                        }
                    }
                }
                launch(uiErrorHandler) {
                    viewModel.lookupHistory.collect { history ->
                        binding.lookupHistoryText.text = if (history.isEmpty()) {
                            getString(R.string.lookup_history_empty)
                        } else {
                            history.joinToString(separator = "\n\n") {
                                getString(
                                    R.string.lookup_history_item_template,
                                    it.cnpj,
                                    it.legalName,
                                    if (it.cnae.isBlank()) "-" else it.cnae,
                                    it.riskGrade
                                )
                            }
                        }
                    }
                }
                launch(uiErrorHandler) {
                    while (true) {
                        val summary = withContext(Dispatchers.IO) { DocumentLocalRepository.loadOperationalSummary() }
                        binding.opsSummaryText.text = getString(
                            R.string.home_ops_summary_template,
                            summary.totalDocuments,
                            summary.pendingSync,
                            summary.failedSync,
                            summary.signedToday,
                            summary.expiringIn30Days,
                            summary.latestDocumentId ?: "-"
                        )
                        renderSafeModeStatus()
                        kotlinx.coroutines.delay(5_000)
                    }
                }
            }
        }
    }

    private fun renderSafeModeStatus() {
        val safeMode = CrashShield.isSafeModeActive(this)
        val crashCount = CrashShield.crashCountInCurrentWindow(this)

        if (safeMode) {
            val remainingMs = CrashShield.remainingSafeModeMs(this)
            val remainingMin = (remainingMs / 60_000L).coerceAtLeast(0L)
            binding.safeModeText.text = getString(R.string.home_safe_mode_on_with_timer, remainingMin)
            binding.stabilityStatusText.text = getString(R.string.home_stability_critical)
            binding.stabilityStatusText.setTextColor(ContextCompat.getColor(this, R.color.status_critical))
        } else {
            binding.safeModeText.text = getString(R.string.home_safe_mode_off)
            when {
                crashCount >= 2 -> {
                    binding.stabilityStatusText.text = getString(R.string.home_stability_high)
                    binding.stabilityStatusText.setTextColor(ContextCompat.getColor(this, R.color.status_high))
                }
                crashCount == 1 -> {
                    binding.stabilityStatusText.text = getString(R.string.home_stability_moderate)
                    binding.stabilityStatusText.setTextColor(ContextCompat.getColor(this, R.color.status_moderate))
                }
                else -> {
                    binding.stabilityStatusText.text = getString(R.string.home_stability_normal)
                    binding.stabilityStatusText.setTextColor(ContextCompat.getColor(this, R.color.status_normal))
                }
            }
        }

        val nowLabel = timeFormat.format(Date(System.currentTimeMillis()))
        binding.stabilityUpdatedAtText.text = getString(R.string.home_stability_updated_template, nowLabel)
    }

    private fun showRecoverableError(throwable: Throwable) {
        Toast.makeText(
            this,
            "Erro na inicializacao: ${throwable.javaClass.simpleName}",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun renderPersonalizedSubtitle() {
        val name = ProfileManager.getDisplayName(this)
        if (name.isNotBlank()) {
            binding.subtitleText.text = getString(R.string.home_welcome_name_template, name)
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

    private fun showCrashlyticsConsentIfNeeded() {
        val hasShownConsent = getSharedPreferences("anda_ui_state", MODE_PRIVATE)
            .getBoolean("consent_crashlytics_shown", false)

        if (!hasShownConsent) {
            // Mark that we've shown the consent dialog
            getSharedPreferences("anda_ui_state", MODE_PRIVATE)
                .edit()
                .putBoolean("consent_crashlytics_shown", true)
                .apply()

            // Show the consent dialog
            CrashReporterConsentDialog.show(supportFragmentManager)
        }
    }
}

