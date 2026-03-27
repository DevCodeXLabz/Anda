package com.example.anda.feature.os

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
import com.example.anda.data.local.entity.DocumentEntity
import com.example.anda.data.repository.DocumentLocalRepository
import com.example.anda.data.repository.SstRepository
import com.example.anda.data.services.AutofillService
import com.example.anda.data.services.DocumentArtifactExportService
import com.example.anda.data.services.DocumentGenerationService
import com.example.anda.data.integration.ServiceIntegrationHelper
import com.example.anda.databinding.ActivityOsBinding
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

class OsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOsBinding
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
            if (cnpj.isNotBlank()) binding.osCnpjInput.setText(cnpj)
            if (name.isNotBlank()) binding.osCompanyNameInput.setText(name)
            if (cnae.isNotBlank()) binding.osCnaeInput.setText(cnae)
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
                    sourceDocument = "OS",
                    companyScope = cleanCnpj
                )
            }
            binding.osCnpjStatusText.text = getString(R.string.doc_pick_company_selected_local)
            preloadLegalContext(cnpj.filter(Char::isDigit))
        }
    }

    private val pickEmployeeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val name = data.getStringExtra(EmployeeManagementActivity.RESULT_EMPLOYEE_NAME).orEmpty()
            val cpf  = data.getStringExtra(EmployeeManagementActivity.RESULT_EMPLOYEE_CPF).orEmpty()
            val role = data.getStringExtra(EmployeeManagementActivity.RESULT_EMPLOYEE_ROLE).orEmpty()
            val empCnpj = data.getStringExtra(EmployeeManagementActivity.RESULT_EMPLOYEE_COMPANY_CNPJ).orEmpty()
            val empComp = data.getStringExtra(EmployeeManagementActivity.RESULT_EMPLOYEE_COMPANY_NAME).orEmpty()
            if (name.isNotBlank()) binding.osEmployeeNameInput.setText(name)
            if (cpf.isNotBlank())  binding.osEmployeeCpfInput.setText(cpf)
            if (role.isNotBlank()) binding.osEmployeeRoleInput.setText(role)
            // Backfill company if CNPJ is still blank
            if (empCnpj.isNotBlank() && binding.osCnpjInput.text.isNullOrBlank()) {
                binding.osCnpjInput.setText(empCnpj)
                binding.osCompanyNameInput.setText(empComp)
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
                    sourceDocument = "OS",
                    companyScope = empScope
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Initialize SmartAutofill integration helper
        integrationHelper = ServiceIntegrationHelper(this, lifecycleScope)
        DocumentLocalRepository.initialize(this)
        SstRepository.initialize(this)
        applyRequestContextFromIntent()
        binding.signOsButton.isEnabled = false
        binding.exportOsPdfButton.isEnabled = false
        setupRequiredFieldsSummary()

        binding.osPickCompanyButton.setOnClickListener {
            pickCompanyLauncher.launch(CompanyManagementActivity.buildPickIntent(this))
        }

        binding.osPickEmployeeButton.setOnClickListener {
            pickEmployeeLauncher.launch(
                EmployeeManagementActivity.buildPickIntent(this,
                    binding.osCnpjInput.text.toString().filter { it.isDigit() })
            )
        }

        binding.osLookupCnpjButton.setOnClickListener {
            val cnpj = binding.osCnpjInput.text.toString().trim()
            if (OsFormValidator.digitsOnly(cnpj).length != 14) {
                binding.osCnpjStatusText.text = getString(R.string.os_lookup_invalid_cnpj)
                return@setOnClickListener
            }
            binding.osCnpjStatusText.text = getString(R.string.os_lookup_searching)
            binding.osLookupCnpjButton.isEnabled = false
            lifecycleScope.launch { SstRepository.lookupCompanyByCnpj(cnpj) }
        }
        binding.generateOsButton.setOnClickListener { generate() }
        binding.signOsButton.setOnClickListener { signCurrentDocument() }
        binding.exportOsPdfButton.setOnClickListener { exportPdf() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SstRepository.companyLookupState.collect { s ->
                    when {
                        s.isLoading -> {
                            binding.osCnpjStatusText.text = getString(R.string.os_lookup_searching)
                            binding.osLookupCnpjButton.isEnabled = false
                        }
                        s.company != null -> {
                            val c = s.company
                            if (binding.osCompanyNameInput.text.isNullOrBlank()) binding.osCompanyNameInput.setText(c.legalName)
                            if (binding.osCnaeInput.text.isNullOrBlank()) binding.osCnaeInput.setText(c.cnae)
                            binding.osCnpjStatusText.text = c.legalName.take(40)
                            preloadLegalContext(c.cnpj.filter(Char::isDigit))
                            binding.osLookupCnpjButton.isEnabled = true
                        }
                        !s.errorMessage.isNullOrBlank() -> {
                            binding.osCnpjStatusText.text = getString(R.string.os_lookup_not_found)
                            binding.osLookupCnpjButton.isEnabled = true
                        }
                        else -> binding.osLookupCnpjButton.isEnabled = true
                    }
                }
            }
        }
    }

    private fun generate() {
        if (isOperationInProgress) return
        clearFieldErrors()

        val cnpj = OsFormValidator.digitsOnly(binding.osCnpjInput.text.toString())
        val companyName = binding.osCompanyNameInput.text.toString().trim()
        val cnae = binding.osCnaeInput.text.toString().trim()
        val employee = binding.osEmployeeNameInput.text.toString().trim()
        val employeeCpf = OsFormValidator.digitsOnly(binding.osEmployeeCpfInput.text.toString())
        val role = binding.osEmployeeRoleInput.text.toString().trim()
        val sector = binding.osSectorInput.text.toString().trim()
        val activities = binding.osActivitiesInput.text.toString().trim()
        val responsible = binding.osResponsibleInput.text.toString().trim()

        val missingFields = OsFormValidator.validate(
            OsFormData(cnpj = cnpj, employeeName = employee, activities = activities, responsible = responsible)
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
            sourceDocument = "OS",
            companyScope = currentCompanyScope ?: cnpj
        )

        setOperationInProgress(true)
        safeLaunch("OsActivity/generate") {
            try {
                val risks = autofillService.detectRisksByCnae(cnae)
                val html = generationService.generateOS(
                    companyCnpj = cnpj,
                    companyName = companyName,
                    employeeName = employee,
                    employeeCpf = employeeCpf.ifBlank { getString(R.string.os_default_employee_cpf) },
                    employeeRole = role.ifBlank { getString(R.string.os_default_employee_role) },
                    sector = sector.ifBlank { getString(R.string.os_default_sector) },
                    activities = activities,
                    risks = risks,
                    responsibleName = responsible
                )
                val id = "os-${System.currentTimeMillis()}"
                currentDocumentId = id
                currentPayload = html
                withContext(Dispatchers.IO) {
                    DocumentLocalRepository.saveDraft(
                        DocumentEntity(
                            documentId = id,
                            documentType = "OS",
                            companyCnpj = cnpj,
                            title = getString(R.string.os_document_title_template, employee, role.take(25)),
                            payloadJson = html,
                            contentHash = DocumentSignatureHelper.sha256(html),
                            sourceRequestCode = sourceRequestCode
                        )
                    )
                }
                binding.osPreviewText.text = html.take(600)
                binding.signOsButton.isEnabled = true
                binding.exportOsPdfButton.isEnabled = true
                binding.osStatusText.text = getString(R.string.os_generated_template, id)
                updateRequiredFieldsSummary(emptyList())
            } finally {
                setOperationInProgress(false)
            }
        }
    }

    private fun preloadLegalContext(companyCnpjDigits: String) {
        if (companyCnpjDigits.length != 14) return

        safeLaunch("OsActivity/preloadLegalContext") {
            val previous = withContext(Dispatchers.IO) {
                DocumentLocalRepository.findLatestByCompanyAndType(companyCnpjDigits, "OS")
                    ?: DocumentLocalRepository.findLatestByCompanyCnpj(companyCnpjDigits)
            } ?: return@safeLaunch

            val context = autofillService.extractPreviousDocumentContext(
                payload = previous.payloadJson,
                signedBy = previous.signedBy
            )

            var applied = false
            if (binding.osResponsibleInput.text.isNullOrBlank() && !context.responsibleName.isNullOrBlank()) {
                binding.osResponsibleInput.setText(context.responsibleName)
                applied = true
            }

            if (applied) {
                binding.osCnpjStatusText.text = getString(
                    R.string.prefill_context_reused_template,
                    previous.documentType,
                    previous.documentId
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
        if (requestCompanyCnpj.length == 14 && binding.osCnpjInput.text.isNullOrBlank()) {
            binding.osCnpjInput.setText(requestCompanyCnpj)
            lastPrefilledCnpj = requestCompanyCnpj
            // Track and publish company selection from incoming request
            currentCompanyScope = requestCompanyCnpj
            if (requestCompanyName.isNotBlank()) {
                val companyFields = mapOf(
                    ServiceIntegrationHelper.FieldIds.COMPANY_CNPJ to requestCompanyCnpj,
                    ServiceIntegrationHelper.FieldIds.COMPANY_NAME to requestCompanyName
                )
                integrationHelper.onFieldsChangedNormalized(
                    fields = companyFields,
                    sourceDocument = "OS",
                    companyScope = requestCompanyCnpj
                )
            }
            preloadLegalContext(requestCompanyCnpj)
        }
        if (requestCompanyName.isNotBlank() && binding.osCompanyNameInput.text.isNullOrBlank()) {
            binding.osCompanyNameInput.setText(requestCompanyName)
        }
        if (!sourceRequestCode.isNullOrBlank()) {
            binding.osStatusText.text = getString(R.string.service_request_linked_status_template, sourceRequestCode)
        }
    }

    private fun signCurrentDocument() {
        if (isOperationInProgress) return
        val documentId = currentDocumentId
        if (documentId.isNullOrBlank()  currentPayload.isBlank()) {
            showToast(R.string.os_error_generate_before_sign)
            return
        }
        if (!DocumentSignatureHelper.canAuthenticate(this)) {
            showToast(R.string.os_error_biometric_unavailable)
            return
        }
        val responsible = binding.osResponsibleInput.text.toString().trim().ifBlank {
            getString(R.string.os_signed_by_fallback)
        }
        setOperationInProgress(true)
        DocumentSignatureHelper.signWithBiometric(
            activity = this, payload = currentPayload, signedBy = responsible,
            onSuccess = { record ->
                lifecycleScope.launch(Dispatchers.IO) {
                    runCatching {
                        DocumentLocalRepository.markSigned(documentId = documentId, signedAt = record.signedAt, signedBy = record.signedBy, contentHash = record.contentHash, signatureB64 = record.signatureB64, publicKeyFingerprint = record.publicKeyFingerprint, signatureAlgorithm = "SHA256withRSA")
                    }.onFailure { CrashShield.recordRecoverableError("OsActivity/markSigned", it) }
                }
                runOnUiThread {
                    binding.osStatusText.text = getString(
                        R.string.os_signed_template,
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
            showToast(R.string.os_error_generate_before_export)
            return
        }
        setOperationInProgress(true)
        safeLaunch("OsActivity/pdf") {
            try {
                runCatching {
                    withContext(Dispatchers.IO) {
                        artifactExportService.exportPlainTextPdfAndAudit(
                            context = this@OsActivity,
                            title = getString(R.string.os_export_title),
                            body = currentPayload,
                            documentId = currentDocumentId,
                            filePrefix = "os"
                        )
                    }
                }
                    .onSuccess {
                        binding.osStatusText.text = getString(
                            R.string.os_export_result_template,
                            it.pdfFile.absolutePath,
                            it.auditFile?.absolutePath ?: getString(R.string.audit_not_generated)
                        )
                    }
                    .onFailure { showToast(R.string.os_error_export_failed) }
            } finally {
                setOperationInProgress(false)
            }
        }
    }

    private fun setupRequiredFieldsSummary() {
        val updater = {
            val missing = OsFormValidator.validate(
                OsFormData(
                    cnpj = binding.osCnpjInput.text?.toString().orEmpty(),
                    employeeName = binding.osEmployeeNameInput.text?.toString().orEmpty().trim(),
                    activities = binding.osActivitiesInput.text?.toString().orEmpty().trim(),
                    responsible = binding.osResponsibleInput.text?.toString().orEmpty().trim()
                )
            )
            updateRequiredFieldsSummary(missing)
        }
        binding.osCnpjInput.doAfterTextChanged { updater() }
        binding.osCnpjInput.doAfterTextChanged { value ->
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
        binding.osEmployeeNameInput.doAfterTextChanged { updater() }
        binding.osActivitiesInput.doAfterTextChanged { updater() }
        binding.osResponsibleInput.doAfterTextChanged { updater() }
        updater()
    }

    private fun updateRequiredFieldsSummary(missingFields: List<OsField>) {
        binding.osRequiredSummaryText.text = if (missingFields.isEmpty()) {
            getString(R.string.os_required_ready)
        } else {
            val labels = missingFields.joinToString(", ") { getString(fieldLabelRes(it)) }
            getString(R.string.os_required_pending_template, labels)
        }
    }

    private fun showValidationErrors(missingFields: List<OsField>) {
        clearFieldErrors()
        missingFields.forEach {
            when (it) {
                OsField.COMPANY_CNPJ -> binding.osCnpjInput.error = getString(R.string.os_error_cnpj_invalid_field)
                OsField.EMPLOYEE_NAME -> binding.osEmployeeNameInput.error = getString(R.string.os_error_employee_required_field)
                OsField.ACTIVITIES -> binding.osActivitiesInput.error = getString(R.string.os_error_activities_required_field)
                OsField.RESPONSIBLE -> binding.osResponsibleInput.error = getString(R.string.os_error_responsible_required_field)
            }
        }
        when (missingFields.first()) {
            OsField.COMPANY_CNPJ -> binding.osCnpjInput.requestFocus()
            OsField.EMPLOYEE_NAME -> binding.osEmployeeNameInput.requestFocus()
            OsField.ACTIVITIES -> binding.osActivitiesInput.requestFocus()
            OsField.RESPONSIBLE -> binding.osResponsibleInput.requestFocus()
        }
    }

    private fun clearFieldErrors() {
        binding.osCnpjInput.error = null
        binding.osEmployeeNameInput.error = null
        binding.osActivitiesInput.error = null
        binding.osResponsibleInput.error = null
    }

    @StringRes
    private fun validationMessageFor(field: OsField): Int {
        return when (field) {
            OsField.COMPANY_CNPJ -> R.string.os_error_cnpj_invalid_message
            OsField.EMPLOYEE_NAME -> R.string.os_error_employee_required_message
            OsField.ACTIVITIES -> R.string.os_error_activities_required_message
            OsField.RESPONSIBLE -> R.string.os_error_responsible_required_message
        }
    }

    @StringRes
    private fun fieldLabelRes(field: OsField): Int {
        return when (field) {
            OsField.COMPANY_CNPJ -> R.string.os_required_label_cnpj
            OsField.EMPLOYEE_NAME -> R.string.os_required_label_employee
            OsField.ACTIVITIES -> R.string.os_required_label_activities
            OsField.RESPONSIBLE -> R.string.os_required_label_responsible
        }
    }

    private fun setOperationInProgress(inProgress: Boolean) {
        isOperationInProgress = inProgress
        binding.generateOsButton.isEnabled = !inProgress
        binding.signOsButton.isEnabled = !inProgress && currentPayload.isNotBlank()
        binding.exportOsPdfButton.isEnabled = !inProgress && currentPayload.isNotBlank()
    }

    private fun showToast(@StringRes resId: Int) {
        Toast.makeText(this, getString(resId), Toast.LENGTH_SHORT).show()
    }
}
