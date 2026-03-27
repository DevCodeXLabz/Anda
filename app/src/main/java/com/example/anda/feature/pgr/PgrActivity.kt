package com.example.anda.feature.pgr

import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.anda.R
import com.example.anda.core.stability.CrashShield
import com.example.anda.core.stability.safeLaunch
import com.example.anda.data.integration.ServiceIntegrationHelper
import com.example.anda.data.local.entity.DocumentEntity
import com.example.anda.data.repository.DocumentLocalRepository
import com.example.anda.data.repository.SstRepository
import com.example.anda.data.services.AutofillService
import com.example.anda.data.services.DocumentArtifactExportService
import com.example.anda.data.services.DocumentGenerationService
import com.example.anda.databinding.ActivityPgrBinding
import com.example.anda.domain.RiskManagementProgram
import com.example.anda.feature.companies.CompanyManagementActivity
import com.example.anda.feature.requests.ServiceRequestIntentContract
import com.example.anda.feature.security.DocumentSignatureHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PgrActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPgrBinding
    private lateinit var integrationHelper: ServiceIntegrationHelper
    private val autofillService = AutofillService()
    private val generationService = DocumentGenerationService()
    private val artifactExportService = DocumentArtifactExportService()
    private val dtFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))

    private var currentDocumentId: String? = null
    private var currentPayload: String = ""
    private var isOperationInProgress = false
    private var lastPrefilledCnpj: String? = null
    private var sourceRequestCode: String? = null
    private var currentCompanyScope: String? = null  // Track current company for scoped operations

    private val pickCompanyLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val cnpj = data.getStringExtra(CompanyManagementActivity.RESULT_COMPANY_CNPJ).orEmpty()
            val name = data.getStringExtra(CompanyManagementActivity.RESULT_COMPANY_LEGAL_NAME).orEmpty()
            val cnae = data.getStringExtra(CompanyManagementActivity.RESULT_COMPANY_CNAE).orEmpty()
            
            // Update UI fields
            if (cnpj.isNotBlank()) binding.pgrCnpjInput.setText(cnpj)
            if (name.isNotBlank()) binding.pgrCompanyNameInput.setText(name)
            if (cnae.isNotBlank()) binding.pgrCnaeInput.setText(cnae)
            
            // Track company scope for future operations (use clean CNPJ as scope)
            val cleanCnpj = cnpj.filter(Char::isDigit)
            currentCompanyScope = cleanCnpj
            
            // Publish company selection to autofill system
            if (cleanCnpj.isNotEmpty()) {
                val companyFields = mapOf(
                    ServiceIntegrationHelper.FieldIds.COMPANY_CNPJ to cnpj,
                    ServiceIntegrationHelper.FieldIds.COMPANY_NAME to name
                )
                integrationHelper.onFieldsChangedNormalized(
                    fields = companyFields,
                    sourceDocument = "PGR",
                    companyScope = cleanCnpj
                )
            }
            
            binding.pgrCnpjStatusText.text = getString(R.string.doc_pick_company_selected_local)
            preloadLegalContext(cleanCnpj)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPgrBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize SmartAutofill integration helper
        integrationHelper = ServiceIntegrationHelper(this, lifecycleScope)

        DocumentLocalRepository.initialize(this)
        SstRepository.initialize(this)
        applyRequestContextFromIntent()

        // Preenche o ano atual automaticamente
        binding.pgrYearInput.setText(Calendar.getInstance().get(Calendar.YEAR).toString())

        binding.exportPgrPdfButton.isEnabled = false
        binding.signPgrButton.isEnabled = false

        binding.pgrPickCompanyButton.setOnClickListener {
            pickCompanyLauncher.launch(CompanyManagementActivity.buildPickIntent(this))
        }
        binding.pgrStatusText.text = getString(R.string.pgr_status_waiting)
        setupRequiredFieldsSummary()

        binding.pgrLookupCnpjButton.setOnClickListener {
            val cnpj = binding.pgrCnpjInput.text.toString().trim()
            if (PgrFormValidator.digitsOnly(cnpj).length != 14) {
                binding.pgrCnpjStatusText.text = getString(R.string.pgr_lookup_invalid_cnpj)
                return@setOnClickListener
            }
            binding.pgrCnpjStatusText.text = getString(R.string.pgr_lookup_searching_company)
            binding.pgrLookupCnpjButton.isEnabled = false
            lifecycleScope.launch { SstRepository.lookupCompanyByCnpj(cnpj) }
        }

        binding.generatePgrButton.setOnClickListener { generateAndSave() }
        binding.signPgrButton.setOnClickListener { signCurrentDocument() }
        binding.exportPgrPdfButton.setOnClickListener { exportPdf() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SstRepository.companyLookupState.collect { state ->
                    when {
                        state.isLoading -> {
                            binding.pgrCnpjStatusText.text = getString(R.string.pgr_lookup_searching)
                            binding.pgrLookupCnpjButton.isEnabled = false
                        }
                        state.company != null -> {
                            val c = state.company
                            if (binding.pgrCompanyNameInput.text.isNullOrBlank())
                                binding.pgrCompanyNameInput.setText(c.legalName)
                            if (binding.pgrCnaeInput.text.isNullOrBlank())
                                binding.pgrCnaeInput.setText(c.cnae)
                            val grau = state.riskGrade ?: 2
                            binding.pgrCnpjStatusText.text = getString(
                                R.string.pgr_lookup_company_grade_template,
                                c.legalName.take(40),
                                grau
                            )
                            preloadLegalContext(c.cnpj.filter(Char::isDigit))
                            binding.pgrLookupCnpjButton.isEnabled = true
                        }
                        !state.errorMessage.isNullOrBlank() -> {
                            binding.pgrCnpjStatusText.text = getString(
                                R.string.pgr_lookup_not_found_template,
                                state.errorMessage
                            )
                            binding.pgrLookupCnpjButton.isEnabled = true
                        }
                        else -> binding.pgrLookupCnpjButton.isEnabled = true
                    }
                }
            }
        }
    }

    private fun generateAndSave() {
        if (isOperationInProgress) return
        clearFieldErrors()

        val cnpj = PgrFormValidator.digitsOnly(binding.pgrCnpjInput.text.toString())
        val companyName = binding.pgrCompanyNameInput.text.toString().trim()
        val cnae = binding.pgrCnaeInput.text.toString().trim()
        val technician = binding.pgrTechnicianInput.text.toString().trim()
        val crea = binding.pgrCreaInput.text.toString().trim().ifBlank { null }
        val yearStr = binding.pgrYearInput.text.toString().trim()
        val year = yearStr.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)

        val missingFields = PgrFormValidator.validate(
            PgrFormData(cnpj = cnpj, companyName = companyName, technician = technician)
        )
        if (missingFields.isNotEmpty()) {
            showValidationErrors(missingFields)
            updateRequiredFieldsSummary(missingFields)
            showToast(validationMessageFor(missingFields.first()))
            return
        }

        // Publish form data to autofill system before generation
        val formData = mapOf(
            ServiceIntegrationHelper.FieldIds.COMPANY_CNPJ to cnpj,
            ServiceIntegrationHelper.FieldIds.COMPANY_NAME to companyName,
            ServiceIntegrationHelper.FieldIds.HAZARD_TYPE to cnae
        )
        integrationHelper.onFieldsChangedNormalized(
            fields = formData,
            sourceDocument = "PGR",
            companyScope = currentCompanyScope ?: cnpj
        )

        setOperationInProgress(true)
        safeLaunch("PgrActivity/generate") {
            try {
                val domainCompany = com.example.anda.domain.CompanyProfile(
                    cnpj = cnpj,
                    legalName = companyName,
                    tradeName = "",
                    cnae = cnae
                )
                val risks = autofillService.detectRisksByCnae(cnae)
                val now = System.currentTimeMillis()
                val pgr = RiskManagementProgram(
                    id = "pgr-$now",
                    companyProfile = domainCompany,
                    year = year,
                    technician = technician,
                    technicanCREA = crea,
                    identifiedRisks = risks,
                    controlMeasures = risks.associate { risk ->
                        risk.name to risk.controlMeasures
                    },
                    timeline = binding.pgrTimelineInput.text.toString().trim().ifBlank { null },
                    observations = binding.pgrObservationsInput.text.toString().trim().ifBlank { null }
                )

                val html = generationService.generatePGR(pgr)
                val hash = DocumentSignatureHelper.sha256(html)
                currentDocumentId = pgr.id
                currentPayload = html

                withContext(Dispatchers.IO) {
                    DocumentLocalRepository.saveDraft(
                        DocumentEntity(
                            documentId = pgr.id,
                            documentType = "PGR",
                            companyCnpj = cnpj,
                            title = getString(R.string.pgr_document_title_template, companyName, year),
                            payloadJson = html,
                            contentHash = hash,
                            sourceRequestCode = sourceRequestCode
                        )
                    )
                }

                binding.pgrPreviewText.text = html.take(700)
                binding.signPgrButton.isEnabled = true
                binding.exportPgrPdfButton.isEnabled = true
                binding.pgrStatusText.text = getString(R.string.pgr_generated_template, pgr.id)
                updateRequiredFieldsSummary(emptyList())
            } finally {
                setOperationInProgress(false)
            }
        }
    }

    private fun preloadLegalContext(companyCnpjDigits: String) {
        if (companyCnpjDigits.length != 14) return

        safeLaunch("PgrActivity/preloadLegalContext") {
            val previous = withContext(Dispatchers.IO) {
                DocumentLocalRepository.findLatestByCompanyAndType(companyCnpjDigits, "PGR")
                    ?: DocumentLocalRepository.findLatestByCompanyCnpj(companyCnpjDigits)
            } ?: return@safeLaunch

            val context = autofillService.extractPreviousDocumentContext(
                payload = previous.payloadJson,
                signedBy = previous.signedBy
            )

            var applied = false
            if (binding.pgrTechnicianInput.text.isNullOrBlank() && !context.responsibleName.isNullOrBlank()) {
                binding.pgrTechnicianInput.setText(context.responsibleName)
                applied = true
            }
            if (binding.pgrCreaInput.text.isNullOrBlank() && !context.professionalRegistry.isNullOrBlank()) {
                binding.pgrCreaInput.setText(context.professionalRegistry)
                applied = true
            }

            if (applied) {
                binding.pgrCnpjStatusText.text = getString(
                    R.string.prefill_context_reused_template,
                    previous.documentType,
                    previous.documentId
                )
            }
        }
    }

    private fun clearFieldErrors() {
        binding.pgrCnpjInput.error = null
        binding.pgrCompanyNameInput.error = null
        binding.pgrTechnicianInput.error = null
    }

    private fun applyRequestContextFromIntent() {
        sourceRequestCode = intent.getStringExtra(ServiceRequestIntentContract.EXTRA_REQUEST_CODE)
            ?.trim()
            ?.ifBlank { null }
        val requestCompanyCnpj = intent.getStringExtra(ServiceRequestIntentContract.EXTRA_REQUEST_COMPANY_CNPJ)
            .orEmpty()
            .filter(Char::isDigit)
        val requestCompanyName = intent.getStringExtra(ServiceRequestIntentContract.EXTRA_REQUEST_COMPANY_NAME)
            .orEmpty()
            .trim()
        
        if (requestCompanyCnpj.length == 14 && binding.pgrCnpjInput.text.isNullOrBlank()) {
            binding.pgrCnpjInput.setText(requestCompanyCnpj)
            lastPrefilledCnpj = requestCompanyCnpj
            currentCompanyScope = requestCompanyCnpj
            
            // Publish intent company context to autofill system
            if (requestCompanyName.isNotBlank()) {
                val companyFields = mapOf(
                    ServiceIntegrationHelper.FieldIds.COMPANY_CNPJ to requestCompanyCnpj,
                    ServiceIntegrationHelper.FieldIds.COMPANY_NAME to requestCompanyName
                )
                integrationHelper.onFieldsChangedNormalized(
                    fields = companyFields,
                    sourceDocument = "PGR",
                    companyScope = requestCompanyCnpj
                )
            }
            
            preloadLegalContext(requestCompanyCnpj)
        }
        if (requestCompanyName.isNotBlank() && binding.pgrCompanyNameInput.text.isNullOrBlank()) {
            binding.pgrCompanyNameInput.setText(requestCompanyName)
        }
        if (!sourceRequestCode.isNullOrBlank()) {
            binding.pgrStatusText.text = getString(R.string.service_request_linked_status_template, sourceRequestCode)
        }
    }

    private fun showValidationErrors(missingFields: List<PgrField>) {
        clearFieldErrors()
        missingFields.forEach {
            when (it) {
                PgrField.COMPANY_CNPJ -> binding.pgrCnpjInput.error = getString(R.string.pgr_error_cnpj_invalid_field)
                PgrField.COMPANY_NAME -> binding.pgrCompanyNameInput.error = getString(R.string.pgr_error_required_field)
                PgrField.TECHNICIAN -> binding.pgrTechnicianInput.error = getString(R.string.pgr_error_required_field)
            }
        }
        when (missingFields.first()) {
            PgrField.COMPANY_CNPJ -> binding.pgrCnpjInput.requestFocus()
            PgrField.COMPANY_NAME -> binding.pgrCompanyNameInput.requestFocus()
            PgrField.TECHNICIAN -> binding.pgrTechnicianInput.requestFocus()
        }
    }

    private fun setupRequiredFieldsSummary() {
        val updater = {
            val missing = PgrFormValidator.validate(
                PgrFormData(
                    cnpj = binding.pgrCnpjInput.text?.toString().orEmpty(),
                    companyName = binding.pgrCompanyNameInput.text?.toString().orEmpty().trim(),
                    technician = binding.pgrTechnicianInput.text?.toString().orEmpty().trim()
                )
            )
            updateRequiredFieldsSummary(missing)
        }
        binding.pgrCnpjInput.doAfterTextChanged { updater() }
        binding.pgrCnpjInput.doAfterTextChanged { value ->
            val digits = value?.toString().orEmpty().filter(Char::isDigit)
            when {
                digits.length == 14 && digits != lastPrefilledCnpj -> {
                    lastPrefilledCnpj = digits
                    preloadLegalContext(digits)
                }
                digits.length < 14 -> {
                    lastPrefilledCnpj = null
                }
            }
        }
        binding.pgrCompanyNameInput.doAfterTextChanged { updater() }
        binding.pgrTechnicianInput.doAfterTextChanged { updater() }
        updater()
    }

    private fun updateRequiredFieldsSummary(missingFields: List<PgrField>) {
        binding.pgrRequiredSummaryText.text = if (missingFields.isEmpty()) {
            getString(R.string.pgr_required_ready)
        } else {
            val labels = missingFields.joinToString(", ") { getString(fieldLabelRes(it)) }
            getString(R.string.pgr_required_pending_template, labels)
        }
    }

    @StringRes
    private fun validationMessageFor(field: PgrField): Int {
        return when (field) {
            PgrField.COMPANY_CNPJ -> R.string.pgr_error_message_cnpj_invalid
            PgrField.COMPANY_NAME -> R.string.pgr_error_message_company_name_required
            PgrField.TECHNICIAN -> R.string.pgr_error_message_technician_required
        }
    }

    @StringRes
    private fun fieldLabelRes(field: PgrField): Int {
        return when (field) {
            PgrField.COMPANY_CNPJ -> R.string.pgr_required_label_cnpj
            PgrField.COMPANY_NAME -> R.string.pgr_required_label_company_name
            PgrField.TECHNICIAN -> R.string.pgr_required_label_technician
        }
    }

    private fun signCurrentDocument() {
        if (isOperationInProgress) return
        val documentId = currentDocumentId
        if (documentId.isNullOrBlank() || currentPayload.isBlank()) {
            showToast(R.string.pgr_error_generate_before_sign)
            return
        }
        if (!DocumentSignatureHelper.canAuthenticate(this)) {
            showToast(R.string.pgr_error_biometric_unavailable)
            return
        }
        val technician = binding.pgrTechnicianInput.text.toString().trim()
            .ifBlank { getString(R.string.pgr_signed_by_fallback) }
        setOperationInProgress(true)
        DocumentSignatureHelper.signWithBiometric(
            activity = this, payload = currentPayload, signedBy = technician,
            onSuccess = { record ->
                lifecycleScope.launch(Dispatchers.IO) {
                    runCatching {
                        DocumentLocalRepository.markSigned(documentId = documentId, signedAt = record.signedAt, signedBy = record.signedBy, contentHash = record.contentHash, signatureB64 = record.signatureB64, publicKeyFingerprint = record.publicKeyFingerprint, signatureAlgorithm = "SHA256withRSA")
                    }.onFailure { CrashShield.recordRecoverableError("PgrActivity/markSigned", it) }
                }
                runOnUiThread {
                    binding.pgrStatusText.text = getString(
                        R.string.pgr_signed_status_template,
                        technician,
                        dtFmt.format(Date(record.signedAt))
                    )
                    setOperationInProgress(false)
                }
            },
            onError = {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                setOperationInProgress(false)
            }
        )
    }

    private fun exportPdf() {
        if (isOperationInProgress) return
        if (currentPayload.isBlank()) {
            showToast(R.string.pgr_error_generate_before_export)
            return
        }
        setOperationInProgress(true)
        safeLaunch("PgrActivity/exportPdf") {
            try {
                runCatching {
                    withContext(Dispatchers.IO) {
                        artifactExportService.exportPlainTextPdfAndAudit(
                            context = this@PgrActivity,
                            title = getString(R.string.pgr_export_title),
                            body = currentPayload,
                            documentId = currentDocumentId,
                            filePrefix = "pgr"
                        )
                    }
                }.onSuccess { result ->
                    binding.pgrStatusText.text = getString(
                        R.string.pgr_export_status_template,
                        result.pdfFile.absolutePath,
                        result.auditFile?.absolutePath ?: getString(R.string.audit_not_generated)
                    )
                }.onFailure {
                    showToast(R.string.pgr_error_export_failed)
                }
            } finally {
                setOperationInProgress(false)
            }
        }
    }

    private fun setOperationInProgress(inProgress: Boolean) {
        isOperationInProgress = inProgress
        binding.generatePgrButton.isEnabled = !inProgress
        binding.signPgrButton.isEnabled = !inProgress && currentPayload.isNotBlank()
        binding.exportPgrPdfButton.isEnabled = !inProgress && currentPayload.isNotBlank()
    }

    private fun showToast(@StringRes resId: Int) {
        Toast.makeText(this, getString(resId), Toast.LENGTH_SHORT).show()
    }
}

