package com.example.anda.feature.documents

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.anda.core.stability.safeLaunch
import com.example.anda.data.local.AppDatabase
import com.example.anda.data.local.entity.DocumentEntity
import com.example.anda.R
import com.example.anda.data.repository.DocumentLocalRepository
import com.example.anda.data.repository.SstRepository
import com.example.anda.data.services.SyncMonitoringService
import com.example.anda.data.services.ZipBundleExportService
import com.example.anda.databinding.ActivityDocumentsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.io.File
import java.util.Date
import java.util.Locale

class DocumentsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDocumentsBinding
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))
    private var loadedDocuments: List<DocumentEntity> = emptyList()
    private var statusRefreshJob: Job? = null
    private val zipBundleExportService = ZipBundleExportService()
    private var syncMonitoringService: SyncMonitoringService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDocumentsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        DocumentLocalRepository.initialize(this)
        val db = AppDatabase.getInstance(this)
        syncMonitoringService = SyncMonitoringService(db.documentDao(), db.syncAttemptLogDao())

        binding.applyFilterButton.setOnClickListener { loadDocuments() }
        binding.retrySyncButton.setOnClickListener { retrySyncNow() }
        binding.retryFailedButton.setOnClickListener { retryFailedNow() }
        binding.syncSignedUnsyncedButton.setOnClickListener { syncSignedUnsyncedNow() }
        binding.syncAllNowButton.setOnClickListener { syncAllNow() }
        binding.exportAllZipButton.setOnClickListener { exportAllZip() }
        binding.filterAllButton.setOnClickListener { applyStatusFilter("ALL") }
        binding.filterDraftButton.setOnClickListener { applyStatusFilter("DRAFT") }
        binding.filterSignedButton.setOnClickListener { applyStatusFilter("SIGNED") }
        binding.filterSyncedButton.setOnClickListener { applyStatusFilter("SYNCED") }
        binding.filterExpiringButton.setOnClickListener { applyStatusFilter("VENCENDO30D") }
        binding.clearFilterButton.setOnClickListener {
            binding.typeFilterInput.setText("")
            binding.companyFilterInput.setText("")
            binding.titleFilterInput.setText("")
            binding.statusFilterInput.setText("")
            loadDocuments()
        }
        binding.documentsListView.setOnItemClickListener { _, _, position, _ ->
            val selected = loadedDocuments.getOrNull(position) ?: return@setOnItemClickListener
            startActivity(
                Intent(this, DocumentDetailActivity::class.java)
                    .putExtra(DocumentDetailActivity.EXTRA_DOCUMENT_ID, selected.documentId)
            )
        }
        binding.documentsListView.setOnItemLongClickListener { _, _, position, _ ->
            val selected = loadedDocuments.getOrNull(position) ?: return@setOnItemLongClickListener true
            openPdfDirectFromList(selected)
            true
        }

        loadDocuments()
    }

    override fun onStart() {
        super.onStart()
        statusRefreshJob = lifecycleScope.launch {
            while (isActive) {
                refreshPendingOnly()
                delay(5_000)
            }
        }
    }

    override fun onStop() {
        statusRefreshJob?.cancel()
        statusRefreshJob = null
        super.onStop()
    }

    private fun retryFailedNow() {
        safeLaunch("DocumentsActivity/retryFailedNow") {
            val changed = withContext(Dispatchers.IO) { DocumentLocalRepository.retryAllFailedNow() }
            val synced = withContext(Dispatchers.IO) { SstRepository.syncPendingRecords() }
            Toast.makeText(
                this@DocumentsActivity,
                "Falhas reagendadas: $changed | Itens sincronizados: $synced",
                Toast.LENGTH_SHORT
            ).show()
            loadDocuments()
        }
    }

    private fun retrySyncNow() {
        safeLaunch("DocumentsActivity/retrySyncNow") {
            val synced = withContext(Dispatchers.IO) { SstRepository.syncPendingRecords() }
            Toast.makeText(this@DocumentsActivity, "Itens sincronizados: $synced", Toast.LENGTH_SHORT).show()
            loadDocuments()
        }
    }

    private fun syncSignedUnsyncedNow() {
        safeLaunch("DocumentsActivity/syncSignedUnsyncedNow") {
            val queued = withContext(Dispatchers.IO) { DocumentLocalRepository.forceSyncSignedUnsyncedBatch() }
            val synced = withContext(Dispatchers.IO) { SstRepository.syncPendingRecords() }
            Toast.makeText(
                this@DocumentsActivity,
                "Enfileirados: $queued | Sincronizados: $synced",
                Toast.LENGTH_SHORT
            ).show()
            loadDocuments()
        }
    }

    private fun syncAllNow() {
        safeLaunch("DocumentsActivity/syncAllNow") {
            setSyncBusy(true)
            binding.syncProgress.progress = 15

            val queued = withContext(Dispatchers.IO) { DocumentLocalRepository.forceSyncAllBatch() }
            binding.syncProgress.progress = 60

            val synced = withContext(Dispatchers.IO) { SstRepository.syncPendingRecords() }
            binding.syncProgress.progress = 100

            Toast.makeText(
                this@DocumentsActivity,
                "Documentos enfileirados: $queued | Itens sincronizados: $synced",
                Toast.LENGTH_SHORT
            ).show()
            loadDocuments()
            setSyncBusy(false)
        }
    }

    private fun loadDocuments() {
        val typeFilter = binding.typeFilterInput.text.toString().trim().uppercase()
        val companyFilter = digitsOnly(binding.companyFilterInput.text.toString())
        val titleFilter = binding.titleFilterInput.text.toString().trim()
        val statusFilter = binding.statusFilterInput.text.toString().trim().uppercase().ifBlank { "ALL" }

        safeLaunch("DocumentsActivity/loadDocuments") {
            val rawDocs = withContext(Dispatchers.IO) {
                DocumentLocalRepository.listDocuments(typeFilter = typeFilter, companyFilter = companyFilter, titleFilter = titleFilter)
            }
            val docs = rawDocs.filter { matchesStatus(it, statusFilter) }
            loadedDocuments = docs
            val pending = withContext(Dispatchers.IO) { DocumentLocalRepository.pendingSyncCount() }
            val draftCount = docs.count { it.signedAt == null }
            val signedCount = docs.count { it.signedAt != null }
            val syncedCount = docs.count { it.isSynced }

            binding.pendingSyncText.text = getString(R.string.documents_pending_sync_template, pending)
            binding.pendingSyncText.setTextColor(
                ContextCompat.getColor(this@DocumentsActivity, pendingSeverityColor(pending))
            )
            binding.draftCountText.text = getString(R.string.documents_draft_count_template, draftCount)
            binding.draftCountText.setTextColor(
                ContextCompat.getColor(this@DocumentsActivity, if (draftCount > 0) R.color.status_moderate else R.color.status_neutral)
            )
            binding.signedCountText.text = getString(R.string.documents_signed_count_template, signedCount)
            binding.signedCountText.setTextColor(
                ContextCompat.getColor(this@DocumentsActivity, if (signedCount > 0) R.color.status_high else R.color.status_neutral)
            )
            binding.syncedCountText.text = getString(R.string.documents_synced_count_template, syncedCount)
            binding.syncedCountText.setTextColor(
                ContextCompat.getColor(this@DocumentsActivity, if (syncedCount > 0) R.color.status_normal else R.color.status_neutral)
            )
            // Sync health and retry stats from SyncMonitoringService
            val (healthStats, retryStats) = withContext(Dispatchers.IO) {
                val service = syncMonitoringService
                val health = runCatching { service?.getSyncHealthStats() }.getOrNull()
                val retry = runCatching { service?.getRetryStats() }.getOrNull()
                health to retry
            }
            if (healthStats != null) {
                val healthText = getString(
                    R.string.documents_sync_health_template,
                    healthStats.totalDocuments,
                    healthStats.unsyncedDocuments,
                    healthStats.successRate * 100
                )
                val retryText = retryStats?.let {
                    getString(
                        R.string.documents_retry_stats_template,
                        it.totalAttempts,
                        it.averageAttemptsPerDocument,
                        it.maxAttemptsForDocument
                    )
                }.orEmpty()
                binding.syncHealthText.text = if (retryText.isBlank()) {
                    healthText
                } else {
                    getString(R.string.documents_sync_health_with_retry_template, healthText, retryText)
                }
            } else {
                binding.syncHealthText.text = ""
            }
            if (docs.isEmpty()) {
                binding.listHintText.text = getString(R.string.documents_list_empty)
                binding.documentsListView.adapter = null
            } else {
                binding.listHintText.text = getString(R.string.documents_list_hint)
                val versionCounts = withContext(Dispatchers.IO) {
                    docs.associate { doc -> doc.documentId to DocumentLocalRepository.getVersionCount(doc.documentId) }
                }
                val rows = docs.map { doc ->
                    val signed = if (doc.signedAt != null) {
                        "ASSINATURA LOCAL em ${dateFormat.format(Date(doc.signedAt))} por ${doc.signedBy.orEmpty()}"
                    } else {
                        "RASCUNHO"
                    }
                    val pdfStatus = if (doc.pdfPath.isNullOrBlank()) "Sem PDF" else "PDF pronto"
                    val auditStatus = if (doc.auditTxtPath.isNullOrBlank()) "Sem auditoria" else "Auditoria pronta"
                    val expiry = doc.validUntil?.let { dateFormat.format(Date(it)) } ?: "N/A"
                    val versions = versionCounts[doc.documentId] ?: 0
                    """
                    ID: ${doc.documentId}
                    Tipo: ${doc.documentType}
                    Empresa: ${doc.companyCnpj}
                    Titulo: ${doc.title}
                    Status local: $signed
                    Validade: $expiry
                    PDF: $pdfStatus
                    Auditoria: $auditStatus
                    Versoes: $versions
                    Sincronizado: ${if (doc.isSynced) "SIM" else "NAO"}
                    Atualizado: ${dateFormat.format(Date(doc.updatedAt))}
                    """.trimIndent()
                }
                binding.documentsListView.adapter = ArrayAdapter(
                    this@DocumentsActivity,
                    android.R.layout.simple_list_item_1,
                    rows
                )
            }
        }
    }

    private fun refreshPendingOnly() {
        safeLaunch("DocumentsActivity/refreshPendingOnly") {
            val pending = withContext(Dispatchers.IO) { DocumentLocalRepository.pendingSyncCount() }
            binding.pendingSyncText.text = getString(R.string.documents_pending_sync_template, pending)
            binding.pendingSyncText.setTextColor(
                ContextCompat.getColor(this@DocumentsActivity, pendingSeverityColor(pending))
            )
        }
    }

    private fun digitsOnly(input: String): String = input.filter { it.isDigit() }

    private fun pendingSeverityColor(pending: Int): Int {
        return when {
            pending <= 0 -> R.color.status_normal
            pending <= 3 -> R.color.status_moderate
            pending <= 8 -> R.color.status_high
            else -> R.color.status_critical
        }
    }

    private fun openPdfDirectFromList(document: DocumentEntity) {
        val pdfPath = document.pdfPath
        if (pdfPath.isNullOrBlank()) {
            Toast.makeText(this, "Documento sem PDF exportado", Toast.LENGTH_SHORT).show()
            return
        }

        val file = File(pdfPath)
        if (!file.exists()) {
            Toast.makeText(this, "Arquivo PDF nao encontrado no dispositivo", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/pdf")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        runCatching { startActivity(intent) }
            .onFailure {
                val message = if (it is ActivityNotFoundException) {
                    "Nenhum app de PDF encontrado no aparelho"
                } else {
                    "Falha ao abrir PDF"
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
    }

    private fun matchesStatus(doc: DocumentEntity, statusFilter: String): Boolean {
        return when (statusFilter) {
            "ALL" -> true
            "DRAFT" -> doc.signedAt == null
            "SIGNED" -> doc.signedAt != null && !doc.isSynced
            "SYNCED" -> doc.isSynced
            "VENCENDO30D" -> {
                val now = System.currentTimeMillis()
                val until = now + (30L * 24L * 60L * 60L * 1000L)
                val validUntil = doc.validUntil
                validUntil != null && validUntil in now..until
            }
            else -> true
        }
    }

    private fun applyStatusFilter(status: String) {
        binding.statusFilterInput.setText(status)
        loadDocuments()
    }

    private fun setSyncBusy(isBusy: Boolean) {
        binding.syncProgress.visibility = if (isBusy) android.view.View.VISIBLE else android.view.View.GONE
        binding.retrySyncButton.isEnabled = !isBusy
        binding.retryFailedButton.isEnabled = !isBusy
        binding.syncSignedUnsyncedButton.isEnabled = !isBusy
        binding.syncAllNowButton.isEnabled = !isBusy
    }

    private fun exportAllZip() {
        safeLaunch("DocumentsActivity/exportAllZip") {
            val allDocs = withContext(Dispatchers.IO) { DocumentLocalRepository.listDocuments() }
            val exportableDocs = allDocs.filter {
                !it.pdfPath.isNullOrBlank() || !it.auditTxtPath.isNullOrBlank()
            }
            if (exportableDocs.isEmpty()) {
                Toast.makeText(this@DocumentsActivity, "Nenhum PDF ou auditoria disponivel para exportar", Toast.LENGTH_SHORT).show()
                return@safeLaunch
            }
            val result = withContext(Dispatchers.IO) {
                zipBundleExportService.exportDocumentBundle(
                    context = this@DocumentsActivity,
                    baseName = "anda-docs",
                    documents = exportableDocs.map { ZipBundleExportService.BundleDocument.fromEntity(it) }
                )
            }
            val zipFile = result.zipFile
            val uri = FileProvider.getUriForFile(this@DocumentsActivity, "$packageName.fileprovider", zipFile)
            val shareIntent = Intent(Intent.ACTION_SEND)
                .setType("application/zip")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching {
                startActivity(
                    Intent.createChooser(
                        shareIntent,
                        "Compartilhar ZIP (${result.filesAdded} artefatos | ${result.documentsCount} documentos)"
                    )
                )
            }.onFailure {
                Toast.makeText(this@DocumentsActivity, "Falha ao compartilhar ZIP", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

