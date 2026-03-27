package com.example.anda.feature.pcmso

import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.anda.core.stability.CrashShield
import com.example.anda.core.stability.safeLaunch
import com.example.anda.R
import com.example.anda.data.integration.ServiceIntegrationHelper
import com.example.anda.data.local.entity.DocumentEntity
import com.example.anda.data.repository.DocumentLocalRepository
import com.example.anda.data.repository.SstRepository
import com.example.anda.data.services.AutofillService
import com.example.anda.data.services.DocumentArtifactExportService
import com.example.anda.data.services.DocumentGenerationService
import com.example.anda.databinding.ActivityPcmsoBinding
import com.example.anda.domain.EmployeeExaminationType
import com.example.anda.domain.OccupationalHealthControlProgram
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

class PcmsoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPcmsoBinding
    private lateinit var integrationHelper: ServiceIntegrationHelper
    private val autofillService = AutofillService()
    private val generationService = DocumentGenerationService()
    private val artifactExportService = DocumentArtifactExportService()
    private val dtFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))

    private var currentDocumentId: String? = null
    private var currentPayload = ""
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
            
            if (cnpj.isNotBlank()) binding.pcmsoCnpjInput.setText(cnpj)
            if (name.isNotBlank()) binding.pcmsoCompanyNameInput.setText(name)
            if (cnae.isNotBlank()) binding.pcmsoCnaeInput.setText(cnae)
            
            // Track company scope for future operations
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
                    sourceDocument = "PCMSO",
                    companyScope = cleanCnpj
                )
            }
            
            binding.pcmsoCnpjStatusText.text = getString(R.string.doc_pick_company_selected_local)
            preloadLegalContext(cleanCnpj)
            applyRiskAutofill(cnae)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPcmsoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize SmartAutofill integration helper
        integrationHelper = ServiceIntegrationHelper(this, lifecycleScope)
        
        DocumentLocalRepository.initialize(this)
        SstRepository.initialize(this)
        applyRequestContextFromIntent()
        binding.pcmsoYearInput.setText(Calendar.getInstance().get(Calendar.YEAR).toString())
        binding.signPcmsoButton.isEnabled = false
        binding.exportPcmsoPdfButton.isEnabled = false
        setupRequiredFieldsSummary()

        binding.pcmsoPickCompanyButton.setOnClickListener {
            pickCompanyLauncher.launch(CompanyManagementActivity.buildPickIntent(this))
        }
        binding.pcmsoLookupCnpjButton.setOnClickListener {
            val cnpj = binding.pcmsoCnpjInput.text.toString().trim()
            if (PcmsoFormValidator.digitsOnly(cnpj).length != 14) {
                binding.pcmsoCnpjStatusText.text = getString(R.string.pcmso_lookup_invalid_cnpj)
                return@setOnClickListener
            }
            binding.pcmsoCnpjStatusText.text = getString(R.string.pcmso_lookup_searching)
            binding.pcmsoLookupCnpjButton.isEnabled = false
            lifecycleScope.launch { SstRepository.lookupCompanyByCnpj(cnpj) }
        }
        binding.generatePcmsoButton.setOnClickListener { generate() }
        binding.signPcmsoButton.setOnClickListener { signCurrentDocument() }
        binding.exportPcmsoPdfButton.setOnClickListener { exportPdf() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SstRepository.companyLookupState.collect { s ->
                    when {
                        s.isLoading -> {
                            binding.pcmsoCnpjStatusText.text = getString(R.string.pcmso_lookup_searching)
                            binding.pcmsoLookupCnpjButton.isEnabled = false
                        }
                        s.company != null -> {
                            val c = s.company
                            if (binding.pcmsoCompanyNameInput.text.isNullOrBlank()) binding.pcmsoCompanyNameInput.setText(c.legalName)
                            if (binding.pcmsoCnaeInput.text.isNullOrBlank()) binding.pcmsoCnaeInput.setText(c.cnae)
                            binding.pcmsoCnpjStatusText.text = c.legalName.take(40)
                            preloadLegalContext(c.cnpj.filter(Char::isDigit))
                            applyRiskAutofill(c.cnae)
                            binding.pcmsoLookupCnpjButton.isEnabled = true
                        }
                        !s.errorMessage.isNullOrBlank() -> {
                            binding.pcmsoCnpjStatusText.text = getString(R.string.pcmso_lookup_not_found)
                            binding.pcmsoLookupCnpjButton.isEnabled = true
                        }
                        else -> binding.pcmsoLookupCnpjButton.isEnabled = true
                    }
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Generate
    // ──────────────────────────────────────────────────────────────────────────

    private fun generate() {
        if (isOperationInProgress) return
        clearFieldErrors()

        val cnpj        = PcmsoFormValidator.digitsOnly(binding.pcmsoCnpjInput.text.toString())
        val companyName = binding.pcmsoCompanyNameInput.text.toString().trim()
        val cnae        = binding.pcmsoCnaeInput.text.toString().trim()
        val doctor      = binding.pcmsoDoctorInput.text.toString().trim()
        val crm         = binding.pcmsoCrmInput.text.toString().trim()
        val clinic      = binding.pcmsoClinicNameInput.text.toString().trim()
        val clinicCnpj  = PcmsoFormValidator.digitsOnly(binding.pcmsoClinicCnpjInput.text.toString())
            .ifBlank { getString(R.string.pcmso_default_clinic_cnpj) }
        val year = binding.pcmsoYearInput.text.toString().toIntOrNull()
            ?: Calendar.getInstance().get(Calendar.YEAR)
        val riskAssessment = binding.pcmsoRiskAssessmentInput.text.toString().trim()
            .ifBlank { getString(R.string.pcmso_default_risk_assessment) }
        val programName = binding.pcmsoProgramNameInput.text.toString().trim()
        val observations = binding.pcmsoObservationsInput.text.toString().trim().ifBlank { null }

        val selectedExams = collectSelectedExams()
        val hasAtLeastOneExam = selectedExams.isNotEmpty()

        val missingFields = PcmsoFormValidator.validate(
            PcmsoFormData(
                cnpj = cnpj,
                doctorName = doctor,
                programName = programName,
                hasAtLeastOneExam = hasAtLeastOneExam
            )
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
            ServiceIntegrationHelper.FieldIds.HAZARD_TYPE to cnae,
            ServiceIntegrationHelper.FieldIds.MEDICAL_EXAMINATION to doctor
        )
        integrationHelper.onFieldsChangedNormalized(
            fields = formData,
            sourceDocument = "PCMSO",
            companyScope = currentCompanyScope ?: cnpj
        )

        setOperationInProgress(true)
        safeLaunch("PcmsoActivity/generate") {
            try {
                val domainCompany = com.example.anda.domain.CompanyProfile(
                    cnpj = cnpj, legalName = companyName, tradeName = "", cnae = cnae
                )
                val schedule = buildExaminationSchedule()
                val pcmso = OccupationalHealthControlProgram(
                    id = "pcmso-${System.currentTimeMillis()}",
                    companyProfile = domainCompany,
                    year = year,
                    occupationalDoctor = doctor,
                    occupationalDoctorCRM = crm,
                    clinicName = clinic.ifBlank { getString(R.string.pcmso_default_clinic_name) },
                    clinicCNPJ = clinicCnpj,
                    riskAssessment = riskAssessment,
                    objectives = listOf(
                        getString(R.string.pcmso_objective_health),
                        getString(R.string.pcmso_objective_prevent),
                        getString(R.string.pcmso_objective_nr7)
                    ),
                    examinationSchedule = schedule,
                    complementaryExams = selectedExams,
                    responsibilities = programName,
                    observations = observations
                )
                val html = generationService.generatePCMSO(pcmso)
                val id = pcmso.id
                currentDocumentId = id
                currentPayload = html
                withContext(Dispatchers.IO) {
                    DocumentLocalRepository.saveDraft(
                        DocumentEntity(
                            documentId = id,
                            documentType = "PCMSO",
                            companyCnpj = cnpj,
                            title = getString(R.string.pcmso_document_title_template, companyName, year),
                            payloadJson = html,
                            contentHash = DocumentSignatureHelper.sha256(html),
                            sourceRequestCode = sourceRequestCode
                        )
                    )
                }
                binding.pcmsoPreviewText.text = html.take(600)
                binding.signPcmsoButton.isEnabled = true
                binding.exportPcmsoPdfButton.isEnabled = true
                binding.pcmsoStatusText.text = getString(R.string.pcmso_generated_template, id)
                updateRequiredFieldsSummary(emptyList())
            } finally {
                setOperationInProgress(false)
            }
        }
    }

    /** Collects all checked exam labels (including free-text "other" exams). */
    private fun collectSelectedExams(): List<String> {
        val exams = mutableListOf<String>()
        if (binding.pcmsoExamMedicalCheck.isChecked)    exams += getString(R.string.pcmso_exam_checkbox_medical)
        if (binding.pcmsoExamAudiologyCheck.isChecked)  exams += getString(R.string.pcmso_exam_checkbox_audiology)
        if (binding.pcmsoExamSpirometryCheck.isChecked) exams += getString(R.string.pcmso_exam_checkbox_spirometry)
        if (binding.pcmsoExamEcgCheck.isChecked)        exams += getString(R.string.pcmso_exam_checkbox_ecg)
        if (binding.pcmsoExamHemogramCheck.isChecked)   exams += getString(R.string.pcmso_exam_checkbox_hemogram2)
        if (binding.pcmsoExamVisualCheck.isChecked)     exams += getString(R.string.pcmso_exam_checkbox_visual)
        val other = binding.pcmsoOtherExamsInput.text.toString()
        other.split(",", ";").map { it.trim() }.filter { it.isNotBlank() }.forEach { exams += it }
        return exams
    }

    /** Builds the examination schedule map based on fixed types + user-selected return/removal flags. */
    private fun buildExaminationSchedule(): Map<EmployeeExaminationType, String> {
        val schedule = mutableMapOf(
            EmployeeExaminationType.ADMISSION to getString(R.string.pcmso_schedule_admission),
            EmployeeExaminationType.PERIODIC   to getString(R.string.pcmso_schedule_periodic),
            EmployeeExaminationType.EXIT       to getString(R.string.pcmso_schedule_exit)
        )
        if (binding.pcmsoScheduleReturnCheck.isChecked) {
            schedule[EmployeeExaminationType.RETURN] = getString(R.string.pcmso_schedule_return)
        }
        if (binding.pcmsoScheduleRemovalCheck.isChecked) {
            schedule[EmployeeExaminationType.REMOVAL] = getString(R.string.pcmso_schedule_removal)
        }
        return schedule
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Risk autofill
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Populates pcmsoRiskAssessmentInput (if blank) and pre-selects exam checkboxes
     * based on the risk level detected for the given CNAE.
     */
    private fun applyRiskAutofill(cnae: String) {
        if (cnae.isBlank()) return
        val riskLevel = autofillService.detectRiskLevelByCnae(cnae)
        if (binding.pcmsoRiskAssessmentInput.text.isNullOrBlank()) {
            binding.pcmsoRiskAssessmentInput.setText(
                autofillService.buildPcmsoRiskAssessmentSuggestion(cnae)
            )
        }
        // Pre-select checkboxes; never uncheck what user already checked
        val suggestedExams = autofillService.suggestPcmsoExamsForRisk(riskLevel)
        if (getString(R.string.pcmso_exam_checkbox_medical) in suggestedExams)
            binding.pcmsoExamMedicalCheck.isChecked = true
        if (getString(R.string.pcmso_exam_checkbox_audiology) in suggestedExams)
            binding.pcmsoExamAudiologyCheck.isChecked = true
        if (getString(R.string.pcmso_exam_checkbox_spirometry) in suggestedExams)
            binding.pcmsoExamSpirometryCheck.isChecked = true
        if (getString(R.string.pcmso_exam_checkbox_ecg) in suggestedExams)
            binding.pcmsoExamEcgCheck.isChecked = true
        if (getString(R.string.pcmso_exam_checkbox_hemogram2) in suggestedExams)
            binding.pcmsoExamHemogramCheck.isChecked = true
        if (getString(R.string.pcmso_exam_checkbox_visual) in suggestedExams)
            binding.pcmsoExamVisualCheck.isChecked = true
        // Auto-suggest program name if blank
        if (binding.pcmsoProgramNameInput.text.isNullOrBlank()) {
            val companyName = binding.pcmsoCompanyNameInput.text.toString().trim()
            val year = binding.pcmsoYearInput.text.toString().trim()
            if (companyName.isNotBlank()) {
                binding.pcmsoProgramNameInput.setText(
                    getString(R.string.pcmso_program_name_template, year, companyName)
                )
            }
        }
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
        
        if (requestCompanyCnpj.length == 14 && binding.pcmsoCnpjInput.text.isNullOrBlank()) {
            binding.pcmsoCnpjInput.setText(requestCompanyCnpj)
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
                    sourceDocument = "PCMSO",
                    companyScope = requestCompanyCnpj
                )
            }
            
            preloadLegalContext(requestCompanyCnpj)
        }
        if (requestCompanyName.isNotBlank() && binding.pcmsoCompanyNameInput.text.isNullOrBlank()) {
            binding.pcmsoCompanyNameInput.setText(requestCompanyName)
        }
        if (!sourceRequestCode.isNullOrBlank()) {
            binding.pcmsoStatusText.text = getString(
                R.string.service_request_linked_status_template,
                sourceRequestCode
            )
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Pre-load previous context
    // ──────────────────────────────────────────────────────────────────────────

    private fun preloadLegalContext(companyCnpjDigits: String) {
        if (companyCnpjDigits.length != 14) return
        safeLaunch("PcmsoActivity/preloadLegalContext") {
            val previous = withContext(Dispatchers.IO) {
                DocumentLocalRepository.findLatestByCompanyAndType(companyCnpjDigits, "PCMSO")
                    ?: DocumentLocalRepository.findLatestByCompanyCnpj(companyCnpjDigits)
            } ?: return@safeLaunch
            val context = autofillService.extractPreviousDocumentContext(
                payload = previous.payloadJson, signedBy = previous.signedBy
            )
            var applied = false
            if (binding.pcmsoDoctorInput.text.isNullOrBlank() && !context.responsibleName.isNullOrBlank()) {
                binding.pcmsoDoctorInput.setText(context.responsibleName); applied = true
            }
            if (binding.pcmsoCrmInput.text.isNullOrBlank() && !context.professionalRegistry.isNullOrBlank()) {
                binding.pcmsoCrmInput.setText(context.professionalRegistry); applied = true
            }
            if (binding.pcmsoClinicNameInput.text.isNullOrBlank() && !context.clinicName.isNullOrBlank()) {
                binding.pcmsoClinicNameInput.setText(context.clinicName); applied = true
            }
            if (binding.pcmsoClinicCnpjInput.text.isNullOrBlank() && !context.clinicCnpj.isNullOrBlank()) {
                binding.pcmsoClinicCnpjInput.setText(context.clinicCnpj); applied = true
            }
            if (applied) {
                binding.pcmsoCnpjStatusText.text = getString(
                    R.string.prefill_context_reused_template,
                    previous.documentType, previous.documentId
                )
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Sign & Export
    // ──────────────────────────────────────────────────────────────────────────

    private fun signCurrentDocument() {
        if (isOperationInProgress) return
        val documentId = currentDocumentId
        if (documentId.isNullOrBlank() || currentPayload.isBlank()) {
            showToast(R.string.pcmso_error_generate_before_sign); return
        }
        if (!DocumentSignatureHelper.canAuthenticate(this)) {
            showToast(R.string.pcmso_error_biometric_unavailable); return
        }
        val doctor = binding.pcmsoDoctorInput.text.toString().trim()
            .ifBlank { getString(R.string.pcmso_signed_by_fallback) }
        setOperationInProgress(true)
        DocumentSignatureHelper.signWithBiometric(
            activity = this, payload = currentPayload, signedBy = doctor,
            onSuccess = { record ->
                lifecycleScope.launch(Dispatchers.IO) {
                    runCatching {
                        DocumentLocalRepository.markSigned(
                            documentId = documentId, signedAt = record.signedAt,
                            signedBy = record.signedBy, contentHash = record.contentHash,
                            signatureB64 = record.signatureB64,
                            publicKeyFingerprint = record.publicKeyFingerprint,
                            signatureAlgorithm = "SHA256withRSA"
                        )
                    }.onFailure { CrashShield.recordRecoverableError("PcmsoActivity/markSigned", it) }
                }
                runOnUiThread {
                    binding.pcmsoStatusText.text = getString(
                        R.string.pcmso_signed_template, doctor, dtFmt.format(Date(record.signedAt))
                    )
                    setOperationInProgress(false)
                }
            },
            onError = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show(); setOperationInProgress(false) }
        )
    }

    private fun exportPdf() {
        if (isOperationInProgress) return
        if (currentPayload.isBlank()) { showToast(R.string.pcmso_error_generate_before_export); return }
        setOperationInProgress(true)
        safeLaunch("PcmsoActivity/pdf") {
            try {
                runCatching {
                    withContext(Dispatchers.IO) {
                        artifactExportService.exportPlainTextPdfAndAudit(
                            context = this@PcmsoActivity,
                            title = getString(R.string.pcmso_export_title),
                            body = currentPayload, documentId = currentDocumentId, filePrefix = "pcmso"
                        )
                    }
                }.onSuccess {
                    binding.pcmsoStatusText.text = getString(
                        R.string.pcmso_export_result_template,
                        it.pdfFile.absolutePath,
                        it.auditFile?.absolutePath ?: getString(R.string.audit_not_generated)
                    )
                }.onFailure { showToast(R.string.pcmso_error_export_failed) }
            } finally { setOperationInProgress(false) }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Form validation UI
    // ──────────────────────────────────────────────────────────────────────────

    private fun setupRequiredFieldsSummary() {
        val updater = { updateRequiredFieldsSummary(currentMissingFields()) }
        binding.pcmsoCnpjInput.doAfterTextChanged { updater() }
        binding.pcmsoCnpjInput.doAfterTextChanged { value ->
            val digits = value?.toString().orEmpty().filter(Char::isDigit)
            when {
                digits.length == 14 && digits != lastPrefilledCnpj -> {
                    lastPrefilledCnpj = digits; preloadLegalContext(digits)
                }
                digits.length < 14 -> lastPrefilledCnpj = null
            }
        }
        binding.pcmsoDoctorInput.doAfterTextChanged { updater() }
        binding.pcmsoProgramNameInput.doAfterTextChanged { updater() }
        binding.pcmsoCnaeInput.doAfterTextChanged { value ->
            val cnae = value?.toString().orEmpty().trim()
            if (cnae.length >= 2) applyRiskAutofill(cnae)
        }
        updater()
    }

    private fun currentMissingFields(): List<PcmsoField> = PcmsoFormValidator.validate(
        PcmsoFormData(
            cnpj = binding.pcmsoCnpjInput.text?.toString().orEmpty(),
            doctorName = binding.pcmsoDoctorInput.text?.toString().orEmpty().trim(),
            programName = binding.pcmsoProgramNameInput.text?.toString().orEmpty(),
            hasAtLeastOneExam = collectSelectedExams().isNotEmpty()
        )
    )

    private fun updateRequiredFieldsSummary(missingFields: List<PcmsoField>) {
        binding.pcmsoRequiredSummaryText.text = if (missingFields.isEmpty()) {
            getString(R.string.pcmso_required_ready)
        } else {
            val labels = missingFields.joinToString(", ") { getString(fieldLabelRes(it)) }
            getString(R.string.pcmso_required_pending_template, labels)
        }
    }

    private fun showValidationErrors(missingFields: List<PcmsoField>) {
        clearFieldErrors()
        missingFields.forEach {
            when (it) {
                PcmsoField.COMPANY_CNPJ   -> binding.pcmsoCnpjInput.error = getString(R.string.pcmso_error_cnpj_invalid_field)
                PcmsoField.DOCTOR_NAME    -> binding.pcmsoDoctorInput.error = getString(R.string.pcmso_error_doctor_required_field)
                PcmsoField.PROGRAM_NAME   -> binding.pcmsoProgramNameInput.error = getString(R.string.pcmso_error_program_name_required)
                PcmsoField.AT_LEAST_ONE_EXAM -> { /* no single field to highlight; toast is enough */ }
            }
        }
        when (missingFields.first()) {
            PcmsoField.COMPANY_CNPJ   -> binding.pcmsoCnpjInput.requestFocus()
            PcmsoField.DOCTOR_NAME    -> binding.pcmsoDoctorInput.requestFocus()
            PcmsoField.PROGRAM_NAME   -> binding.pcmsoProgramNameInput.requestFocus()
            PcmsoField.AT_LEAST_ONE_EXAM -> binding.pcmsoExamMedicalCheck.requestFocus()
        }
    }

    private fun clearFieldErrors() {
        binding.pcmsoCnpjInput.error = null
        binding.pcmsoDoctorInput.error = null
        binding.pcmsoProgramNameInput.error = null
    }

    @StringRes
    private fun validationMessageFor(field: PcmsoField): Int = when (field) {
        PcmsoField.COMPANY_CNPJ      -> R.string.pcmso_error_cnpj_invalid_message
        PcmsoField.DOCTOR_NAME       -> R.string.pcmso_error_doctor_required_message
        PcmsoField.PROGRAM_NAME      -> R.string.pcmso_error_program_name_required
        PcmsoField.AT_LEAST_ONE_EXAM -> R.string.pcmso_error_no_exam_selected
    }

    @StringRes
    private fun fieldLabelRes(field: PcmsoField): Int = when (field) {
        PcmsoField.COMPANY_CNPJ      -> R.string.pcmso_required_label_cnpj
        PcmsoField.DOCTOR_NAME       -> R.string.pcmso_required_label_doctor
        PcmsoField.PROGRAM_NAME      -> R.string.pcmso_required_label_program_name
        PcmsoField.AT_LEAST_ONE_EXAM -> R.string.pcmso_required_label_exams
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun setOperationInProgress(inProgress: Boolean) {
        isOperationInProgress = inProgress
        binding.generatePcmsoButton.isEnabled = !inProgress
        binding.signPcmsoButton.isEnabled = !inProgress && currentPayload.isNotBlank()
        binding.exportPcmsoPdfButton.isEnabled = !inProgress && currentPayload.isNotBlank()
    }

    private fun showToast(@StringRes resId: Int) =
        Toast.makeText(this, getString(resId), Toast.LENGTH_SHORT).show()
}
