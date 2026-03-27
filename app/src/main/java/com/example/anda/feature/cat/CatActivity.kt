package com.example.anda.feature.cat

import android.content.Intent
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
import com.example.anda.data.local.entity.DocumentEntity
import com.example.anda.data.repository.DocumentLocalRepository
import com.example.anda.data.repository.SstRepository
import com.example.anda.data.services.AutofillService
import com.example.anda.data.services.DocumentArtifactExportService
import com.example.anda.data.services.DocumentGenerationService
import com.example.anda.databinding.ActivityCatBinding
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

class CatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCatBinding
    private val autofillService = AutofillService()
    private val generationService = DocumentGenerationService()
    private val artifactExportService = DocumentArtifactExportService()
    private val dtFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))

    private var currentDocumentId: String? = null
    private var currentPayload: String = ""
    private var isOperationInProgress: Boolean = false
    private var lastPrefilledCnpj: String? = null
    private var sourceRequestCode: String? = null

    private val pickCompanyLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val cnpj = data.getStringExtra(CompanyManagementActivity.RESULT_COMPANY_CNPJ).orEmpty()
            val name = data.getStringExtra(CompanyManagementActivity.RESULT_COMPANY_LEGAL_NAME).orEmpty()
            val cnae = data.getStringExtra(CompanyManagementActivity.RESULT_COMPANY_CNAE).orEmpty()
            if (cnpj.isNotBlank()) binding.catCompanyCnpjInput.setText(cnpj)
            if (name.isNotBlank()) binding.catCompanyNameInput.setText(name)
            if (cnae.isNotBlank()) binding.catCompanyCnaeInput.setText(cnae)
            binding.catCnpjStatusText.text = getString(R.string.doc_pick_company_selected_local)
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
            if (name.isNotBlank()) binding.catEmployeeNameInput.setText(name)
            if (cpf.isNotBlank())  binding.catEmployeeCpfInput.setText(cpf)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        DocumentLocalRepository.initialize(this)
        SstRepository.initialize(this)
        applyRequestContextFromIntent()

        // Preenche data/hora atual automaticamente
        binding.catAccidentDateTimeInput.setText(dtFormat.format(Date(System.currentTimeMillis())))
        binding.signCatButton.isEnabled = false
        binding.exportCatPdfButton.isEnabled = false
        binding.catStatusText.text = getString(R.string.cat_status_waiting)
        setupRequiredFieldsSummary()

        binding.catPickCompanyButton.setOnClickListener {
            pickCompanyLauncher.launch(CompanyManagementActivity.buildPickIntent(this))
        }

        binding.catPickEmployeeButton.setOnClickListener {
            pickEmployeeLauncher.launch(EmployeeManagementActivity.buildPickIntent(this))
        }

        binding.catLookupCnpjButton.setOnClickListener {
            val cnpj = binding.catCompanyCnpjInput.text.toString().trim()
            if (cnpj.filter { it.isDigit() }.length != 14) {
                binding.catCnpjStatusText.text = getString(R.string.cat_lookup_invalid_cnpj)
                return@setOnClickListener
            }
            binding.catCnpjStatusText.text = getString(R.string.cat_lookup_searching_company)
            binding.catLookupCnpjButton.isEnabled = false
            lifecycleScope.launch { SstRepository.lookupCompanyByCnpj(cnpj) }
        }

        binding.generateCatButton.setOnClickListener { generateAndSave() }
        binding.signCatButton.setOnClickListener { signCurrentDocument() }
        binding.exportCatPdfButton.setOnClickListener { exportPdf() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SstRepository.companyLookupState.collect { state ->
                    when {
                        state.isLoading -> {
                            binding.catCnpjStatusText.text = getString(R.string.cat_lookup_searching)
                            binding.catLookupCnpjButton.isEnabled = false
                        }
                        state.company != null -> {
                            val c = state.company
                            if (binding.catCompanyNameInput.text.isNullOrBlank())
                                binding.catCompanyNameInput.setText(c.legalName)
                            if (binding.catCompanyCnaeInput.text.isNullOrBlank())
                                binding.catCompanyCnaeInput.setText(c.cnae)
                            binding.catCnpjStatusText.text = c.legalName.take(40)
                            preloadLegalContext(c.cnpj.filter(Char::isDigit))
                            binding.catLookupCnpjButton.isEnabled = true
                        }
                        !state.errorMessage.isNullOrBlank() -> {
                            binding.catCnpjStatusText.text = getString(
                                R.string.cat_lookup_not_found_template,
                                state.errorMessage
                            )
                            binding.catLookupCnpjButton.isEnabled = true
                        }
                        else -> binding.catLookupCnpjButton.isEnabled = true
                    }
                }
            }
        }
    }

    private fun generateAndSave() {
        if (isOperationInProgress) return
        clearFieldErrors()

        val cnpj = CatFormValidator.digitsOnly(binding.catCompanyCnpjInput.text.toString())
        val companyName = binding.catCompanyNameInput.text.toString().trim()
        val cnae = binding.catCompanyCnaeInput.text.toString().trim()
        val employeeName = binding.catEmployeeNameInput.text.toString().trim()
        val employeeCpf = CatFormValidator.digitsOnly(binding.catEmployeeCpfInput.text.toString())
        val accidentDateTimeStr = binding.catAccidentDateTimeInput.text.toString().trim()
        val location = binding.catAccidentLocationInput.text.toString().trim()
        val bodyPart = binding.catBodyPartInput.text.toString().trim()
        val injuryType = binding.catInjuryTypeInput.text.toString().trim()
        val description = binding.catDescriptionInput.text.toString().trim()
        val medicalAttention = binding.catMedicalAttentionInput.text.toString().trim()
        val reportedBy = binding.catReportedByInput.text.toString().trim()

        val missingFields = CatFormValidator.validate(
            CatFormData(
                companyCnpj = cnpj,
                companyName = companyName,
                employeeName = employeeName,
                employeeCpf = employeeCpf,
                description = description,
                reportedBy = reportedBy
            )
        )
        if (missingFields.isNotEmpty()) {
            showValidationErrors(missingFields)
            updateRequiredFieldsSummary(missingFields)
            showToast(validationMessageFor(missingFields.first()))
            return
        }

        val accidentTs = runCatching { dtFormat.parse(accidentDateTimeStr)?.time }
            .getOrNull() ?: System.currentTimeMillis()

        setOperationInProgress(true)
        safeLaunch("CatActivity/generate") {
            try {
                val html = generationService.generateCAT(
                    companyCnpj = cnpj,
                    companyName = companyName,
                    companyCnae = cnae,
                    employeeName = employeeName,
                    employeeCpf = employeeCpf,
                    accidentDateTime = accidentTs,
                    accidentLocation = location.ifBlank { getString(R.string.cat_not_informed) },
                    accidentDescription = description,
                    bodyPartAffected = bodyPart.ifBlank { getString(R.string.cat_not_informed) },
                    injuryType = injuryType.ifBlank { getString(R.string.cat_not_informed) },
                    medicalAttention = medicalAttention.ifBlank { getString(R.string.cat_not_informed) },
                    reportedBy = reportedBy
                )
                val docId = "cat-${System.currentTimeMillis()}"
                val hash = DocumentSignatureHelper.sha256(html)
                currentDocumentId = docId
                currentPayload = html

                withContext(Dispatchers.IO) {
                    DocumentLocalRepository.saveDraft(
                        DocumentEntity(
                            documentId = docId,
                            documentType = "CAT",
                            companyCnpj = cnpj,
                            title = getString(R.string.cat_document_title_template, employeeName),
                            payloadJson = html,
                            contentHash = hash,
                            sourceRequestCode = sourceRequestCode
                        )
                    )
                }

                binding.catPreviewText.text = html.take(600)
                binding.signCatButton.isEnabled = true
                binding.exportCatPdfButton.isEnabled = true
                binding.catStatusText.text = getString(R.string.cat_generated_status_template, docId)
                updateRequiredFieldsSummary(emptyList())
            } finally {
                setOperationInProgress(false)
            }
        }
    }

    private fun preloadLegalContext(companyCnpjDigits: String) {
        if (companyCnpjDigits.length != 14) return

        safeLaunch("CatActivity/preloadLegalContext") {
            val previous = withContext(Dispatchers.IO) {
                DocumentLocalRepository.findLatestByCompanyAndType(companyCnpjDigits, "CAT")
                    ?: DocumentLocalRepository.findLatestByCompanyCnpj(companyCnpjDigits)
            } ?: return@safeLaunch

            val context = autofillService.extractPreviousDocumentContext(
                payload = previous.payloadJson,
                signedBy = previous.signedBy
            )

            var applied = false
            if (binding.catReportedByInput.text.isNullOrBlank() && !context.reportedBy.isNullOrBlank()) {
                binding.catReportedByInput.setText(context.reportedBy)
                applied = true
            }

            if (applied) {
                binding.catCnpjStatusText.text = getString(
                    R.string.prefill_context_reused_template,
                    previous.documentType,
                    previous.documentId
                )
            }
        }
    }

    private fun clearFieldErrors() {
        binding.catCompanyCnpjInput.error = null
        binding.catCompanyNameInput.error = null
        binding.catEmployeeNameInput.error = null
        binding.catEmployeeCpfInput.error = null
        binding.catDescriptionInput.error = null
        binding.catReportedByInput.error = null
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
        if (requestCompanyCnpj.length == 14 && binding.catCompanyCnpjInput.text.isNullOrBlank()) {
            binding.catCompanyCnpjInput.setText(requestCompanyCnpj)
            lastPrefilledCnpj = requestCompanyCnpj
            preloadLegalContext(requestCompanyCnpj)
        }
        if (requestCompanyName.isNotBlank() && binding.catCompanyNameInput.text.isNullOrBlank()) {
            binding.catCompanyNameInput.setText(requestCompanyName)
        }
        if (!sourceRequestCode.isNullOrBlank()) {
            binding.catStatusText.text = getString(R.string.service_request_linked_status_template, sourceRequestCode)
        }
    }

    private fun showValidationErrors(missingFields: List<CatField>) {
        clearFieldErrors()
        missingFields.forEach {
            when (it) {
                CatField.COMPANY_CNPJ -> binding.catCompanyCnpjInput.error = getString(R.string.cat_error_cnpj_invalid)
                CatField.COMPANY_NAME -> binding.catCompanyNameInput.error = getString(R.string.cat_error_required)
                CatField.EMPLOYEE_NAME -> binding.catEmployeeNameInput.error = getString(R.string.cat_error_required)
                CatField.EMPLOYEE_CPF -> binding.catEmployeeCpfInput.error = getString(R.string.cat_error_cpf_invalid)
                CatField.DESCRIPTION -> binding.catDescriptionInput.error = getString(R.string.cat_error_required)
                CatField.REPORTED_BY -> binding.catReportedByInput.error = getString(R.string.cat_error_required)
            }
        }
        when (missingFields.first()) {
            CatField.COMPANY_CNPJ -> binding.catCompanyCnpjInput.requestFocus()
            CatField.COMPANY_NAME -> binding.catCompanyNameInput.requestFocus()
            CatField.EMPLOYEE_NAME -> binding.catEmployeeNameInput.requestFocus()
            CatField.EMPLOYEE_CPF -> binding.catEmployeeCpfInput.requestFocus()
            CatField.DESCRIPTION -> binding.catDescriptionInput.requestFocus()
            CatField.REPORTED_BY -> binding.catReportedByInput.requestFocus()
        }
    }

    private fun signCurrentDocument() {
        if (isOperationInProgress) return
        val documentId = currentDocumentId
        if (documentId.isNullOrBlank() || currentPayload.isBlank()) {
            showToast(R.string.cat_error_generate_before_sign)
            return
        }
        if (!DocumentSignatureHelper.canAuthenticate(this)) {
            showToast(R.string.cat_error_biometric_unavailable)
            return
        }
        val reportedBy = binding.catReportedByInput.text.toString().trim()
            .ifBlank { getString(R.string.cat_signed_by_fallback) }
        setOperationInProgress(true)
        DocumentSignatureHelper.signWithBiometric(
            activity = this,
            payload = currentPayload,
            signedBy = reportedBy,
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
                    }.onFailure { CrashShield.recordRecoverableError("CatActivity/markSigned", it) }
                }
                runOnUiThread {
                    val dtFmt2 = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))
                    binding.catStatusText.text = getString(
                        R.string.cat_signed_status_template,
                        reportedBy,
                        dtFmt2.format(Date(record.signedAt))
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
            showToast(R.string.cat_error_generate_before_export)
            return
        }
        setOperationInProgress(true)
        safeLaunch("CatActivity/exportPdf") {
            try {
                runCatching {
                    withContext(Dispatchers.IO) {
                        artifactExportService.exportPlainTextPdfAndAudit(
                            context = this@CatActivity,
                            title = getString(R.string.cat_export_title),
                            body = currentPayload,
                            documentId = currentDocumentId,
                            filePrefix = "cat"
                        )
                    }
                }.onSuccess { result ->
                    binding.catStatusText.text = getString(
                        R.string.cat_export_status_template,
                        result.pdfFile.absolutePath,
                        result.auditFile?.absolutePath ?: getString(R.string.audit_not_generated)
                    )
                }.onFailure {
                    showToast(R.string.cat_error_export_failed)
                }
            } finally {
                setOperationInProgress(false)
            }
        }
    }

    private fun setupRequiredFieldsSummary() {
        val updater = {
            val missing = CatFormValidator.validate(
                CatFormData(
                    companyCnpj = binding.catCompanyCnpjInput.text?.toString().orEmpty(),
                    companyName = binding.catCompanyNameInput.text?.toString().orEmpty().trim(),
                    employeeName = binding.catEmployeeNameInput.text?.toString().orEmpty().trim(),
                    employeeCpf = binding.catEmployeeCpfInput.text?.toString().orEmpty(),
                    description = binding.catDescriptionInput.text?.toString().orEmpty().trim(),
                    reportedBy = binding.catReportedByInput.text?.toString().orEmpty().trim()
                )
            )
            updateRequiredFieldsSummary(missing)
        }
        binding.catCompanyCnpjInput.doAfterTextChanged { updater() }
        binding.catCompanyCnpjInput.doAfterTextChanged { value ->
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
        binding.catCompanyNameInput.doAfterTextChanged { updater() }
        binding.catEmployeeNameInput.doAfterTextChanged { updater() }
        binding.catEmployeeCpfInput.doAfterTextChanged { updater() }
        binding.catDescriptionInput.doAfterTextChanged { updater() }
        binding.catReportedByInput.doAfterTextChanged { updater() }
        updater()
    }

    private fun updateRequiredFieldsSummary(missingFields: List<CatField>) {
        binding.catRequiredSummaryText.text = if (missingFields.isEmpty()) {
            getString(R.string.cat_required_ready)
        } else {
            val labels = missingFields.joinToString(", ") { getString(fieldLabelRes(it)) }
            getString(R.string.cat_required_pending_template, labels)
        }
    }

    @StringRes
    private fun validationMessageFor(field: CatField): Int {
        return when (field) {
            CatField.COMPANY_CNPJ -> R.string.cat_error_message_cnpj_invalid
            CatField.COMPANY_NAME -> R.string.cat_error_message_company_name_required
            CatField.EMPLOYEE_NAME -> R.string.cat_error_message_employee_name_required
            CatField.EMPLOYEE_CPF -> R.string.cat_error_message_employee_cpf_invalid
            CatField.DESCRIPTION -> R.string.cat_error_message_description_required
            CatField.REPORTED_BY -> R.string.cat_error_message_reported_by_required
        }
    }

    @StringRes
    private fun fieldLabelRes(field: CatField): Int {
        return when (field) {
            CatField.COMPANY_CNPJ -> R.string.cat_required_label_cnpj
            CatField.COMPANY_NAME -> R.string.cat_required_label_company_name
            CatField.EMPLOYEE_NAME -> R.string.cat_required_label_employee_name
            CatField.EMPLOYEE_CPF -> R.string.cat_required_label_employee_cpf
            CatField.DESCRIPTION -> R.string.cat_required_label_description
            CatField.REPORTED_BY -> R.string.cat_required_label_reported_by
        }
    }

    private fun setOperationInProgress(inProgress: Boolean) {
        isOperationInProgress = inProgress
        binding.generateCatButton.isEnabled = !inProgress
        binding.signCatButton.isEnabled = !inProgress && currentPayload.isNotBlank()
        binding.exportCatPdfButton.isEnabled = !inProgress && currentPayload.isNotBlank()
    }

    private fun showToast(@StringRes resId: Int) {
        Toast.makeText(this, getString(resId), Toast.LENGTH_SHORT).show()
    }
}
