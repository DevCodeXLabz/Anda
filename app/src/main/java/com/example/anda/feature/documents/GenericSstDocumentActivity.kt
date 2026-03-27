package com.example.anda.feature.documents

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.anda.core.stability.CrashShield
import com.example.anda.core.stability.safeLaunch
import com.example.anda.data.local.entity.DocumentEntity
import com.example.anda.data.repository.DocumentLocalRepository
import com.example.anda.data.repository.SstRepository
import com.example.anda.data.services.AutofillService
import com.example.anda.data.services.DocumentArtifactExportService
import com.example.anda.data.services.DocumentGenerationService
import com.example.anda.databinding.ActivityGenericDocumentBinding
import com.example.anda.R
import com.example.anda.feature.companies.CompanyManagementActivity
import com.example.anda.feature.requests.ServiceRequestIntentContract
import com.example.anda.feature.security.DocumentSignatureHelper
import com.example.anda.domain.DocumentTypeNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GenericSstDocumentActivity : AppCompatActivity() {

    private data class RequiredField(
        val label: String,
        val targetView: View,
        val isMissing: () -> Boolean,
        val isApplicable: () -> Boolean = { true }
    )

    private data class DocumentProfile(
        val aliases: Set<String>,
        val defaultObjective: String,
        val defaultScope: String,
        val defaultDetails: String,
        val defaultRecommendations: String,
        val requireObjective: Boolean = false,
        val requireScope: Boolean = false,
        val requireDetails: Boolean = true,
        val requireRecommendations: Boolean = false
    )

    private lateinit var binding: ActivityGenericDocumentBinding
    private val autofillService = AutofillService()
    private val generationService = DocumentGenerationService()
    private val artifactExportService = DocumentArtifactExportService()
    private val dtFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))

    private var currentDocumentId: String? = null
    private var currentPayload = ""
    private var docType = "GENERIC"
    private var docTitle = "Documento SST"
    private var docSubtitle = "Documento tecnico operacional"
    private var sourceRequestCode: String? = null
    private var autoFocusDoneOnOpen = false
    private lateinit var profile: DocumentProfile

    private val pickCompanyLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val cnpj = data.getStringExtra(CompanyManagementActivity.RESULT_COMPANY_CNPJ).orEmpty()
            val name = data.getStringExtra(CompanyManagementActivity.RESULT_COMPANY_LEGAL_NAME).orEmpty()
            val cnae = data.getStringExtra(CompanyManagementActivity.RESULT_COMPANY_CNAE).orEmpty()
            if (cnpj.isNotBlank()) binding.companyCnpjInput.setText(cnpj)
            if (name.isNotBlank()) binding.companyNameInput.setText(name)
            if (cnae.isNotBlank()) binding.companyCnaeInput.setText(cnae)
            binding.cnpjStatusText.text = getString(R.string.doc_pick_company_selected_local)
            applyDocumentDefaults(force = false)
            preloadFromPreviousDocument(cnpj.filter(Char::isDigit))
            updateMissingFieldsSummary()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGenericDocumentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        docType = intent.getStringExtra(EXTRA_DOCUMENT_TYPE).orEmpty().ifBlank { "GENERIC" }
        docType = normalizeType(docType)
        docTitle = intent.getStringExtra(EXTRA_DOCUMENT_TITLE).orEmpty().ifBlank { "Documento SST" }
        docSubtitle = intent.getStringExtra(EXTRA_DOCUMENT_SUBTITLE).orEmpty().ifBlank { "Documento tecnico operacional" }
        sourceRequestCode = intent.getStringExtra(ServiceRequestIntentContract.EXTRA_REQUEST_CODE)?.trim().orEmpty().ifBlank { null }
        profile = resolveProfile(docType)

        binding.docTitleText.text = docTitle
        binding.docSubtitleText.text = docSubtitle
        applyRequestContextFromIntent()
        applyDocumentDefaults(force = false)
        configureSpecializedFields()
        setupRealtimeMissingSummary()
        updateMissingFieldsSummary()
        binding.root.postDelayed({ focusFirstMissingOnOpenIfNeeded() }, 220L)

        DocumentLocalRepository.initialize(this)
        SstRepository.initialize(this)

        binding.signButton.isEnabled = false
        binding.exportPdfButton.isEnabled = false

        binding.lookupCnpjButton.setOnClickListener {
            val cnpj = binding.companyCnpjInput.text.toString().trim()
            if (cnpj.filter { it.isDigit() }.length != 14) {
                binding.cnpjStatusText.text = "CNPJ invalido. Informe 14 digitos."
                return@setOnClickListener
            }
            binding.cnpjStatusText.text = "Buscando empresa..."
            binding.lookupCnpjButton.isEnabled = false
            lifecycleScope.launch { SstRepository.lookupCompanyByCnpj(cnpj) }
        }

        binding.genericPickCompanyButton.setOnClickListener {
            pickCompanyLauncher.launch(CompanyManagementActivity.buildPickIntent(this))
        }

        binding.generateButton.setOnClickListener { generateAndSave() }
        binding.nextMissingButton.setOnClickListener { focusNextMissingField() }
        binding.prevMissingButton.setOnClickListener { focusPrevMissingField() }
        binding.signButton.setOnClickListener { signCurrentDocument() }
        binding.exportPdfButton.setOnClickListener { exportPdf() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SstRepository.companyLookupState.collect { state ->
                    when {
                        state.isLoading -> {
                            binding.cnpjStatusText.text = "Buscando empresa..."
                            binding.lookupCnpjButton.isEnabled = false
                        }
                        state.company != null -> {
                            val c = state.company
                            if (binding.companyNameInput.text.isNullOrBlank()) {
                                binding.companyNameInput.setText(c.legalName)
                            }
                            if (binding.companyCnaeInput.text.isNullOrBlank()) {
                                binding.companyCnaeInput.setText(c.cnae)
                            }
                            binding.cnpjStatusText.text = c.legalName.take(50)
                            applyDocumentDefaults(force = false)
                            preloadFromPreviousDocument(c.cnpj.filter(Char::isDigit))
                            updateMissingFieldsSummary()
                            binding.lookupCnpjButton.isEnabled = true
                        }
                        !state.errorMessage.isNullOrBlank() -> {
                            binding.cnpjStatusText.text = "Nao encontrado: ${state.errorMessage}"
                            binding.lookupCnpjButton.isEnabled = true
                        }
                        else -> binding.lookupCnpjButton.isEnabled = true
                    }
                }
            }
        }
    }

    private fun generateAndSave() {
        val cnpj = binding.companyCnpjInput.text.toString().filter { it.isDigit() }
        val companyName = binding.companyNameInput.text.toString().trim()
        val cnae = binding.companyCnaeInput.text.toString().trim()
        val responsible = binding.responsibleInput.text.toString().trim()
        val objective = binding.objectiveInput.text.toString().trim()
        val scope = binding.scopeInput.text.toString().trim()
        val details = binding.detailsInput.text.toString().trim()
        val recommendations = binding.recommendationsInput.text.toString().trim()
        val specializedFields = collectSpecializedFields()
        val requiredMessage = getString(com.example.anda.R.string.generic_doc_error_required)

        if (cnpj.length != 14) {
            binding.companyCnpjInput.error = "CNPJ invalido"
            return
        }
        if (companyName.isBlank()) {
            binding.companyNameInput.error = requiredMessage
            return
        }
        if (responsible.isBlank()) {
            binding.responsibleInput.error = requiredMessage
            return
        }
        if (profile.requireObjective && objective.isBlank()) {
            binding.objectiveInput.error = requiredMessage
            return
        }
        if (profile.requireScope && scope.isBlank()) {
            binding.scopeInput.error = requiredMessage
            return
        }
        if (profile.requireDetails && details.isBlank()) {
            binding.detailsInput.error = requiredMessage
            return
        }
        if (profile.requireRecommendations && recommendations.isBlank()) {
            binding.recommendationsInput.error = requiredMessage
            return
        }
        if (!validateSpecializedFields()) {
            return
        }

        safeLaunch("GenericSstDocumentActivity/generate") {
            val docId = "${docType.lowercase(Locale.ROOT)}-${System.currentTimeMillis()}"
            val html = generationService.generateGenericSstDocument(
                DocumentGenerationService.GenericSstDocumentInput(
                    documentType = docType,
                    documentTitle = docTitle,
                    normativeReference = docSubtitle,
                    companyCnpj = cnpj,
                    companyName = companyName,
                    companyCnae = cnae,
                    responsibleName = responsible,
                    objective = objective,
                    scope = scope,
                    technicalDetails = details,
                    recommendations = recommendations,
                    specializedFields = specializedFields,
                    documentId = docId
                )
            )

            currentDocumentId = docId
            currentPayload = html

            withContext(Dispatchers.IO) {
                DocumentLocalRepository.saveDraft(
                    DocumentEntity(
                        documentId = docId,
                        documentType = docType,
                        companyCnpj = cnpj,
                        title = "$docType - $companyName",
                        payloadJson = html,
                        contentHash = DocumentSignatureHelper.sha256(html),
                        sourceRequestCode = sourceRequestCode
                    )
                )
            }

            binding.previewText.text = html.take(700)
            binding.signButton.isEnabled = true
            binding.exportPdfButton.isEnabled = true
            binding.statusText.text = "$docType gerado offline: $docId"
        }
    }

    private fun signCurrentDocument() {
        val documentId = currentDocumentId
        if (documentId.isNullOrBlank() || currentPayload.isBlank()) {
            Toast.makeText(this, "Gere o documento antes de assinar", Toast.LENGTH_SHORT).show()
            return
        }
        if (!DocumentSignatureHelper.canAuthenticate(this)) {
            Toast.makeText(this, "Biometria/credencial indisponivel", Toast.LENGTH_SHORT).show()
            return
        }
        val responsible = binding.responsibleInput.text.toString().trim().ifBlank { "Responsavel Tecnico" }
        DocumentSignatureHelper.signWithBiometric(
            activity = this,
            payload = currentPayload,
            signedBy = responsible,
            onSuccess = { record ->
                lifecycleScope.launch(Dispatchers.IO) {
                    runCatching {
                        DocumentLocalRepository.markSigned(
                            documentId = documentId,
                            signedAt = record.signedAt,
                            signedBy = record.signedBy,
                            contentHash = record.contentHash,
                            signatureB64 = record.signatureB64,
                            publicKeyFingerprint = record.publicKeyFingerprint,
                            signatureAlgorithm = "SHA256withRSA"
                        )
                    }.onFailure {
                        CrashShield.recordRecoverableError("GenericSstDocumentActivity/markSigned", it)
                    }
                }
                runOnUiThread {
                    binding.statusText.text = "Assinado por $responsible em ${dtFmt.format(Date(record.signedAt))}"
                }
            },
            onError = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        )
    }

    private fun exportPdf() {
        if (currentPayload.isBlank()) {
            Toast.makeText(this, "Gere o documento antes", Toast.LENGTH_SHORT).show()
            return
        }

        safeLaunch("GenericSstDocumentActivity/exportPdf") {
            runCatching {
                withContext(Dispatchers.IO) {
                    artifactExportService.exportPlainTextPdfAndAudit(
                        context = this@GenericSstDocumentActivity,
                        title = "$docType - ANDA",
                        body = currentPayload,
                        documentId = currentDocumentId,
                        filePrefix = docType.lowercase(Locale.ROOT)
                    )
                }
            }.onSuccess { result ->
                val auditPath = result.auditFile?.absolutePath ?: "Nao gerado"
                binding.statusText.text = "PDF exportado: ${result.pdfFile.absolutePath}\nAuditoria TXT: $auditPath"
            }.onFailure {
                Toast.makeText(this@GenericSstDocumentActivity, "Falha ao exportar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyRequestContextFromIntent() {
        val requestCompanyCnpj = intent.getStringExtra(ServiceRequestIntentContract.EXTRA_REQUEST_COMPANY_CNPJ)
            .orEmpty()
            .filter(Char::isDigit)
        val requestCompanyName = intent.getStringExtra(ServiceRequestIntentContract.EXTRA_REQUEST_COMPANY_NAME)
            .orEmpty()
            .trim()

        if (requestCompanyCnpj.length == 14 && binding.companyCnpjInput.text.isNullOrBlank()) {
            binding.companyCnpjInput.setText(requestCompanyCnpj)
            preloadFromPreviousDocument(requestCompanyCnpj)
        }
        if (requestCompanyName.isNotBlank() && binding.companyNameInput.text.isNullOrBlank()) {
            binding.companyNameInput.setText(requestCompanyName)
        }
        if (!sourceRequestCode.isNullOrBlank()) {
            binding.statusText.text = getString(R.string.service_request_linked_status_template, sourceRequestCode)
        }
    }

    companion object {
        const val EXTRA_DOCUMENT_TYPE = "extra_document_type"
        const val EXTRA_DOCUMENT_TITLE = "extra_document_title"
        const val EXTRA_DOCUMENT_SUBTITLE = "extra_document_subtitle"
        const val KEY_AET_WORKSTATION = "aet_workstation"
        const val KEY_AET_TASK = "aet_task"
        const val KEY_LTCAT_AGENT = "ltcat_agent"
        const val KEY_LTCAT_INTENSITY = "ltcat_intensity"
        const val KEY_LTCAT_HABITUALITY = "ltcat_habituality"
        const val KEY_PT_ISSUER = "pt_issuer"
        const val KEY_PT_EXECUTOR = "pt_executor"
        const val KEY_PT_AUTHORIZER = "pt_authorizer"
        const val KEY_NR_ASSET = "nr_asset"
        const val KEY_NR_CRITICAL = "nr_critical"
        const val KEY_NR_DEADLINE = "nr_deadline"
        const val KEY_PCA_RISK = "pca_risk"
        const val KEY_PCA_MEASURE = "pca_measure"
        const val KEY_PPR_EPI = "ppr_epi"
        const val KEY_PPR_FIT = "ppr_fit"
        const val KEY_INSALUBRIDADE_AGENT = "insalubridade_agent"
        const val KEY_INSALUBRIDADE_GRAU = "insalubridade_grau"
        const val KEY_PERICULOSIDADE_AGENT = "periculosidade_agent"
        const val KEY_PERICULOSIDADE_EXP = "periculosidade_exp"
        const val KEY_PLANO_ACAO_RISCO = "plano_acao_risco"
        const val KEY_PLANO_ACAO_ACAO = "plano_acao_acao"
        // Block 4
        const val KEY_RESGATE_CENARIO = "resgate_cenario"
        const val KEY_RESGATE_EQUIPE = "resgate_equipe"
        const val KEY_INVENTARIO_SETOR = "inventario_setor"
        const val KEY_INVENTARIO_RISCO = "inventario_risco"
        const val KEY_PGRTR_ATIVIDADE = "pgrtr_atividade"
        const val KEY_PGRTR_RISCO = "pgrtr_risco"
        const val KEY_MAPA_SETOR = "mapa_setor"
        const val KEY_MAPA_RISCO_GRAVE = "mapa_risco_grave"

        private val PROFILES = listOf(
            DocumentProfile(
                aliases = setOf("AET"),
                defaultObjective = "Avaliar adaptacao ergonomica entre trabalhador, tarefa, mobiliario e organizacao do trabalho.",
                defaultScope = "Postos criticos, atividades repetitivas, levantamento de cargas e fatores psicossociais.",
                defaultDetails = "Descrever metodo (observacao, entrevista, registros), riscos ergonomicos e evidencias coletadas.",
                defaultRecommendations = "Priorizar medidas de engenharia, ajuste de posto, pausas e plano de monitoramento.",
                requireObjective = true,
                requireScope = true,
                requireRecommendations = true
            ),
            DocumentProfile(
                aliases = setOf("LTCAT"),
                defaultObjective = "Caracterizar exposicao ocupacional para fins previdenciarios.",
                defaultScope = "Ambientes, agentes nocivos, intensidade/concentracao, habitualidade e permanencia.",
                defaultDetails = "Registrar metodologia de avaliacao, fontes, medições e conclusao tecnica.",
                defaultRecommendations = "Definir controles e periodicidade de reavaliacao.",
                requireObjective = true,
                requireScope = true,
                requireRecommendations = true
            ),
            DocumentProfile(
                aliases = setOf("LAUDO_NR10", "NR10"),
                defaultObjective = "Avaliar conformidade de instalacoes e servicos em eletricidade.",
                defaultScope = "Painéis, circuitos, procedimentos, qualificação e medidas de protecao coletiva/individual.",
                defaultDetails = "Descrever nao conformidades, classificacao de risco e evidencias tecnicas.",
                defaultRecommendations = "Plano de adequacao com prazos e responsaveis.",
                requireObjective = true,
                requireScope = true,
                requireRecommendations = true
            ),
            DocumentProfile(
                aliases = setOf("LAUDO_NR12", "NR12"),
                defaultObjective = "Avaliar seguranca em maquinas e equipamentos.",
                defaultScope = "Sistemas de protecao, parada de emergencia, manualizacao e bloqueio de energia.",
                defaultDetails = "Mapear perigos por maquina, risco residual e historico de incidentes.",
                defaultRecommendations = "Adequacoes tecnicas e administrativas com validacao final.",
                requireObjective = true,
                requireScope = true,
                requireRecommendations = true
            ),
            DocumentProfile(
                aliases = setOf("LAUDO_NR20", "NR20"),
                defaultObjective = "Avaliar riscos com inflamaveis e combustiveis.",
                defaultScope = "Armazenamento, manuseio, transferencia, areas classificadas e resposta a emergencia.",
                defaultDetails = "Identificar cenarios acidentais e controles existentes.",
                defaultRecommendations = "Fortalecer barreiras preventivas e plano de emergencia.",
                requireObjective = true,
                requireScope = true,
                requireRecommendations = true
            ),
            DocumentProfile(
                aliases = setOf("PCA", "PPR", "PT", "PLANO_ACAO", "RESGATE", "PGRTR", "INVENTARIO", "INSALUBRIDADE", "PERICULOSIDADE", "MAPA_RISCO", "MAPA_RISCOS"),
                defaultObjective = "Consolidar controles tecnicos e administrativos para reducao de riscos ocupacionais.",
                defaultScope = "Setores e atividades aplicaveis, com foco em risco significativo.",
                defaultDetails = "Registrar diagnostico tecnico com evidencias e criterios utilizados.",
                defaultRecommendations = "Definir acoes, prioridades, prazos e responsaveis.",
                requireObjective = true,
                requireScope = true,
                requireRecommendations = true
            ),
            DocumentProfile(
                aliases = setOf("GENERIC"),
                defaultObjective = "Documento tecnico operacional SST.",
                defaultScope = "Escopo conforme necessidade da operacao.",
                defaultDetails = "Detalhar analise tecnica com base nas evidencias coletadas.",
                defaultRecommendations = "Registrar plano de acao com prazos e responsaveis."
            )
        )

        private fun normalizeType(type: String): String {
            return DocumentTypeNormalizer.normalize(type)
        }

        private fun resolveProfile(type: String): DocumentProfile {
            return PROFILES.firstOrNull { it.aliases.contains(type.uppercase(Locale.ROOT)) }
                ?: PROFILES.last()
        }
    }

    private fun applyDocumentDefaults(force: Boolean) {
        fun apply(current: CharSequence?, fallback: String, setter: (String) -> Unit) {
            if (force || current.isNullOrBlank()) setter(fallback)
        }
        apply(binding.objectiveInput.text, profile.defaultObjective) { binding.objectiveInput.setText(it) }
        apply(binding.scopeInput.text, profile.defaultScope) { binding.scopeInput.setText(it) }
        apply(binding.detailsInput.text, profile.defaultDetails) { binding.detailsInput.setText(it) }
        apply(binding.recommendationsInput.text, profile.defaultRecommendations) { binding.recommendationsInput.setText(it) }
    }

    private fun preloadFromPreviousDocument(companyCnpjDigits: String) {
        if (companyCnpjDigits.length != 14) return

        safeLaunch("GenericSstDocumentActivity/preloadFromPreviousDocument") {
            val previous = withContext(Dispatchers.IO) {
                DocumentLocalRepository.findLatestByCompanyAndType(companyCnpjDigits, docType)
            } ?: return@safeLaunch

            val context = autofillService.extractPreviousGenericContextFromHtml(
                payload = previous.payloadJson,
                signedBy = previous.signedBy
            )

            if (binding.responsibleInput.text.isNullOrBlank() && !context.responsibleName.isNullOrBlank()) {
                binding.responsibleInput.setText(context.responsibleName)
            }
            if (binding.objectiveInput.text.isNullOrBlank() && !context.objective.isNullOrBlank()) {
                binding.objectiveInput.setText(context.objective)
            }
            if (binding.scopeInput.text.isNullOrBlank() && !context.scope.isNullOrBlank()) {
                binding.scopeInput.setText(context.scope)
            }
            if (binding.detailsInput.text.isNullOrBlank() && !context.technicalDetails.isNullOrBlank()) {
                binding.detailsInput.setText(context.technicalDetails)
            }
            if (binding.recommendationsInput.text.isNullOrBlank() && !context.recommendations.isNullOrBlank()) {
                binding.recommendationsInput.setText(context.recommendations)
            }
            updateMissingFieldsSummary()
        }
    }

    private fun configureSpecializedFields() {
        val isAet = docType == "AET"
        val isLtcat = docType == "LTCAT"
        val isPt = docType == "PT"
        val isNrLaudo = docType == "LAUDO_NR10" || docType == "LAUDO_NR12" || docType == "LAUDO_NR20"
        val isPca = docType == "PCA"
        val isPpr = docType == "PPR"
        val isInsalubridade = docType == "INSALUBRIDADE"
        val isPericulosidade = docType == "PERICULOSIDADE"
        val isPlanoAcao = docType == "PLANO_ACAO"
        val isResgate = docType == "RESGATE"
        val isInventario = docType == "INVENTARIO"
        val isPgrtr = docType == "PGRTR"
        val isMapaRisco = docType == "MAPA_RISCO"

        binding.specializedSectionTitle.isVisible = isAet || isLtcat || isPt || isNrLaudo || isPca || isPpr || isInsalubridade || isPericulosidade || isPlanoAcao || isResgate || isInventario || isPgrtr || isMapaRisco
        binding.aetWorkstationLayout.isVisible = isAet
        binding.aetTaskLayout.isVisible = isAet
        binding.ltcatAgentLayout.isVisible = isLtcat
        binding.ltcatIntensityLayout.isVisible = isLtcat
        binding.ltcatHabitualityLayout.isVisible = isLtcat
        binding.ptIssuerLayout.isVisible = isPt
        binding.ptExecutorLayout.isVisible = isPt
        binding.ptAuthorizerLayout.isVisible = isPt
        binding.nrAssetLayout.isVisible = isNrLaudo
        binding.nrCriticalLayout.isVisible = isNrLaudo
        binding.nrDeadlineLayout.isVisible = isNrLaudo
        binding.pcaRiskLayout.isVisible = isPca
        binding.pcaMeasureLayout.isVisible = isPca
        binding.pprEpiLayout.isVisible = isPpr
        binding.pprFitLayout.isVisible = isPpr
        binding.insalubridadeAgentLayout.isVisible = isInsalubridade
        binding.insalubridadeGrauLayout.isVisible = isInsalubridade
        binding.periculosidadeAgentLayout.isVisible = isPericulosidade
        binding.periculosidadeExpLayout.isVisible = isPericulosidade
        binding.planoAcaoRiscoLayout.isVisible = isPlanoAcao
        binding.planoAcaoAcaoLayout.isVisible = isPlanoAcao
        // Block 4
        binding.resgateScenarioLayout.isVisible = isResgate
        binding.resgateEquipeLayout.isVisible = isResgate
        binding.inventarioSetorLayout.isVisible = isInventario
        binding.inventarioRiscoLayout.isVisible = isInventario
        binding.pgrtrAtividadeLayout.isVisible = isPgrtr
        binding.pgrtrRiscoLayout.isVisible = isPgrtr
        binding.mapaSetorLayout.isVisible = isMapaRisco
        binding.mapaRiscoGraveLayout.isVisible = isMapaRisco
        binding.prevMissingButton.isVisible = isPca || isPpr || isNrLaudo || isAet || isLtcat || isPt || isInsalubridade || isPericulosidade || isPlanoAcao || isResgate || isInventario || isPgrtr || isMapaRisco

        if (isPt) {
            val fallbackResponsible = binding.responsibleInput.text?.toString().orEmpty().trim()
            if (binding.ptIssuerInput.text.isNullOrBlank()) binding.ptIssuerInput.setText(fallbackResponsible)
            if (binding.ptExecutorInput.text.isNullOrBlank()) binding.ptExecutorInput.setText(fallbackResponsible)
            if (binding.ptAuthorizerInput.text.isNullOrBlank()) binding.ptAuthorizerInput.setText(fallbackResponsible)
        }

        updateMissingFieldsSummary()
    }

    private fun setupRealtimeMissingSummary() {
        val refresh = { updateMissingFieldsSummary() }
        binding.companyCnpjInput.doAfterTextChanged { refresh() }
        binding.companyNameInput.doAfterTextChanged { refresh() }
        binding.responsibleInput.doAfterTextChanged { refresh() }
        binding.objectiveInput.doAfterTextChanged { refresh() }
        binding.scopeInput.doAfterTextChanged { refresh() }
        binding.detailsInput.doAfterTextChanged { refresh() }
        binding.recommendationsInput.doAfterTextChanged { refresh() }
        binding.aetWorkstationInput.doAfterTextChanged { refresh() }
        binding.aetTaskInput.doAfterTextChanged { refresh() }
        binding.ltcatAgentInput.doAfterTextChanged { refresh() }
        binding.ltcatIntensityInput.doAfterTextChanged { refresh() }
        binding.ltcatHabitualityInput.doAfterTextChanged { refresh() }
        binding.ptIssuerInput.doAfterTextChanged { refresh() }
        binding.ptExecutorInput.doAfterTextChanged { refresh() }
        binding.ptAuthorizerInput.doAfterTextChanged { refresh() }
        binding.nrAssetInput.doAfterTextChanged { refresh() }
        binding.nrCriticalInput.doAfterTextChanged { refresh() }
        binding.nrDeadlineInput.doAfterTextChanged { refresh() }
        binding.pcaRiskInput.doAfterTextChanged { refresh() }
        binding.pcaMeasureInput.doAfterTextChanged { refresh() }
        binding.pprEpiInput.doAfterTextChanged { refresh() }
        binding.pprFitInput.doAfterTextChanged { refresh() }
        binding.insalubridadeAgentInput.doAfterTextChanged { refresh() }
        binding.insalubridadeGrauInput.doAfterTextChanged { refresh() }
        binding.periculosidadeAgentInput.doAfterTextChanged { refresh() }
        binding.periculosidadeExpInput.doAfterTextChanged { refresh() }
        binding.planoAcaoRiscoInput.doAfterTextChanged { refresh() }
        binding.planoAcaoAcaoInput.doAfterTextChanged { refresh() }
        // Block 4
        binding.resgateScenarioInput.doAfterTextChanged { refresh() }
        binding.resgateEquipeInput.doAfterTextChanged { refresh() }
        binding.inventarioSetorInput.doAfterTextChanged { refresh() }
        binding.inventarioRiscoInput.doAfterTextChanged { refresh() }
        binding.pgrtrAtividadeInput.doAfterTextChanged { refresh() }
        binding.pgrtrRiscoInput.doAfterTextChanged { refresh() }
        binding.mapaSetorInput.doAfterTextChanged { refresh() }
        binding.mapaRiscoGraveInput.doAfterTextChanged { refresh() }
    }

    private fun updateMissingFieldsSummary() {
        val missing = computeMissingRequiredFields()
        binding.nextMissingButton.isEnabled = missing.isNotEmpty()
        if (missing.isEmpty()) {
            binding.missingSummaryTitle.text = getString(com.example.anda.R.string.generic_doc_missing_status_ready)
            binding.missingSummaryText.text = getString(com.example.anda.R.string.generic_doc_missing_all_good)
            binding.missingSummaryCard.setStrokeColor(
                ContextCompat.getColor(this, com.example.anda.R.color.status_normal)
            )
            return
        }

        binding.missingSummaryTitle.text = getString(
            com.example.anda.R.string.generic_doc_missing_status_pending,
            missing.size
        )

        val previewLimit = 5
        val previewItems = missing.take(previewLimit).joinToString("\n") { "- $it" }
        val overflowCount = (missing.size - previewLimit).coerceAtLeast(0)
        val overflowText = if (overflowCount > 0) {
            "\n" + getString(com.example.anda.R.string.generic_doc_missing_more_template, overflowCount)
        } else {
            ""
        }

        binding.missingSummaryText.text = getString(
            com.example.anda.R.string.generic_doc_missing_template,
            "$previewItems$overflowText"
        )
        binding.missingSummaryCard.setStrokeColor(
            ContextCompat.getColor(this, com.example.anda.R.color.status_moderate)
        )
    }

    private fun computeMissingRequiredFields(): List<String> {
        return buildRequiredFieldOrder()
            .filter { it.isApplicable() && it.isMissing() }
            .map { it.label }
    }

    private fun focusNextMissingField() {
        val next = findNextMissingField()
        if (next == null) {
            Toast.makeText(this, getString(com.example.anda.R.string.generic_doc_next_missing_done), Toast.LENGTH_SHORT).show()
            return
        }

        next.targetView.requestFocus()
        binding.contentScrollView.post {
            binding.contentScrollView.smoothScrollTo(0, (next.targetView.top - 120).coerceAtLeast(0))
        }
    }

    private fun focusFirstMissingOnOpenIfNeeded() {
        if (autoFocusDoneOnOpen) return
        autoFocusDoneOnOpen = true

        val next = findNextMissingField() ?: return
        next.targetView.requestFocus()
        binding.contentScrollView.post {
            binding.contentScrollView.smoothScrollTo(0, (next.targetView.top - 120).coerceAtLeast(0))
        }
    }

    private fun findNextMissingField(): RequiredField? {
        return buildRequiredFieldOrder().firstOrNull { it.isApplicable() && it.isMissing() }
    }

    private fun focusPrevMissingField() {
        val currentIndex = buildRequiredFieldOrder().indexOfFirst { it.isApplicable() && it.isMissing() }
        if (currentIndex <= 0) {
            Toast.makeText(this, "Ja no primeiro pendente.", Toast.LENGTH_SHORT).show()
            return
        }
        val fields = buildRequiredFieldOrder().take(currentIndex)
        val prev = fields.lastOrNull { it.isApplicable() && it.isMissing() }
        if (prev == null) {
            Toast.makeText(this, "Nenhum pendente anterior.", Toast.LENGTH_SHORT).show()
            return
        }
        prev.targetView.requestFocus()
        binding.contentScrollView.post {
            binding.contentScrollView.smoothScrollTo(0, (prev.targetView.top - 120).coerceAtLeast(0))
        }
    }

    private fun buildRequiredFieldOrder(): List<RequiredField> {
        return listOf(
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_cnpj),
                targetView = binding.companyCnpjInput,
                isMissing = { binding.companyCnpjInput.text?.toString().orEmpty().filter { it.isDigit() }.length != 14 }
            ),
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_company_name),
                targetView = binding.companyNameInput,
                isMissing = { binding.companyNameInput.text.isNullOrBlank() }
            ),
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_responsible),
                targetView = binding.responsibleInput,
                isMissing = { binding.responsibleInput.text.isNullOrBlank() }
            ),
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_objective),
                targetView = binding.objectiveInput,
                isMissing = { binding.objectiveInput.text.isNullOrBlank() },
                isApplicable = { profile.requireObjective }
            ),
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_scope),
                targetView = binding.scopeInput,
                isMissing = { binding.scopeInput.text.isNullOrBlank() },
                isApplicable = { profile.requireScope }
            ),
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_details),
                targetView = binding.detailsInput,
                isMissing = { binding.detailsInput.text.isNullOrBlank() },
                isApplicable = { profile.requireDetails }
            ),
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_recommendations),
                targetView = binding.recommendationsInput,
                isMissing = { binding.recommendationsInput.text.isNullOrBlank() },
                isApplicable = { profile.requireRecommendations }
            ),
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_aet_workstation),
                targetView = binding.aetWorkstationInput,
                isMissing = { binding.aetWorkstationInput.text.isNullOrBlank() },
                isApplicable = { docType == "AET" }
            ),
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_aet_task),
                targetView = binding.aetTaskInput,
                isMissing = { binding.aetTaskInput.text.isNullOrBlank() },
                isApplicable = { docType == "AET" }
            ),
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_ltcat_agent),
                targetView = binding.ltcatAgentInput,
                isMissing = { binding.ltcatAgentInput.text.isNullOrBlank() },
                isApplicable = { docType == "LTCAT" }
            ),
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_ltcat_intensity),
                targetView = binding.ltcatIntensityInput,
                isMissing = { binding.ltcatIntensityInput.text.isNullOrBlank() },
                isApplicable = { docType == "LTCAT" }
            ),
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_ltcat_habituality),
                targetView = binding.ltcatHabitualityInput,
                isMissing = { binding.ltcatHabitualityInput.text.isNullOrBlank() },
                isApplicable = { docType == "LTCAT" }
            ),
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_pt_issuer),
                targetView = binding.ptIssuerInput,
                isMissing = { binding.ptIssuerInput.text.isNullOrBlank() },
                isApplicable = { docType == "PT" }
            ),
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_pt_executor),
                targetView = binding.ptExecutorInput,
                isMissing = { binding.ptExecutorInput.text.isNullOrBlank() },
                isApplicable = { docType == "PT" }
            ),
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_pt_authorizer),
                targetView = binding.ptAuthorizerInput,
                isMissing = { binding.ptAuthorizerInput.text.isNullOrBlank() },
                isApplicable = { docType == "PT" }
            ),
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_nr_asset),
                targetView = binding.nrAssetInput,
                isMissing = { binding.nrAssetInput.text.isNullOrBlank() },
                isApplicable = { docType == "LAUDO_NR10" || docType == "LAUDO_NR12" || docType == "LAUDO_NR20" }
            ),
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_nr_critical),
                targetView = binding.nrCriticalInput,
                isMissing = { binding.nrCriticalInput.text.isNullOrBlank() },
                isApplicable = { docType == "LAUDO_NR10" || docType == "LAUDO_NR12" || docType == "LAUDO_NR20" }
            ),
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_nr_deadline),
                targetView = binding.nrDeadlineInput,
                isMissing = { binding.nrDeadlineInput.text.isNullOrBlank() },
                isApplicable = { docType == "LAUDO_NR10" || docType == "LAUDO_NR12" || docType == "LAUDO_NR20" }
            ),
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_pca_risk),
                targetView = binding.pcaRiskInput,
                isMissing = { binding.pcaRiskInput.text.isNullOrBlank() },
                isApplicable = { docType == "PCA" }
            ),
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_pca_measure),
                targetView = binding.pcaMeasureInput,
                isMissing = { binding.pcaMeasureInput.text.isNullOrBlank() },
                isApplicable = { docType == "PCA" }
            ),
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_ppr_epi),
                targetView = binding.pprEpiInput,
                isMissing = { binding.pprEpiInput.text.isNullOrBlank() },
                isApplicable = { docType == "PPR" }
            ),
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_ppr_fit),
                targetView = binding.pprFitInput,
                isMissing = { binding.pprFitInput.text.isNullOrBlank() },
                isApplicable = { docType == "PPR" }
            ),
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_insalubridade_agent),
                targetView = binding.insalubridadeAgentInput,
                isMissing = { binding.insalubridadeAgentInput.text.isNullOrBlank() },
                isApplicable = { docType == "INSALUBRIDADE" }
            ),
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_insalubridade_grau),
                targetView = binding.insalubridadeGrauInput,
                isMissing = { binding.insalubridadeGrauInput.text.isNullOrBlank() },
                isApplicable = { docType == "INSALUBRIDADE" }
            ),
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_periculosidade_agent),
                targetView = binding.periculosidadeAgentInput,
                isMissing = { binding.periculosidadeAgentInput.text.isNullOrBlank() },
                isApplicable = { docType == "PERICULOSIDADE" }
            ),
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_periculosidade_exp),
                targetView = binding.periculosidadeExpInput,
                isMissing = { binding.periculosidadeExpInput.text.isNullOrBlank() },
                isApplicable = { docType == "PERICULOSIDADE" }
            ),
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_plano_acao_risco),
                targetView = binding.planoAcaoRiscoInput,
                isMissing = { binding.planoAcaoRiscoInput.text.isNullOrBlank() },
                isApplicable = { docType == "PLANO_ACAO" }
            ),
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_plano_acao_acao),
                targetView = binding.planoAcaoAcaoInput,
                isMissing = { binding.planoAcaoAcaoInput.text.isNullOrBlank() },
                isApplicable = { docType == "PLANO_ACAO" }
            ),
            // Block 4
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_resgate_cenario),
                targetView = binding.resgateScenarioInput,
                isMissing = { binding.resgateScenarioInput.text.isNullOrBlank() },
                isApplicable = { docType == "RESGATE" }
            ),
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_resgate_equipe),
                targetView = binding.resgateEquipeInput,
                isMissing = { binding.resgateEquipeInput.text.isNullOrBlank() },
                isApplicable = { docType == "RESGATE" }
            ),
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_inventario_setor),
                targetView = binding.inventarioSetorInput,
                isMissing = { binding.inventarioSetorInput.text.isNullOrBlank() },
                isApplicable = { docType == "INVENTARIO" }
            ),
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_inventario_risco),
                targetView = binding.inventarioRiscoInput,
                isMissing = { binding.inventarioRiscoInput.text.isNullOrBlank() },
                isApplicable = { docType == "INVENTARIO" }
            ),
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_pgrtr_atividade),
                targetView = binding.pgrtrAtividadeInput,
                isMissing = { binding.pgrtrAtividadeInput.text.isNullOrBlank() },
                isApplicable = { docType == "PGRTR" }
            ),
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_pgrtr_risco),
                targetView = binding.pgrtrRiscoInput,
                isMissing = { binding.pgrtrRiscoInput.text.isNullOrBlank() },
                isApplicable = { docType == "PGRTR" }
            ),
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_mapa_setor),
                targetView = binding.mapaSetorInput,
                isMissing = { binding.mapaSetorInput.text.isNullOrBlank() },
                isApplicable = { docType == "MAPA_RISCO" }
            ),
            RequiredField(
                label = getString(com.example.anda.R.string.generic_doc_missing_mapa_risco_grave),
                targetView = binding.mapaRiscoGraveInput,
                isMissing = { binding.mapaRiscoGraveInput.text.isNullOrBlank() },
                isApplicable = { docType == "MAPA_RISCO" }
            )
        )
    }

    private fun collectSpecializedFields(): Map<String, String> {
        return buildMap {
            putIfNotBlank(KEY_AET_WORKSTATION, binding.aetWorkstationInput.text?.toString())
            putIfNotBlank(KEY_AET_TASK, binding.aetTaskInput.text?.toString())
            putIfNotBlank(KEY_LTCAT_AGENT, binding.ltcatAgentInput.text?.toString())
            putIfNotBlank(KEY_LTCAT_INTENSITY, binding.ltcatIntensityInput.text?.toString())
            putIfNotBlank(KEY_LTCAT_HABITUALITY, binding.ltcatHabitualityInput.text?.toString())
            putIfNotBlank(KEY_PT_ISSUER, binding.ptIssuerInput.text?.toString())
            putIfNotBlank(KEY_PT_EXECUTOR, binding.ptExecutorInput.text?.toString())
            putIfNotBlank(KEY_PT_AUTHORIZER, binding.ptAuthorizerInput.text?.toString())
            putIfNotBlank(KEY_NR_ASSET, binding.nrAssetInput.text?.toString())
            putIfNotBlank(KEY_NR_CRITICAL, binding.nrCriticalInput.text?.toString())
            putIfNotBlank(KEY_NR_DEADLINE, binding.nrDeadlineInput.text?.toString())
            putIfNotBlank(KEY_PCA_RISK, binding.pcaRiskInput.text?.toString())
            putIfNotBlank(KEY_PCA_MEASURE, binding.pcaMeasureInput.text?.toString())
            putIfNotBlank(KEY_PPR_EPI, binding.pprEpiInput.text?.toString())
            putIfNotBlank(KEY_PPR_FIT, binding.pprFitInput.text?.toString())
            putIfNotBlank(KEY_INSALUBRIDADE_AGENT, binding.insalubridadeAgentInput.text?.toString())
            putIfNotBlank(KEY_INSALUBRIDADE_GRAU, binding.insalubridadeGrauInput.text?.toString())
            putIfNotBlank(KEY_PERICULOSIDADE_AGENT, binding.periculosidadeAgentInput.text?.toString())
            putIfNotBlank(KEY_PERICULOSIDADE_EXP, binding.periculosidadeExpInput.text?.toString())
            putIfNotBlank(KEY_PLANO_ACAO_RISCO, binding.planoAcaoRiscoInput.text?.toString())
            putIfNotBlank(KEY_PLANO_ACAO_ACAO, binding.planoAcaoAcaoInput.text?.toString())
            // Block 4
            putIfNotBlank(KEY_RESGATE_CENARIO, binding.resgateScenarioInput.text?.toString())
            putIfNotBlank(KEY_RESGATE_EQUIPE, binding.resgateEquipeInput.text?.toString())
            putIfNotBlank(KEY_INVENTARIO_SETOR, binding.inventarioSetorInput.text?.toString())
            putIfNotBlank(KEY_INVENTARIO_RISCO, binding.inventarioRiscoInput.text?.toString())
            putIfNotBlank(KEY_PGRTR_ATIVIDADE, binding.pgrtrAtividadeInput.text?.toString())
            putIfNotBlank(KEY_PGRTR_RISCO, binding.pgrtrRiscoInput.text?.toString())
            putIfNotBlank(KEY_MAPA_SETOR, binding.mapaSetorInput.text?.toString())
            putIfNotBlank(KEY_MAPA_RISCO_GRAVE, binding.mapaRiscoGraveInput.text?.toString())
        }
    }

    private fun MutableMap<String, String>.putIfNotBlank(key: String, value: String?) {
        val normalized = value.orEmpty().trim()
        if (normalized.isNotBlank()) {
            this[key] = normalized
        }
    }

    private fun validateSpecializedFields(): Boolean {
        binding.aetWorkstationInput.error = null
        binding.aetTaskInput.error = null
        binding.ltcatAgentInput.error = null
        binding.ltcatIntensityInput.error = null
        binding.ltcatHabitualityInput.error = null
        binding.ptIssuerInput.error = null
        binding.ptExecutorInput.error = null
        binding.ptAuthorizerInput.error = null
        binding.nrAssetInput.error = null
        binding.nrCriticalInput.error = null
        binding.nrDeadlineInput.error = null
        binding.pcaRiskInput.error = null
        binding.pcaMeasureInput.error = null
        binding.pprEpiInput.error = null
        binding.pprFitInput.error = null
        binding.insalubridadeAgentInput.error = null
        binding.insalubridadeGrauInput.error = null
        binding.periculosidadeAgentInput.error = null
        binding.periculosidadeExpInput.error = null
        binding.planoAcaoRiscoInput.error = null
        binding.planoAcaoAcaoInput.error = null
        // Block 4
        binding.resgateScenarioInput.error = null
        binding.resgateEquipeInput.error = null
        binding.inventarioSetorInput.error = null
        binding.inventarioRiscoInput.error = null
        binding.pgrtrAtividadeInput.error = null
        binding.pgrtrRiscoInput.error = null
        binding.mapaSetorInput.error = null
        binding.mapaRiscoGraveInput.error = null

        val requiredMessage = getString(com.example.anda.R.string.generic_doc_error_required)
        return when (docType) {
            "AET" -> {
                val workstation = binding.aetWorkstationInput.text?.toString().orEmpty().trim()
                val task = binding.aetTaskInput.text?.toString().orEmpty().trim()
                if (workstation.isBlank()) {
                    binding.aetWorkstationInput.error = requiredMessage
                    false
                } else if (task.isBlank()) {
                    binding.aetTaskInput.error = requiredMessage
                    false
                } else true
            }
            "LTCAT" -> {
                val agent = binding.ltcatAgentInput.text?.toString().orEmpty().trim()
                val intensity = binding.ltcatIntensityInput.text?.toString().orEmpty().trim()
                val habituality = binding.ltcatHabitualityInput.text?.toString().orEmpty().trim()
                when {
                    agent.isBlank() -> {
                        binding.ltcatAgentInput.error = requiredMessage
                        false
                    }
                    intensity.isBlank() -> {
                        binding.ltcatIntensityInput.error = requiredMessage
                        false
                    }
                    habituality.isBlank() -> {
                        binding.ltcatHabitualityInput.error = requiredMessage
                        false
                    }
                    else -> true
                }
            }
            "PT" -> {
                val issuer = binding.ptIssuerInput.text?.toString().orEmpty().trim()
                val executor = binding.ptExecutorInput.text?.toString().orEmpty().trim()
                val authorizer = binding.ptAuthorizerInput.text?.toString().orEmpty().trim()
                when {
                    issuer.isBlank() -> {
                        binding.ptIssuerInput.error = requiredMessage
                        false
                    }
                    executor.isBlank() -> {
                        binding.ptExecutorInput.error = requiredMessage
                        false
                    }
                    authorizer.isBlank() -> {
                        binding.ptAuthorizerInput.error = requiredMessage
                        false
                    }
                    else -> true
                }
            }
            "LAUDO_NR10", "LAUDO_NR12", "LAUDO_NR20" -> {
                val asset = binding.nrAssetInput.text?.toString().orEmpty().trim()
                val critical = binding.nrCriticalInput.text?.toString().orEmpty().trim()
                val deadline = binding.nrDeadlineInput.text?.toString().orEmpty().trim()
                when {
                    asset.isBlank() -> {
                        binding.nrAssetInput.error = requiredMessage
                        false
                    }
                    critical.isBlank() -> {
                        binding.nrCriticalInput.error = requiredMessage
                        false
                    }
                    deadline.isBlank() -> {
                        binding.nrDeadlineInput.error = requiredMessage
                        false
                    }
                    else -> true
                }
            }
            "PCA" -> {
                val risk = binding.pcaRiskInput.text?.toString().orEmpty().trim()
                val measure = binding.pcaMeasureInput.text?.toString().orEmpty().trim()
                when {
                    risk.isBlank() -> {
                        binding.pcaRiskInput.error = requiredMessage
                        false
                    }
                    measure.isBlank() -> {
                        binding.pcaMeasureInput.error = requiredMessage
                        false
                    }
                    else -> true
                }
            }
            "PPR" -> {
                val epi = binding.pprEpiInput.text?.toString().orEmpty().trim()
                val fit = binding.pprFitInput.text?.toString().orEmpty().trim()
                when {
                    epi.isBlank() -> {
                        binding.pprEpiInput.error = requiredMessage
                        false
                    }
                    fit.isBlank() -> {
                        binding.pprFitInput.error = requiredMessage
                        false
                    }
                    else -> true
                }
            }
            "INSALUBRIDADE" -> {
                val agent = binding.insalubridadeAgentInput.text?.toString().orEmpty().trim()
                val grau = binding.insalubridadeGrauInput.text?.toString().orEmpty().trim()
                when {
                    agent.isBlank() -> {
                        binding.insalubridadeAgentInput.error = requiredMessage
                        false
                    }
                    grau.isBlank() -> {
                        binding.insalubridadeGrauInput.error = requiredMessage
                        false
                    }
                    else -> true
                }
            }
            "PERICULOSIDADE" -> {
                val agent = binding.periculosidadeAgentInput.text?.toString().orEmpty().trim()
                val exp = binding.periculosidadeExpInput.text?.toString().orEmpty().trim()
                when {
                    agent.isBlank() -> {
                        binding.periculosidadeAgentInput.error = requiredMessage
                        false
                    }
                    exp.isBlank() -> {
                        binding.periculosidadeExpInput.error = requiredMessage
                        false
                    }
                    else -> true
                }
            }
            "PLANO_ACAO" -> {
                val risco = binding.planoAcaoRiscoInput.text?.toString().orEmpty().trim()
                val acao = binding.planoAcaoAcaoInput.text?.toString().orEmpty().trim()
                when {
                    risco.isBlank() -> {
                        binding.planoAcaoRiscoInput.error = requiredMessage
                        false
                    }
                    acao.isBlank() -> {
                        binding.planoAcaoAcaoInput.error = requiredMessage
                        false
                    }
                    else -> true
                }
            }
            "RESGATE" -> {
                val cenario = binding.resgateScenarioInput.text?.toString().orEmpty().trim()
                val equipe = binding.resgateEquipeInput.text?.toString().orEmpty().trim()
                when {
                    cenario.isBlank() -> {
                        binding.resgateScenarioInput.error = requiredMessage
                        false
                    }
                    equipe.isBlank() -> {
                        binding.resgateEquipeInput.error = requiredMessage
                        false
                    }
                    else -> true
                }
            }
            "INVENTARIO" -> {
                val setor = binding.inventarioSetorInput.text?.toString().orEmpty().trim()
                val risco = binding.inventarioRiscoInput.text?.toString().orEmpty().trim()
                when {
                    setor.isBlank() -> {
                        binding.inventarioSetorInput.error = requiredMessage
                        false
                    }
                    risco.isBlank() -> {
                        binding.inventarioRiscoInput.error = requiredMessage
                        false
                    }
                    else -> true
                }
            }
            "PGRTR" -> {
                val atividade = binding.pgrtrAtividadeInput.text?.toString().orEmpty().trim()
                val risco = binding.pgrtrRiscoInput.text?.toString().orEmpty().trim()
                when {
                    atividade.isBlank() -> {
                        binding.pgrtrAtividadeInput.error = requiredMessage
                        false
                    }
                    risco.isBlank() -> {
                        binding.pgrtrRiscoInput.error = requiredMessage
                        false
                    }
                    else -> true
                }
            }
            "MAPA_RISCO" -> {
                val setor = binding.mapaSetorInput.text?.toString().orEmpty().trim()
                val riscoGrave = binding.mapaRiscoGraveInput.text?.toString().orEmpty().trim()
                when {
                    setor.isBlank() -> {
                        binding.mapaSetorInput.error = requiredMessage
                        false
                    }
                    riscoGrave.isBlank() -> {
                        binding.mapaRiscoGraveInput.error = requiredMessage
                        false
                    }
                    else -> true
                }
            }
            else -> true
        }
    }

}

