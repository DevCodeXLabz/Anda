package com.example.anda.feature.requests

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.anda.R
import com.example.anda.data.local.AppDatabase
import com.example.anda.data.local.entity.DocumentEntity
import com.example.anda.data.local.entity.ServiceRequestEntity
import com.example.anda.data.repository.DocumentLocalRepository
import com.example.anda.databinding.ActivityServiceRequestDetailBinding
import com.example.anda.feature.documents.DocumentDetailActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ServiceRequestDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityServiceRequestDetailBinding
    private lateinit var db: AppDatabase
    private val dtFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))

    private var currentRequest: ServiceRequestEntity? = null
    private var currentLinkedDocument: DocumentEntity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServiceRequestDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getInstance(this)
        DocumentLocalRepository.initialize(this)

        binding.openDocumentFlowButton.setOnClickListener {
            val request = currentRequest ?: return@setOnClickListener
            startActivity(ServiceRequestDocumentLauncher.buildIntent(this, request))
        }
        binding.openLinkedDocumentButton.setOnClickListener {
            val document = currentLinkedDocument
            if (document == null) {
                Toast.makeText(this, getString(R.string.service_request_detail_empty_linked), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(
                Intent(this, DocumentDetailActivity::class.java)
                    .putExtra(DocumentDetailActivity.EXTRA_DOCUMENT_ID, document.documentId)
            )
        }
        binding.refreshDetailButton.setOnClickListener { loadData() }
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun loadData() {
        val requestCode = intent.getStringExtra(EXTRA_REQUEST_CODE).orEmpty().trim()
        if (requestCode.isBlank()) {
            finish()
            return
        }

        lifecycleScope.launch {
            val request = withContext(Dispatchers.IO) {
                db.serviceRequestDao().findByRequestCode(requestCode)
            }
            val linkedDocument = withContext(Dispatchers.IO) {
                DocumentLocalRepository.findLatestByRequestCode(requestCode)
            }

            if (request == null) {
                Toast.makeText(this@ServiceRequestDetailActivity, getString(R.string.service_request_detail_not_found), Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            currentRequest = request
            currentLinkedDocument = linkedDocument
            renderRequest(request)
            renderLinkedDocument(linkedDocument)
        }
    }

    private fun renderRequest(request: ServiceRequestEntity) {
        val operational = ServiceRequestOperationalHistoryFormatter.parse(request.notes)
        binding.requestSummaryText.text = getString(
            R.string.service_request_detail_summary_template,
            request.requestCode,
            request.contractorName,
            request.contractorCnpj,
            request.requestedDocumentType,
            statusLabel(request.status),
            assignedLabel(request),
            contactLabel(request),
            dtFmt.format(Date(request.updatedAt)),
            operational.freeNotes.ifBlank { getString(R.string.service_request_detail_no_notes) }
        )
        binding.operationalHistoryText.text = operational.historyLines.ifEmpty {
            listOf(getString(R.string.service_request_detail_no_history))
        }.joinToString("\n")
        binding.freeNotesText.text = operational.freeNotes.ifBlank {
            getString(R.string.service_request_detail_no_notes)
        }
    }

    private fun renderLinkedDocument(document: DocumentEntity?) {
        if (document == null) {
            binding.linkedDocumentText.text = getString(R.string.service_request_detail_empty_linked)
            binding.openLinkedDocumentButton.isEnabled = false
            return
        }
        binding.openLinkedDocumentButton.isEnabled = true
        binding.linkedDocumentText.text = getString(
            R.string.service_request_detail_linked_template,
            document.documentId,
            document.documentType,
            deriveOperationalStatus(document),
            if (document.signedAt != null) getString(R.string.service_request_detail_signed_yes) else getString(R.string.service_request_detail_signed_no),
            if (document.isSynced) getString(R.string.service_request_detail_synced_yes) else getString(R.string.service_request_detail_synced_no)
        )
    }

    private fun deriveOperationalStatus(document: DocumentEntity): String {
        return when {
            document.isSynced -> getString(R.string.service_request_ops_synced).substringAfter(": ")
            document.signedAt != null && !document.pdfPath.isNullOrBlank() -> getString(R.string.service_request_ops_signed_pdf).substringAfter(": ")
            document.signedAt != null -> getString(R.string.service_request_ops_signed).substringAfter(": ")
            !document.pdfPath.isNullOrBlank() -> getString(R.string.service_request_ops_pdf).substringAfter(": ")
            else -> getString(R.string.service_request_ops_draft).substringAfter(": ")
        }
    }

    private fun statusLabel(status: String): String {
        return when (com.example.anda.data.requests.ServiceRequestStatus.normalize(status)) {
            com.example.anda.data.requests.ServiceRequestStatus.OPEN -> getString(R.string.service_request_status_open)
            com.example.anda.data.requests.ServiceRequestStatus.ASSIGNED -> getString(R.string.service_request_status_assigned)
            com.example.anda.data.requests.ServiceRequestStatus.IN_PROGRESS -> getString(R.string.service_request_status_in_progress)
            com.example.anda.data.requests.ServiceRequestStatus.COMPLETED -> getString(R.string.service_request_status_done)
            else -> status
        }
    }

    private fun assignedLabel(request: ServiceRequestEntity): String {
        return if (request.assignedEmployeeName.isBlank()) {
            getString(R.string.service_request_assignee_unassigned)
        } else {
            getString(R.string.service_request_assignee_template, request.assignedEmployeeName)
        }
    }

    private fun contactLabel(request: ServiceRequestEntity): String {
        return if (request.preferredContactChannel == "whatsapp") {
            if (request.contactWhatsapp.isBlank()) {
                getString(R.string.service_request_contact_whatsapp_missing)
            } else {
                getString(R.string.service_request_contact_whatsapp_template, request.contactWhatsapp)
            }
        } else {
            getString(R.string.service_request_contact_app)
        }
    }

    companion object {
        const val EXTRA_REQUEST_CODE = "extra_request_code"
    }
}

