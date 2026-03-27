package com.example.anda.feature.aso

import android.os.Bundle
import android.widget.ArrayAdapter
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
import com.example.anda.data.local.AppDatabase
import com.example.anda.data.local.entity.DocumentEntity
import com.example.anda.data.repository.DocumentLocalRepository
import com.example.anda.data.repository.SstRepository
import com.example.anda.data.services.AutofillService
import com.example.anda.data.services.AsoOperationalPolicy
import com.example.anda.data.services.DocumentArtifactExportService
import com.example.anda.data.services.DocumentGenerationService
import com.example.anda.data.integration.ServiceIntegrationHelper
import com.example.anda.databinding.ActivityAsoBinding
import com.example.anda.domain.CompanyProfile
import com.example.anda.domain.EmployeeExaminationType
import com.example.anda.domain.EmployeeProfile
import com.example.anda.feature.companies.CompanyManagementActivity
import com.example.anda.feature.employees.EmployeeManagementActivity
import com.example.anda.feature.requests.ServiceRequestIntentContract
import com.example.anda.feature.security.DocumentSignatureHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class AsoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAsoBinding
    private lateinit var integrationHelper: ServiceIntegrationHelper

    private val autofillService = AutofillService()
    private val generationService = DocumentGenerationService()
    private val artifactExportService = DocumentArtifactExportService()

    private var currentDocumentId: String? = null
    private var currentPayload: String = ""
    private var isOperationInProgress: Boolean = false
    private var lastPrefilledCnpj: String? = null
    private var sourceRequestCode: String? = null

    // Employee picker launcher
    private val pickEmployeeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            binding.employeeNameInput.setText(
                data.getStringExtra(EmployeeManagementActivity.RESULT_EMPLOYEE_NAME).orEmpty()
            )
            binding.employeeCpfInput.setText(
                data.getStringExtra(EmployeeManagementActivity.RESULT_EMPLOYEE_CPF).orEmpty()
            )

            val employeeCompanyCnpj = data
                .getStringExtra(EmployeeManagementActivity.RESULT_EMPLOYEE_COMPANY_CNPJ)
                .orEmpty()
                .filter(Char::isDigit)
            val employeeCompanyName = data
                .getStringExtra(EmployeeManagementActivity.RESULT_EMPLOYEE_COMPANY_NAME)
                .orEmpty()
                .trim()
            val currentCompanyCnpj = binding.companyCnpjInput.text.toString().filter(Char::isDigit)

            if (currentCompanyCnpj.isBlank() && employeeCompanyCnpj.isNotBlank()) {
                applySelectedCompany(
                    cnpj = employeeCompanyCnpj,
                    legalName = employeeCompanyName,
                    cnae = binding.companyCnaeInput.text?.toString().orEmpty().trim(),
                    statusMessage = getString(R.string.aso_lookup_employee_company_applied)
                )
            } else if (currentCompanyCnpj == employeeCompanyCnpj &&
                binding.companyNameInput.text.isNullOrBlank() &&
                employeeCompanyName.isNotBlank()
            ) {
                binding.companyNameInput.setText(employeeCompanyName)
            }
        }
    }

    private val pickCompanyLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            applySelectedCompany(
                cnpj = data.getStringExtra(CompanyManagementActivity.RESULT_COMPANY_CNPJ).orEmpty(),
                legalName = data.getStringExtra(CompanyManagementActivity.RESULT_COMPANY_LEGAL_NAME).orEmpty(),
                cnae = data.getStringExtra(CompanyManagementActivity.RESULT_COMPANY_CNAE).orEmpty(),
                statusMessage = getString(R.string.aso_lookup_company_selected_local)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAsoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize SmartAutofill
        integrationHelper = ServiceIntegrationHelper(this, lifecycleScope)
        integrationHelper.prefillForm("ASO", mapOf(
            "company_cnpj" to binding.companyCnpjInput,
            "company_name" to binding.companyNameInput
        ))

        DocumentLocalRepository.initialize(this)
        SstRepository.initialize(this)
        applyRequestContextFromIntent()

        setupExaminationTypeSpinner()
        setupRequiredFieldsSummary()

        binding.asoDisclaimerText.text = AsoOperationalPolicy.UI_FLOW_DISCLAIMER
        binding.generateAsoButton.setOnClickListener { generateAndSaveDraft() }
        binding.signAsoButton.setOnClickListener { signCurrentDocument() }
        binding.exportAsoPdfButton.setOnClickListener { exportCurrentPdf() }
        binding.signAsoButton.isEnabled = false
        binding.exportAsoPdfButton.isEnabled = false
        binding.asoStatusText.text = AsoOperationalPolicy.INITIAL_STATUS

        // Employee picker
        binding.pickEmployeeButton.setOnClickListener {
            val cnpj = binding.companyCnpjInput.text.toString().filter { it.isDigit() }
            pickEmployeeLauncher.launch(
                EmployeeManagementActivity.buildPickIntent(this, cnpj)
            )
        }

        binding.pickCompanyButton.setOnClickListener {
            pickCompanyLauncher.launch(CompanyManagementActivity.buildPickIntent(this))
        }

        binding.lookupCompanyCnpjButton.setOnClickListener {
            val cnpj = binding.companyCnpjInput.text.toString().trim()
            if (cnpj.filter { it.isDigit() }.length != 14) {
                binding.cnpjLookupStatusText.text = getString(R.string.aso_lookup_invalid_cnpj)
                return@setOnClickListener
            }
            binding.cnpjLookupStatusText.text = getString(R.string.aso_lookup_searching_company)
            binding.lookupCompanyCnpjButton.isEnabled = false
            lifecycleScope.launch {
                SstRepository.lookupCompanyByCnpj(cnpj)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SstRepository.companyLookupState.collect { state ->
                    when {
                        state.isLoading -> {
                            binding.cnpjLookupStatusText.text = getString(R.string.aso_lookup_searching_company)
                            binding.lookupCompanyCnpjButton.isEnabled = false
                        }
                        state.company != null -> {
                            val c = state.company
                            val normalizedCnpj = c.cnpj.filter(Char::isDigit)
                            if (binding.companyNameInput.text.isNullOrBlank()) {
                                binding.companyNameInput.setText(c.legalName)
                            }
                            if (binding.companyCnaeInput.text.isNullOrBlank()) {
                                binding.companyCnaeInput.setText(c.cnae)
                            }
                            val grau = state.riskGrade ?: 2
                            binding.cnpjLookupStatusText.text = getString(
                                R.string.aso_lookup_company_risk_template,
                                c.legalName.take(40),
                                grau
                            )
                            if (normalizedCnpj.length == 14) {
                                lastPrefilledCnpj = normalizedCnpj
                                preloadLegalContext(normalizedCnpj)
                            }
                            binding.lookupCompanyCnpjButton.isEnabled = true
                        }
                        !state.errorMessage.isNullOrBlank() -> {
                            binding.cnpjLookupStatusText.text = getString(
                                R.string.aso_lookup_not_found_template,
                                state.errorMessage
                            )
                            binding.lookupCompanyCnpjButton.isEnabled = true
                        }
                        else -> {
                            binding.lookupCompanyCnpjButton.isEnabled = true
                        }
                    }
                }
            }
        }
    }

    private fun setupExaminationTypeSpinner() {
        val types = listOf(
            getString(R.string.aso_exam_type_admission),
            getString(R.string.aso_exam_type_periodic),
            getString(R.string.aso_exam_type_return),
            getString(R.string.aso_exam_type_removal),
            getString(R.string.aso_exam_type_exit)
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, types)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.examinationTypeSpinner.adapter = adapter
    }

    private fun applySelectedCompany(
        cnpj: String,
        legalName: String,
        cnae: String,
        statusMessage: String
    ) {
        val normalizedCnpj = cnpj.filter(Char::isDigit)
        if (normalizedCnpj.isNotBlank()) {
            binding.companyCnpjInput.setText(normalizedCnpj)
            // Track for SmartAutofill
            integrationHelper.onFieldChanged("company_cnpj", normalizedCnpj, "ASO")
        }
        if (legalName.isNotBlank()) {
            binding.companyNameInput.setText(legalName)
            // Track for SmartAutofill
            integrationHelper.onFieldChanged("company_name", legalName, "ASO")
        }
        if (cnae.isNotBlank()) {
            binding.companyCnaeInput.setText(cnae)
        }
        binding.cnpjLookupStatusText.text = statusMessage
        if (normalizedCnpj.length == 14) {
            lastPrefilledCnpj = normalizedCnpj
        }
        preloadLegalContext(normalizedCnpj)
    }

    private fun preloadLegalContext(companyCnpjDigits: String) {
        if (companyCnpjDigits.length != 14) return

        safeLaunch("AsoActivity/preloadLegalContext") {
            val previous = withContext(Dispatchers.IO) {
                DocumentLocalRepository.findLatestByCompanyAndType(companyCnpjDigits, "ASO")
                    ?: DocumentLocalRepository.findLatestByCompanyCnpj(companyCnpjDigits)
            } ?: return@safeLaunch

            val context = autofillService.extractPreviousDocumentContext(
                payload = previous.payloadJson,
                signedBy = previous.signedBy
            )

            var applied = false
            if (binding.doctorNameInput.text.isNullOrBlank() && !context.responsibleName.isNullOrBlank()) {
                binding.doctorNameInput.setText(context.responsibleName)
                applied = true
            }
            if (binding.doctorCrmInput.text.isNullOrBlank() && !context.professionalRegistry.isNullOrBlank()) {
                binding.doctorCrmInput.setText(context.professionalRegistry)
                applied = true
            }
            if (binding.clinicNameInput.text.isNullOrBlank() && !context.clinicName.isNullOrBlank()) {
                binding.clinicNameInput.setText(context.clinicName)
                applied = true
            }
            if (binding.clinicCnpjInput.text.isNullOrBlank() && !context.clinicCnpj.isNullOrBlank()) {
                binding.clinicCnpjInput.setText(context.clinicCnpj)
                applied = true
            }

            if (applied) {
                binding.cnpjLookupStatusText.text = getString(
                    R.string.prefill_context_reused_template,
                    previous.documentType,
                    previous.documentId
                )
            }
        }
    }

    private fun selectedExaminationType(): EmployeeExaminationType {
        return when (binding.examinationTypeSpinner.selectedItemPosition) {
            0 -> EmployeeExaminationType.ADMISSION
            1 -> EmployeeExaminationType.PERIODIC
            2 -> EmployeeExaminationType.RETURN
            3 -> EmployeeExaminationType.REMOVAL
            4 -> EmployeeExaminationType.EXIT
            else -> EmployeeExaminationType.ADMISSION
        }
    }

    private fun generateAndSaveDraft() {
        if (isOperationInProgress) return
        clearFieldErrors()

        val company = CompanyProfile(
            cnpj = binding.companyCnpjInput.text.toString().filter(Char::isDigit),
            legalName = binding.companyNameInput.text.toString().trim(),
            tradeName = "",
            cnae = binding.companyCnaeInput.text.toString().trim(),
            city = "",
            state = "",
            postalCode = ""
        )

        val employee = EmployeeProfile(
            name = binding.employeeNameInput.text.toString().trim(),
            cpf = binding.employeeCpfInput.text.toString().trim(),
            rg = "",
            birthDate = System.currentTimeMillis(),
            gender = "N/A"
        )

        val doctor = binding.doctorNameInput.text.toString().trim()
        val formData = currentFormData()
        val missingFields = AsoFormValidator.validate(formData)
        if (missingFields.isNotEmpty()) {
            showValidationErrors(missingFields)
            updateRequiredFieldsSummary(missingFields)
            showToast(validationMessageFor(missingFields.first()))
            return
        }

        setOperationInProgress(true)
        safeLaunch("AsoActivity/generateDraft") {
            try {
                val previousContext = withContext(Dispatchers.IO) {
                    company.cnpj.filter(Char::isDigit).takeIf { it.length == 14 }
                        ?.let { DocumentLocalRepository.findLatestByCompanyCnpj(it) }
                        ?.let { autofillService.extractPreviousAsoContextFromHtml(it.payloadJson) }
                }

                val aso = autofillService.createAsoDraft(
                    company = company,
                    employee = employee,
                    doctorName = doctor,
                    examinationType = selectedExaminationType(),
                    doctorCrm = binding.doctorCrmInput.text.toString().trim().ifBlank {
                        previousContext?.doctorCrm ?: getString(R.string.aso_default_doctor_crm)
                    },
                    clinicName = binding.clinicNameInput.text.toString().trim().ifBlank {
                        previousContext?.clinicName ?: getString(R.string.aso_default_clinic_name)
                    },
                    clinicCnpj = binding.clinicCnpjInput.text.toString().trim().ifBlank {
                        previousContext?.clinicCnpj ?: getString(R.string.aso_default_clinic_cnpj)
                    },
                    examinationResult = selectedConclusionResult().ifBlank { "APT" },
                    restrictions = binding.restrictionsInput.text?.toString().orEmpty(),
                    complementaryExams = binding.complementaryExamsInput.text?.toString().orEmpty(),
                    observations = binding.observationsInput.text?.toString().orEmpty()
                )
                val html = generationService.generateASO(aso)
                val hash = DocumentSignatureHelper.sha256(html)
                currentDocumentId = aso.id
                currentPayload = html

                withContext(Dispatchers.IO) {
                    DocumentLocalRepository.saveDraft(
                        DocumentEntity(
                            documentId = aso.id,
                            documentType = "ASO",
                            companyCnpj = company.cnpj,
                            title = getString(R.string.aso_document_title_template, employee.name),
                            payloadJson = html,
                            contentHash = hash,
                            sourceRequestCode = sourceRequestCode,
                            validUntil = aso.validUntil
                        )
                    )
                    // Update employee ASO tracking dates if employee is saved locally
                    val cpf = employee.cpf.filter { it.isDigit() }
                    if (cpf.length == 11) {
                        runCatching {
                            val empDao = AppDatabase.getInstance(applicationContext).employeeDao()
                            val now = System.currentTimeMillis()
                            val oneYear = TimeUnit.DAYS.toMillis(365)
                            empDao.updateAsoDate(cpf, now, now + oneYear)
                        }
                    }
                }

                binding.asoPreviewText.text = html.take(900)
                binding.signAsoButton.isEnabled = true
                binding.exportAsoPdfButton.isEnabled = true
                binding.asoStatusText.text = AsoOperationalPolicy.draftSavedStatus(aso.id)
                updateRequiredFieldsSummary(emptyList())
            } finally {
                setOperationInProgress(false)
            }
        }
    }

    private fun clearFieldErrors() {
        binding.companyCnpjInput.error = null
        binding.companyNameInput.error = null
        binding.companyCnaeInput.error = null
        binding.employeeNameInput.error = null
        binding.employeeCpfInput.error = null
        binding.doctorNameInput.error = null
    }

    private fun currentFormData(): AsoFormData {
        return AsoFormData(
            companyCnpj = binding.companyCnpjInput.text?.toString().orEmpty().trim(),
            companyName = binding.companyNameInput.text?.toString().orEmpty().trim(),
            companyCnae = binding.companyCnaeInput.text?.toString().orEmpty().trim(),
            employeeName = binding.employeeNameInput.text?.toString().orEmpty().trim(),
            employeeCpf = binding.employeeCpfInput.text?.toString().orEmpty().trim(),
            doctorName = binding.doctorNameInput.text?.toString().orEmpty().trim(),
            conclusionResult = selectedConclusionResult()
        )
    }

    /** Returns "APT", "INAPTO", "INAPTO TEMPORÁRIO", or "" if nothing selected. */
    private fun selectedConclusionResult(): String = when (binding.conclusionRadioGroup.checkedRadioButtonId) {
        R.id.radioApt       -> "APT"
        R.id.radioInapt     -> "INAPTO"
        R.id.radioInaptTemp -> "INAPTO TEMPORÁRIO"
        else                -> ""
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

        if (requestCompanyCnpj.length == 14 && binding.companyCnpjInput.text.isNullOrBlank()) {
            binding.companyCnpjInput.setText(requestCompanyCnpj)
            lastPrefilledCnpj = requestCompanyCnpj
            preloadLegalContext(requestCompanyCnpj)
        }
        if (requestCompanyName.isNotBlank() && binding.companyNameInput.text.isNullOrBlank()) {
            binding.companyNameInput.setText(requestCompanyName)
        }
        if (!sourceRequestCode.isNullOrBlank()) {
            binding.asoStatusText.text = getString(R.string.service_request_linked_status_template, sourceRequestCode)
        }
    }

    private fun setupRequiredFieldsSummary() {
        val updater = {
            val missing = AsoFormValidator.validate(currentFormData())
            updateRequiredFieldsSummary(missing)
        }
        binding.companyCnpjInput.doAfterTextChanged { updater() }
        binding.companyCnpjInput.doAfterTextChanged { value ->
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
        binding.companyNameInput.doAfterTextChanged { updater() }
        binding.companyCnaeInput.doAfterTextChanged { updater() }
        binding.employeeNameInput.doAfterTextChanged { updater() }
        binding.employeeCpfInput.doAfterTextChanged { updater() }
        binding.doctorNameInput.doAfterTextChanged { updater() }
        binding.conclusionRadioGroup.setOnCheckedChangeListener { _, _ -> updater() }
        updater()
    }

    private fun updateRequiredFieldsSummary(missingFields: List<AsoField>) {
        binding.asoRequiredSummaryText.text = if (missingFields.isEmpty()) {
            getString(R.string.aso_required_ready)
        } else {
            val labels = missingFields.take(3).joinToString(", ") { getString(fieldLabelRes(it)) }
            val remaining = missingFields.size - 3
            if (remaining > 0) {
                getString(R.string.aso_required_pending_more_template, labels, remaining)
            } else {
                getString(R.string.aso_required_pending_template, labels)
            }
        }
    }

    private fun showValidationErrors(missingFields: List<AsoField>) {
        clearFieldErrors()
        missingFields.forEach { field ->
            when (field) {
                AsoField.COMPANY_CNPJ   -> binding.companyCnpjInput.error = getString(R.string.aso_error_cnpj_invalid_field)
                AsoField.COMPANY_NAME   -> binding.companyNameInput.error = getString(R.string.aso_error_company_name_required_field)
                AsoField.COMPANY_CNAE   -> binding.companyCnaeInput.error = getString(R.string.aso_error_cnae_required_field)
                AsoField.EMPLOYEE_NAME  -> binding.employeeNameInput.error = getString(R.string.aso_error_employee_name_required_field)
                AsoField.EMPLOYEE_CPF   -> binding.employeeCpfInput.error = getString(R.string.aso_error_employee_cpf_invalid_field)
                AsoField.DOCTOR_NAME    -> binding.doctorNameInput.error = getString(R.string.aso_error_doctor_required_field)
                AsoField.CONCLUSION     -> { /* highlighted by toast — RadioGroup has no .error */ }
            }
        }
        focusField(missingFields.first())
    }

    private fun focusField(field: AsoField) {
        when (field) {
            AsoField.COMPANY_CNPJ   -> binding.companyCnpjInput.requestFocus()
            AsoField.COMPANY_NAME   -> binding.companyNameInput.requestFocus()
            AsoField.COMPANY_CNAE   -> binding.companyCnaeInput.requestFocus()
            AsoField.EMPLOYEE_NAME  -> binding.employeeNameInput.requestFocus()
            AsoField.EMPLOYEE_CPF   -> binding.employeeCpfInput.requestFocus()
            AsoField.DOCTOR_NAME    -> binding.doctorNameInput.requestFocus()
            AsoField.CONCLUSION     -> binding.conclusionRadioGroup.requestFocus()
        }
    }

    @StringRes
    private fun validationMessageFor(field: AsoField): Int {
        return when (field) {
            AsoField.COMPANY_CNPJ   -> R.string.aso_error_cnpj_invalid_message
            AsoField.COMPANY_NAME   -> R.string.aso_error_company_name_required_message
            AsoField.COMPANY_CNAE   -> R.string.aso_error_cnae_required_message
            AsoField.EMPLOYEE_NAME  -> R.string.aso_error_employee_name_required_message
            AsoField.EMPLOYEE_CPF   -> R.string.aso_error_employee_cpf_invalid_message
            AsoField.DOCTOR_NAME    -> R.string.aso_error_doctor_required_message
            AsoField.CONCLUSION     -> R.string.aso_error_conclusion_required_message
        }
    }

    @StringRes
    private fun fieldLabelRes(field: AsoField): Int {
        return when (field) {
            AsoField.COMPANY_CNPJ   -> R.string.aso_required_label_cnpj
            AsoField.COMPANY_NAME   -> R.string.aso_required_label_company_name
            AsoField.COMPANY_CNAE   -> R.string.aso_required_label_cnae
            AsoField.EMPLOYEE_NAME  -> R.string.aso_required_label_employee_name
            AsoField.EMPLOYEE_CPF   -> R.string.aso_required_label_employee_cpf
            AsoField.DOCTOR_NAME    -> R.string.aso_required_label_doctor_name
            AsoField.CONCLUSION     -> R.string.aso_required_label_conclusion
        }
    }

    private fun signCurrentDocument() {
        if (isOperationInProgress) return
        val documentId = currentDocumentId
        if (documentId.isNullOrBlank() || currentPayload.isBlank()) {
            showToast(R.string.aso_error_generate_before_sign)
            return
        }

        if (!DocumentSignatureHelper.canAuthenticate(this)) {
            showToast(R.string.aso_error_biometric_unavailable)
            return
        }

        setOperationInProgress(true)
        DocumentSignatureHelper.signWithBiometric(
            activity = this,
            payload = currentPayload,
            signedBy = binding.doctorNameInput.text.toString().trim().ifBlank {
                getString(R.string.aso_signed_by_fallback)
            },
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
                        CrashShield.recordRecoverableError("AsoActivity/markSigned", it)
                    }
                }
                runOnUiThread {
                    binding.asoStatusText.text = AsoOperationalPolicy.signedStatus(record.signedAt, record.signedBy)
                    setOperationInProgress(false)
                }
            },
            onError = { error ->
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                setOperationInProgress(false)
            }
        )
    }

    private fun exportCurrentPdf() {
        if (isOperationInProgress) return
        if (currentPayload.isBlank()) {
            showToast(R.string.aso_error_generate_before_export)
            return
        }

        setOperationInProgress(true)
        safeLaunch("AsoActivity/exportPdf") {
            try {
                runCatching {
                    withContext(Dispatchers.IO) {
                        artifactExportService.exportPlainTextPdfAndAudit(
                            context = this@AsoActivity,
                            title = getString(R.string.aso_export_title),
                            body = AsoOperationalPolicy.buildPdfBody(
                                currentPayload
                            ),
                            documentId = currentDocumentId,
                            filePrefix = "aso"
                        )
                    }
                }.onSuccess { result ->
                    binding.asoStatusText.text = AsoOperationalPolicy.exportStatus(
                        result.pdfFile.absolutePath,
                        result.auditFile?.absolutePath
                    )
                }.onFailure {
                    showToast(R.string.aso_error_export_failed)
                }
            } finally {
                setOperationInProgress(false)
            }
        }
    }

    private fun setOperationInProgress(inProgress: Boolean) {
        isOperationInProgress = inProgress
        binding.generateAsoButton.isEnabled = !inProgress
        binding.signAsoButton.isEnabled = !inProgress && currentPayload.isNotBlank()
        binding.exportAsoPdfButton.isEnabled = !inProgress && currentPayload.isNotBlank()
    }

    private fun showToast(@StringRes messageRes: Int) {
        Toast.makeText(this, getString(messageRes), Toast.LENGTH_SHORT).show()
    }
}

