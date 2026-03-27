package com.example.anda.feature.apr

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
import com.example.anda.databinding.ActivityAprBinding
import com.example.anda.feature.companies.CompanyManagementActivity
import com.example.anda.feature.requests.ServiceRequestIntentContract
import com.example.anda.feature.security.DocumentSignatureHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AprActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAprBinding
    private val autofillService = AutofillService()
    private val generationService = DocumentGenerationService()
    private val artifactExportService = DocumentArtifactExportService()
    private val dtFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))

    private var currentDocumentId: String? = null
    private var currentPayload = ""
    private var isOperationInProgress = false
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
            if (cnpj.isNotBlank()) binding.aprCnpjInput.setText(cnpj)
            if (name.isNotBlank()) binding.aprCompanyNameInput.setText(name)
            if (cnae.isNotBlank()) binding.aprCnaeInput.setText(cnae)
            binding.aprCnpjStatusText.text = getString(R.string.doc_pick_company_selected_local)
            preloadLegalContext(cnpj.filter(Char::isDigit))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAprBinding.inflate(layoutInflater)
        setContentView(binding.root)
        DocumentLocalRepository.initialize(this)
        SstRepository.initialize(this)
        applyRequestContextFromIntent()
        binding.signAprButton.isEnabled = false
        binding.exportAprPdfButton.isEnabled = false
        setupRequiredFieldsSummary()

        binding.aprPickCompanyButton.setOnClickListener {
            pickCompanyLauncher.launch(CompanyManagementActivity.buildPickIntent(this))
        }

        binding.aprLookupCnpjButton.setOnClickListener {
            val cnpj = binding.aprCnpjInput.text.toString().trim()
            if (AprFormValidator.digitsOnly(cnpj).length != 14) {
                binding.aprCnpjStatusText.text = getString(R.string.apr_lookup_invalid_cnpj)
                return@setOnClickListener
            }
            binding.aprCnpjStatusText.text = getString(R.string.apr_lookup_searching)
            binding.aprLookupCnpjButton.isEnabled = false
            lifecycleScope.launch { SstRepository.lookupCompanyByCnpj(cnpj) }
        }
        binding.generateAprButton.setOnClickListener { generate() }
        binding.signAprButton.setOnClickListener { signCurrentDocument() }
        binding.exportAprPdfButton.setOnClickListener { exportPdf() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SstRepository.companyLookupState.collect { s ->
                    when {
                        s.isLoading -> {
                            binding.aprCnpjStatusText.text = getString(R.string.apr_lookup_searching)
                            binding.aprLookupCnpjButton.isEnabled = false
                        }
                        s.company != null -> {
                            val c = s.company
                            if (binding.aprCompanyNameInput.text.isNullOrBlank()) binding.aprCompanyNameInput.setText(c.legalName)
                            if (binding.aprCnaeInput.text.isNullOrBlank()) binding.aprCnaeInput.setText(c.cnae)
                            binding.aprCnpjStatusText.text = c.legalName.take(40)
                            preloadLegalContext(c.cnpj.filter(Char::isDigit))
                            binding.aprLookupCnpjButton.isEnabled = true
                        }
                        !s.errorMessage.isNullOrBlank() -> {
                            binding.aprCnpjStatusText.text = getString(
                                R.string.apr_lookup_not_found_template,
                                s.errorMessage
                            )
                            binding.aprLookupCnpjButton.isEnabled = true
                        }
                        else -> binding.aprLookupCnpjButton.isEnabled = true
                    }
                }
            }
        }
    }

    private fun generate() {
        if (isOperationInProgress) return
        clearFieldErrors()

        val cnpj = AprFormValidator.digitsOnly(binding.aprCnpjInput.text.toString())
        val companyName = binding.aprCompanyNameInput.text.toString().trim()
        val cnae = binding.aprCnaeInput.text.toString().trim()
        val task = binding.aprTaskNameInput.text.toString().trim()
        val technician = binding.aprTechnicianInput.text.toString().trim()
        val workers = binding.aprAuthorizationsInput.text.toString().trim().lines().filter { it.isNotBlank() }

        val missingFields = AprFormValidator.validate(
            AprFormData(cnpj = cnpj, taskName = task, technician = technician)
        )
        if (missingFields.isNotEmpty()) {
            showValidationErrors(missingFields)
            updateRequiredFieldsSummary(missingFields)
            showToast(validationMessageFor(missingFields.first()))
            return
        }

        setOperationInProgress(true)
        safeLaunch("AprActivity/generate") {
            try {
                val risks = autofillService.detectRisksByCnae(cnae)
                val domainCompany = com.example.anda.domain.CompanyProfile(
                    cnpj = cnpj,
                    legalName = companyName,
                    tradeName = "",
                    cnae = cnae
                )
                val html = generationService.generateAPR(
                    taskName = task,
                    companyProfile = domainCompany,
                    technician = technician,
                    risks = risks,
                    authorizations = workers
                )
                val id = "apr-${System.currentTimeMillis()}"
                currentDocumentId = id
                currentPayload = html
                withContext(Dispatchers.IO) {
                    DocumentLocalRepository.saveDraft(
                        DocumentEntity(
                            documentId = id,
                            documentType = "APR",
                            companyCnpj = cnpj,
                            title = getString(R.string.apr_document_title_template, task),
                            payloadJson = html,
                            contentHash = DocumentSignatureHelper.sha256(html),
                            sourceRequestCode = sourceRequestCode
                        )
                    )
                }
                binding.aprPreviewText.text = html.take(600)
                binding.signAprButton.isEnabled = true
                binding.exportAprPdfButton.isEnabled = true
                binding.aprStatusText.text = getString(R.string.apr_generated_template, id)
                updateRequiredFieldsSummary(emptyList())
            } finally {
                setOperationInProgress(false)
            }
        }
    }

    private fun preloadLegalContext(companyCnpjDigits: String) {
        if (companyCnpjDigits.length != 14) return

        safeLaunch("AprActivity/preloadLegalContext") {
            val previous = withContext(Dispatchers.IO) {
                DocumentLocalRepository.findLatestByCompanyAndType(companyCnpjDigits, "APR")
                    ?: DocumentLocalRepository.findLatestByCompanyCnpj(companyCnpjDigits)
            } ?: return@safeLaunch

            val context = autofillService.extractPreviousDocumentContext(
                payload = previous.payloadJson,
                signedBy = previous.signedBy
            )

            var applied = false
            if (binding.aprTechnicianInput.text.isNullOrBlank() && !context.responsibleName.isNullOrBlank()) {
                binding.aprTechnicianInput.setText(context.responsibleName)
                applied = true
            }

            if (applied) {
                binding.aprCnpjStatusText.text = getString(
                    R.string.prefill_context_reused_template,
                    previous.documentType,
                    previous.documentId
                )
            }
        }
    }

    private fun clearFieldErrors() {
        binding.aprCnpjInput.error = null
        binding.aprTaskNameInput.error = null
        binding.aprTechnicianInput.error = null
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
        if (requestCompanyCnpj.length == 14 && binding.aprCnpjInput.text.isNullOrBlank()) {
            binding.aprCnpjInput.setText(requestCompanyCnpj)
            lastPrefilledCnpj = requestCompanyCnpj
            preloadLegalContext(requestCompanyCnpj)
        }
        if (requestCompanyName.isNotBlank() && binding.aprCompanyNameInput.text.isNullOrBlank()) {
            binding.aprCompanyNameInput.setText(requestCompanyName)
        }
        if (!sourceRequestCode.isNullOrBlank()) {
            binding.aprStatusText.text = getString(R.string.service_request_linked_status_template, sourceRequestCode)
        }
    }

    private fun showValidationErrors(missingFields: List<AprField>) {
        clearFieldErrors()
        missingFields.forEach {
            when (it) {
                AprField.COMPANY_CNPJ -> binding.aprCnpjInput.error = getString(R.string.apr_error_cnpj_invalid_field)
                AprField.TASK_NAME -> binding.aprTaskNameInput.error = getString(R.string.apr_error_required_field)
                AprField.TECHNICIAN -> binding.aprTechnicianInput.error = getString(R.string.apr_error_required_field)
            }
        }
        when (missingFields.first()) {
            AprField.COMPANY_CNPJ -> binding.aprCnpjInput.requestFocus()
            AprField.TASK_NAME -> binding.aprTaskNameInput.requestFocus()
            AprField.TECHNICIAN -> binding.aprTechnicianInput.requestFocus()
        }
    }

    private fun setupRequiredFieldsSummary() {
        val updater = {
            val missing = AprFormValidator.validate(
                AprFormData(
                    cnpj = binding.aprCnpjInput.text?.toString().orEmpty(),
                    taskName = binding.aprTaskNameInput.text?.toString().orEmpty().trim(),
                    technician = binding.aprTechnicianInput.text?.toString().orEmpty().trim()
                )
            )
            updateRequiredFieldsSummary(missing)
        }
        binding.aprCnpjInput.doAfterTextChanged { updater() }
        binding.aprCnpjInput.doAfterTextChanged { value ->
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
        binding.aprTaskNameInput.doAfterTextChanged { updater() }
        binding.aprTechnicianInput.doAfterTextChanged { updater() }
        updater()
    }

    private fun updateRequiredFieldsSummary(missingFields: List<AprField>) {
        binding.aprRequiredSummaryText.text = if (missingFields.isEmpty()) {
            getString(R.string.apr_required_ready)
        } else {
            val labels = missingFields.joinToString(", ") { getString(fieldLabelRes(it)) }
            getString(R.string.apr_required_pending_template, labels)
        }
    }

    @StringRes
    private fun validationMessageFor(field: AprField): Int {
        return when (field) {
            AprField.COMPANY_CNPJ -> R.string.apr_error_message_cnpj_invalid
            AprField.TASK_NAME -> R.string.apr_error_message_task_required
            AprField.TECHNICIAN -> R.string.apr_error_message_technician_required
        }
    }

    @StringRes
    private fun fieldLabelRes(field: AprField): Int {
        return when (field) {
            AprField.COMPANY_CNPJ -> R.string.apr_required_label_cnpj
            AprField.TASK_NAME -> R.string.apr_required_label_task
            AprField.TECHNICIAN -> R.string.apr_required_label_technician
        }
    }

    private fun signCurrentDocument() {
        if (isOperationInProgress) return
        val documentId = currentDocumentId
        if (documentId.isNullOrBlank() || currentPayload.isBlank()) {
            showToast(R.string.apr_error_generate_before_sign)
            return
        }
        if (!DocumentSignatureHelper.canAuthenticate(this)) {
            showToast(R.string.apr_error_biometric_unavailable)
            return
        }
        val technician = binding.aprTechnicianInput.text.toString().trim()
            .ifBlank { getString(R.string.apr_signed_by_fallback) }
        setOperationInProgress(true)
        DocumentSignatureHelper.signWithBiometric(
            activity = this, payload = currentPayload, signedBy = technician,
            onSuccess = { record ->
                lifecycleScope.launch(Dispatchers.IO) {
                    runCatching {
                        DocumentLocalRepository.markSigned(documentId = documentId, signedAt = record.signedAt, signedBy = record.signedBy, contentHash = record.contentHash, signatureB64 = record.signatureB64, publicKeyFingerprint = record.publicKeyFingerprint, signatureAlgorithm = "SHA256withRSA")
                    }.onFailure { CrashShield.recordRecoverableError("AprActivity/markSigned", it) }
                }
                runOnUiThread {
                    binding.aprStatusText.text = getString(
                        R.string.apr_signed_status_template,
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
            showToast(R.string.apr_error_generate_before_export)
            return
        }
        setOperationInProgress(true)
        safeLaunch("AprActivity/pdf") {
            try {
                runCatching {
                    withContext(Dispatchers.IO) {
                        artifactExportService.exportPlainTextPdfAndAudit(
                            context = this@AprActivity,
                            title = getString(R.string.apr_export_title),
                            body = currentPayload,
                            documentId = currentDocumentId,
                            filePrefix = "apr"
                        )
                    }
                }
                    .onSuccess {
                        binding.aprStatusText.text = getString(
                            R.string.apr_export_status_template,
                            it.pdfFile.absolutePath,
                            it.auditFile?.absolutePath ?: getString(R.string.audit_not_generated)
                        )
                    }
                    .onFailure { showToast(R.string.apr_error_export_failed) }
            } finally {
                setOperationInProgress(false)
            }
        }
    }

    private fun setOperationInProgress(inProgress: Boolean) {
        isOperationInProgress = inProgress
        binding.generateAprButton.isEnabled = !inProgress
        binding.signAprButton.isEnabled = !inProgress && currentPayload.isNotBlank()
        binding.exportAprPdfButton.isEnabled = !inProgress && currentPayload.isNotBlank()
    }

    private fun showToast(@StringRes resId: Int) {
        Toast.makeText(this, getString(resId), Toast.LENGTH_SHORT).show()
    }
}
