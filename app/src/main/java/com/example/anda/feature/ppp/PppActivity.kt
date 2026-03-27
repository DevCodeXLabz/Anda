package com.example.anda.feature.ppp

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
import com.example.anda.data.integration.ServiceIntegrationHelper
import com.example.anda.core.stability.CrashShield
import com.example.anda.core.stability.safeLaunch
import com.example.anda.data.local.entity.DocumentEntity
import com.example.anda.data.repository.DocumentLocalRepository
import com.example.anda.data.repository.SstRepository
import com.example.anda.data.services.AutofillService
import com.example.anda.data.services.DocumentArtifactExportService
import com.example.anda.data.services.DocumentGenerationService
import com.example.anda.databinding.ActivityPppBinding
import com.example.anda.feature.companies.CompanyManagementActivity
import com.example.anda.feature.employees.EmployeeManagementActivity
import com.example.anda.feature.requests.ServiceRequestIntentContract
import com.example.anda.feature.security.DocumentSignatureHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PppActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPppBinding
    private val autofillService = AutofillService()
    private val generationService = DocumentGenerationService()
    private val artifactExportService = DocumentArtifactExportService()
    private val dtFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))

    private var currentDocumentId: String? = null
    private var currentPayload = ""
    private var isOperationInProgress = false
    private var lastPrefilledCnpj: String? = null
    private var sourceRequestCode: String? = null
    private lateinit var integrationHelper: ServiceIntegrationHelper
    private var currentCompanyScope: String? = null

    private val pickCompanyLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val cnpj = data.getStringExtra(CompanyManagementActivity.RESULT_COMPANY_CNPJ).orEmpty()
            val name = data.getStringExtra(CompanyManagementActivity.RESULT_COMPANY_LEGAL_NAME).orEmpty()
            val cnae = data.getStringExtra(CompanyManagementActivity.RESULT_COMPANY_CNAE).orEmpty()
            if (cnpj.isNotBlank()) binding.pppCnpjInput.setText(cnpj)
            if (name.isNotBlank()) binding.pppCompanyNameInput.setText(name)
            if (cnae.isNotBlank()) binding.pppCnaeInput.setText(cnae)
            // Track and publish company selection
            val cleanCnpj = cnpj.filter(Char::isDigit)
            currentCompanyScope = cleanCnpj
            if (cleanCnpj.isNotBlank()) {
                val companyFields = mapOf(
                    ServiceIntegrationHelper.FieldIds.COMPANY_CNPJ to cnpj,
                    ServiceIntegrationHelper.FieldIds.COMPANY_NAME to name
                )
                integrationHelper.onFieldsChangedNormalized(
                    fields = companyFields,
                    sourceDocument = "PPP",
                    companyScope = cleanCnpj
                )
            }
            binding.pppCnpjStatusText.text = getString(R.string.doc_pick_company_selected_local)
            preloadLegalContext(cleanCnpj)
        }
    }

    private val pickEmployeeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val name    = data.getStringExtra(EmployeeManagementActivity.RESULT_EMPLOYEE_NAME).orEmpty()
            val cpf     = data.getStringExtra(EmployeeManagementActivity.RESULT_EMPLOYEE_CPF).orEmpty()
            val role    = data.getStringExtra(EmployeeManagementActivity.RESULT_EMPLOYEE_ROLE).orEmpty()
            val empCnpj = data.getStringExtra(EmployeeManagementActivity.RESULT_EMPLOYEE_COMPANY_CNPJ).orEmpty()
            val empComp = data.getStringExtra(EmployeeManagementActivity.RESULT_EMPLOYEE_COMPANY_NAME).orEmpty()
            if (name.isNotBlank()) binding.pppEmployeeNameInput.setText(name)
            if (cpf.isNotBlank())  binding.pppEmployeeCpfInput.setText(cpf)
            if (role.isNotBlank()) binding.pppEmployeeRoleInput.setText(role)
            // Backfill company from employee record when CNPJ still blank
            if (empCnpj.isNotBlank() && binding.pppCnpjInput.text.isNullOrBlank()) {
                binding.pppCnpjInput.setText(empCnpj)
                binding.pppCompanyNameInput.setText(empComp)
            }
            // Publish employee selection to autofill (scoped to employee company if available)
            val empScope = empCnpj.filter { it.isDigit() }.ifBlank { currentCompanyScope }
            if (!empScope.isNullOrBlank()) {
                val employeeFields = mapOf(
                    ServiceIntegrationHelper.FieldIds.EMPLOYEE_NAME to name,
                    ServiceIntegrationHelper.FieldIds.EMPLOYEE_CPF to cpf,
                    ServiceIntegrationHelper.FieldIds.EMPLOYEE_DEPARTMENT to role
                )
                integrationHelper.onFieldsChangedNormalized(
                    fields = employeeFields,
                    sourceDocument = "PPP",
                    companyScope = empScope
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPppBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Initialize SmartAutofill integration helper
        integrationHelper = ServiceIntegrationHelper(this, lifecycleScope)
        DocumentLocalRepository.initialize(this)
        SstRepository.initialize(this)
        applyRequestContextFromIntent()
        binding.signPppButton.isEnabled = false
        binding.exportPppPdfButton.isEnabled = false
        binding.pppStatusText.text = getString(R.string.ppp_status_waiting)
        setupRequiredFieldsSummary()

        binding.pppPickCompanyButton.setOnClickListener {
            pickCompanyLauncher.launch(CompanyManagementActivity.buildPickIntent(this))
        }

        binding.pppPickEmployeeButton.setOnClickListener {
            pickEmployeeLauncher.launch(
                EmployeeManagementActivity.buildPickIntent(this,
                    binding.pppCnpjInput.text.toString().filter { it.isDigit() })
            )
        }

        binding.pppLookupCnpjButton.setOnClickListener {
            val cnpj = binding.pppCnpjInput.text.toString().trim()
            if (PppFormValidator.digitsOnly(cnpj).length != 14) {
                binding.pppCnpjStatusText.text = getString(R.string.ppp_lookup_invalid_cnpj)
                return@setOnClickListener
            }
            binding.pppCnpjStatusText.text = getString(R.string.ppp_lookup_searching)
            binding.pppLookupCnpjButton.isEnabled = false
            lifecycleScope.launch { SstRepository.lookupCompanyByCnpj(cnpj) }
        }
        binding.generatePppButton.setOnClickListener { generate() }
        binding.signPppButton.setOnClickListener { signCurrentDocument() }
        binding.exportPppPdfButton.setOnClickListener { exportPdf() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SstRepository.companyLookupState.collect { s ->
                    when {
                        s.isLoading -> {
                            binding.pppCnpjStatusText.text = getString(R.string.ppp_lookup_searching)
                            binding.pppLookupCnpjButton.isEnabled = false
                        }
                        s.company != null -> {
                            val c = s.company
                            if (binding.pppCompanyNameInput.text.isNullOrBlank()) binding.pppCompanyNameInput.setText(c.legalName)
                            if (binding.pppCnaeInput.text.isNullOrBlank()) binding.pppCnaeInput.setText(c.cnae)
                            binding.pppCnpjStatusText.text = c.legalName.take(40)
                            preloadLegalContext(c.cnpj.filter(Char::isDigit))
                            binding.pppLookupCnpjButton.isEnabled = true
                        }
                        !s.errorMessage.isNullOrBlank() -> {
                            binding.pppCnpjStatusText.text = getString(R.string.ppp_lookup_not_found)
                            binding.pppLookupCnpjButton.isEnabled = true
                        }
                        else -> binding.pppLookupCnpjButton.isEnabled = true
                    }
                }
            }
        }
    }

    private fun generate() {
        if (isOperationInProgress) return
        clearFieldErrors()

        val cnpj = PppFormValidator.digitsOnly(binding.pppCnpjInput.text.toString())
        val companyName = binding.pppCompanyNameInput.text.toString().trim()
        val cnae = binding.pppCnaeInput.text.toString().trim()
        val employee = binding.pppEmployeeNameInput.text.toString().trim()
        val employeeCpf = PppFormValidator.digitsOnly(binding.pppEmployeeCpfInput.text.toString())
        val role = binding.pppEmployeeRoleInput.text.toString().trim()
        val sector = binding.pppSectorInput.text.toString().trim()
        val admission = binding.pppAdmissionDateInput.text.toString().trim()
        val dismissal = binding.pppDismissalDateInput.text.toString().trim()
        val activities = binding.pppActivitiesInput.text.toString().trim()
        val medicalExams = binding.pppMedicalExamsInput.text.toString().trim()
        val responsible = binding.pppResponsibleNameInput.text.toString().trim()
        val responsibleCpf = PppFormValidator.digitsOnly(binding.pppResponsibleCpfInput.text.toString())

        val missingFields = PppFormValidator.validate(
            PppFormData(
                cnpj = cnpj,
                employeeName = employee,
                admissionDate = admission,
                responsibleName = responsible
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
            ServiceIntegrationHelper.FieldIds.EMPLOYEE_CPF to employeeCpf,
            ServiceIntegrationHelper.FieldIds.EMPLOYEE_NAME to employee,
            ServiceIntegrationHelper.FieldIds.EMPLOYEE_DEPARTMENT to sector
        )
        integrationHelper.onFieldsChangedNormalized(
            fields = formData,
            sourceDocument = "PPP",
            companyScope = currentCompanyScope ?: cnpj
        )

        setOperationInProgress(true)
        safeLaunch("PppActivity/generate") {
            try {
                val risks = autofillService.detectRisksByCnae(cnae)
                val html = generationService.generatePPP(
                    companyCnpj = cnpj,
                    companyName = companyName,
                    companyCnae = cnae,
                    employeeName = employee,
                    employeeCpf = employeeCpf.ifBlank { getString(R.string.ppp_default_employee_cpf) },
                    employeeRole = role.ifBlank { getString(R.string.ppp_default_role) },
                    sector = sector.ifBlank { getString(R.string.ppp_default_sector) },
                    admissionDate = admission,
                    dismissalDate = dismissal,
                    activities = activities.ifBlank { getString(R.string.ppp_default_activities) },
                    risks = risks,
                    medicalExams = medicalExams,
                    responsibleName = responsible,
                    responsibleCpf = responsibleCpf.ifBlank { getString(R.string.ppp_default_responsible_cpf) }
                )
                val id = "ppp-${System.currentTimeMillis()}"
                currentDocumentId = id
                currentPayload = html
                withContext(Dispatchers.IO) {
                    DocumentLocalRepository.saveDraft(
                        DocumentEntity(
                            documentId = id,
                            documentType = "PPP",
                            companyCnpj = cnpj,
                            title = getString(R.string.ppp_document_title_template, employee),
                            payloadJson = html,
                            contentHash = DocumentSignatureHelper.sha256(html),
                            sourceRequestCode = sourceRequestCode
                        )
                    )
                }
                binding.pppPreviewText.text = html.take(600)
                binding.signPppButton.isEnabled = true
                binding.exportPppPdfButton.isEnabled = true
                binding.pppStatusText.text = getString(R.string.ppp_generated_template, id)
                updateRequiredFieldsSummary(emptyList())
            } finally {
                setOperationInProgress(false)
            }
        }
    }

    private fun preloadLegalContext(companyCnpjDigits: String) {
        if (companyCnpjDigits.length != 14) return

        safeLaunch("PppActivity/preloadLegalContext") {
            val previous = withContext(Dispatchers.IO) {
                DocumentLocalRepository.findLatestByCompanyAndType(companyCnpjDigits, "PPP")
                    ?: DocumentLocalRepository.findLatestByCompanyCnpj(companyCnpjDigits)
            } ?: return@safeLaunch

            val context = autofillService.extractPreviousDocumentContext(
                payload = previous.payloadJson,
                signedBy = previous.signedBy
            )

            var applied = false
            if (binding.pppResponsibleNameInput.text.isNullOrBlank() && !context.responsibleName.isNullOrBlank()) {
                binding.pppResponsibleNameInput.setText(context.responsibleName)
                applied = true
            }

            if (applied) {
                binding.pppCnpjStatusText.text = getString(
                    R.string.prefill_context_reused_template,
                    previous.documentType,
                    previous.documentId
                )
            }
        }
    }

    private fun clearFieldErrors() {
        binding.pppCnpjInput.error = null
        binding.pppEmployeeNameInput.error = null
        binding.pppAdmissionDateInput.error = null
        binding.pppResponsibleNameInput.error = null
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
        if (requestCompanyCnpj.length == 14 && binding.pppCnpjInput.text.isNullOrBlank()) {
            binding.pppCnpjInput.setText(requestCompanyCnpj)
            lastPrefilledCnpj = requestCompanyCnpj
            preloadLegalContext(requestCompanyCnpj)
        }
        if (requestCompanyName.isNotBlank() && binding.pppCompanyNameInput.text.isNullOrBlank()) {
            binding.pppCompanyNameInput.setText(requestCompanyName)
        }
        if (!sourceRequestCode.isNullOrBlank()) {
            binding.pppStatusText.text = getString(R.string.service_request_linked_status_template, sourceRequestCode)
        }
    }

    private fun showValidationErrors(missingFields: List<PppField>) {
        clearFieldErrors()
        missingFields.forEach {
            when (it) {
                PppField.COMPANY_CNPJ -> binding.pppCnpjInput.error = getString(R.string.ppp_error_cnpj_invalid_field)
                PppField.EMPLOYEE_NAME -> binding.pppEmployeeNameInput.error = getString(R.string.ppp_error_required_field)
                PppField.ADMISSION_DATE -> binding.pppAdmissionDateInput.error = getString(R.string.ppp_error_required_field)
                PppField.RESPONSIBLE_NAME -> binding.pppResponsibleNameInput.error = getString(R.string.ppp_error_required_field)
            }
        }
        when (missingFields.first()) {
            PppField.COMPANY_CNPJ -> binding.pppCnpjInput.requestFocus()
            PppField.EMPLOYEE_NAME -> binding.pppEmployeeNameInput.requestFocus()
            PppField.ADMISSION_DATE -> binding.pppAdmissionDateInput.requestFocus()
            PppField.RESPONSIBLE_NAME -> binding.pppResponsibleNameInput.requestFocus()
        }
    }

    private fun setupRequiredFieldsSummary() {
        val updater = {
            val missing = PppFormValidator.validate(
                PppFormData(
                    cnpj = binding.pppCnpjInput.text?.toString().orEmpty(),
                    employeeName = binding.pppEmployeeNameInput.text?.toString().orEmpty().trim(),
                    admissionDate = binding.pppAdmissionDateInput.text?.toString().orEmpty().trim(),
                    responsibleName = binding.pppResponsibleNameInput.text?.toString().orEmpty().trim()
                )
            )
            updateRequiredFieldsSummary(missing)
        }
        binding.pppCnpjInput.doAfterTextChanged { updater() }
        binding.pppCnpjInput.doAfterTextChanged { value ->
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
        binding.pppEmployeeNameInput.doAfterTextChanged { updater() }
        binding.pppAdmissionDateInput.doAfterTextChanged { updater() }
        binding.pppResponsibleNameInput.doAfterTextChanged { updater() }
        updater()
    }

    private fun updateRequiredFieldsSummary(missingFields: List<PppField>) {
        binding.pppRequiredSummaryText.text = if (missingFields.isEmpty()) {
            getString(R.string.ppp_required_ready)
        } else {
            val labels = missingFields.joinToString(", ") { getString(fieldLabelRes(it)) }
            getString(R.string.ppp_required_pending_template, labels)
        }
    }

    @StringRes
    private fun validationMessageFor(field: PppField): Int {
        return when (field) {
            PppField.COMPANY_CNPJ -> R.string.ppp_error_message_cnpj_invalid
            PppField.EMPLOYEE_NAME -> R.string.ppp_error_message_employee_required
            PppField.ADMISSION_DATE -> R.string.ppp_error_message_admission_required
            PppField.RESPONSIBLE_NAME -> R.string.ppp_error_message_responsible_required
        }
    }

    @StringRes
    private fun fieldLabelRes(field: PppField): Int {
        return when (field) {
            PppField.COMPANY_CNPJ -> R.string.ppp_required_label_cnpj
            PppField.EMPLOYEE_NAME -> R.string.ppp_required_label_employee
            PppField.ADMISSION_DATE -> R.string.ppp_required_label_admission
            PppField.RESPONSIBLE_NAME -> R.string.ppp_required_label_responsible
        }
    }

    private fun signCurrentDocument() {
        if (isOperationInProgress) return
        val documentId = currentDocumentId
        if (documentId.isNullOrBlank() || currentPayload.isBlank()) {
            showToast(R.string.ppp_error_generate_before_sign)
            return
        }
        if (!DocumentSignatureHelper.canAuthenticate(this)) {
            showToast(R.string.ppp_error_biometric_unavailable)
            return
        }
        val responsible = binding.pppResponsibleNameInput.text.toString().trim()
            .ifBlank { getString(R.string.ppp_signed_by_fallback) }
        setOperationInProgress(true)
        DocumentSignatureHelper.signWithBiometric(
            activity = this, payload = currentPayload, signedBy = responsible,
            onSuccess = { record ->
                lifecycleScope.launch(Dispatchers.IO) {
                    runCatching {
                        DocumentLocalRepository.markSigned(documentId = documentId, signedAt = record.signedAt, signedBy = record.signedBy, contentHash = record.contentHash, signatureB64 = record.signatureB64, publicKeyFingerprint = record.publicKeyFingerprint, signatureAlgorithm = "SHA256withRSA")
                    }.onFailure { CrashShield.recordRecoverableError("PppActivity/markSigned", it) }
                }
                runOnUiThread {
                    binding.pppStatusText.text = getString(
                        R.string.ppp_signed_status_template,
                        responsible,
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
            showToast(R.string.ppp_error_generate_before_export)
            return
        }
        setOperationInProgress(true)
        safeLaunch("PppActivity/pdf") {
            try {
                runCatching {
                    withContext(Dispatchers.IO) {
                        artifactExportService.exportPlainTextPdfAndAudit(
                            context = this@PppActivity,
                            title = getString(R.string.ppp_export_title),
                            body = currentPayload,
                            documentId = currentDocumentId,
                            filePrefix = "ppp"
                        )
                    }
                }
                    .onSuccess {
                        binding.pppStatusText.text = getString(
                            R.string.ppp_export_status_template,
                            it.pdfFile.absolutePath,
                            it.auditFile?.absolutePath ?: getString(R.string.audit_not_generated)
                        )
                    }
                    .onFailure { showToast(R.string.ppp_error_export_failed) }
            } finally {
                setOperationInProgress(false)
            }
        }
    }

    private fun setOperationInProgress(inProgress: Boolean) {
        isOperationInProgress = inProgress
        binding.generatePppButton.isEnabled = !inProgress
        binding.signPppButton.isEnabled = !inProgress && currentPayload.isNotBlank()
        binding.exportPppPdfButton.isEnabled = !inProgress && currentPayload.isNotBlank()
    }

    private fun showToast(@StringRes resId: Int) {
        Toast.makeText(this, getString(resId), Toast.LENGTH_SHORT).show()
    }
}
