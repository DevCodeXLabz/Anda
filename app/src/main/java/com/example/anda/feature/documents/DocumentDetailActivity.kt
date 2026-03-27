package com.example.anda.feature.documents

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.anda.core.profile.ProfileManager
import com.example.anda.core.profile.UserProfile
import com.example.anda.core.stability.safeLaunch
import com.example.anda.data.repository.DocumentLocalRepository
import com.example.anda.data.services.DocumentArtifactExportService
import com.example.anda.data.services.ZipBundleExportService
import com.example.anda.data.repository.SstRepository
import com.example.anda.databinding.ActivityDocumentDetailBinding
import com.example.anda.R
import com.example.anda.feature.security.DocumentSignatureHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DocumentDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDocumentDetailBinding
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))
    private var currentDocument = null as com.example.anda.data.local.entity.DocumentEntity?
    private var currentPdfPath: String? = null
    private var currentAuditPath: String? = null
    private var currentZipPath: String? = null
    private var isOperationInProgress: Boolean = false
    private val zipBundleExportService = ZipBundleExportService()
    private val artifactExportService = DocumentArtifactExportService()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDocumentDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val documentId = intent.getStringExtra(EXTRA_DOCUMENT_ID).orEmpty()
        if (documentId.isBlank()) {
            finish()
            return
        }
        DocumentLocalRepository.initialize(this)

        binding.forceSyncButton.setOnClickListener {
            safeLaunch("DocumentDetailActivity/forceSync") {
                withContext(Dispatchers.IO) {
                    DocumentLocalRepository.forceSyncDocument(documentId)
                    SstRepository.syncPendingRecords()
                }
                Toast.makeText(this@DocumentDetailActivity, "Sincronizacao forçada enviada", Toast.LENGTH_SHORT).show()
                loadDocument(documentId)
            }
        }
        binding.signDocumentButton.setOnClickListener { signCurrentDocument() }
        binding.exportArtifactsButton.setOnClickListener { exportPdfAndAudit() }
        binding.openPdfButton.setOnClickListener { openCurrentPdf() }
        binding.copyPdfPathButton.setOnClickListener { copyPdfPath() }
        binding.sharePdfButton.setOnClickListener { shareCurrentPdf() }
        binding.openAuditButton.setOnClickListener { openCurrentAuditTxt() }
        binding.shareAuditButton.setOnClickListener { shareCurrentAuditTxt() }
        binding.exportZipButton.setOnClickListener { exportZipBundle() }
        binding.shareZipButton.setOnClickListener { shareCurrentZip() }

        loadDocument(documentId)
    }

    private fun loadDocument(documentId: String) {
        safeLaunch("DocumentDetailActivity/loadDocument") {
            val doc = withContext(Dispatchers.IO) { DocumentLocalRepository.findByDocumentId(documentId) }
            val attempts = withContext(Dispatchers.IO) { DocumentLocalRepository.listSyncAttempts(documentId) }
            val versions = withContext(Dispatchers.IO) { DocumentLocalRepository.listVersionHistory(documentId) }

            if (doc == null) {
                binding.detailText.text = "Documento nao encontrado"
                binding.attemptsText.text = ""
                binding.versionsText.text = ""
                return@safeLaunch
            }

            val signed = if (doc.signedAt != null) {
                "Assinatura operacional local em ${dateFormat.format(Date(doc.signedAt))} por ${doc.signedBy.orEmpty()}"
            } else {
                "Nao assinado"
            }

            binding.detailText.text = """
                ID: ${doc.documentId}
                Tipo: ${doc.documentType}
                Empresa: ${doc.companyCnpj}
                Titulo: ${doc.title}
                Status assinatura: $signed
                Observacao juridica: este registro nao substitui formalizacao juridica definida pela operacao.
                PDF local: ${doc.pdfPath ?: "Nao exportado"}
                Auditoria TXT: ${doc.auditTxtPath ?: "Nao exportado"}
                Sincronizado: ${if (doc.isSynced) "SIM" else "NAO"}
                Atualizado: ${dateFormat.format(Date(doc.updatedAt))}
            """.trimIndent()
            currentDocument = doc
            currentPdfPath = doc.pdfPath
            currentAuditPath = doc.auditTxtPath
            currentZipPath = null

            binding.attemptsText.text = if (attempts.isEmpty()) {
                "Sem historico de tentativas"
            } else {
                attempts.joinToString("\n") {
                    "${dateFormat.format(Date(it.createdAt))} | ${it.status} | tentativa ${it.attemptNumber} | ${it.message}"
                }
            }

            binding.versionsText.text = if (versions.isEmpty()) {
                getString(R.string.document_detail_versions_empty)
            } else {
                versions.joinToString("\n") { v ->
                    val current = if (v.isCurrent) " [ATUAL]" else ""
                    "v${v.versionNumber}$current · ${v.changeType} · ${v.editedBy} · ${dateFormat.format(Date(v.createdAt))}"
                }
            }
        }
    }

    private fun openCurrentPdf() {
        val pdfPath = currentPdfPath
        if (pdfPath.isNullOrBlank()) {
            Toast.makeText(this, "Nenhum PDF exportado para este documento", Toast.LENGTH_SHORT).show()
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
                val msg = if (it is ActivityNotFoundException) {
                    "Nenhum app de PDF encontrado no aparelho"
                } else {
                    "Falha ao abrir PDF"
                }
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
    }

    private fun copyPdfPath() {
        val pdfPath = currentPdfPath
        if (pdfPath.isNullOrBlank()) {
            Toast.makeText(this, "Nenhum PDF exportado para copiar", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newPlainText("pdf_path", pdfPath)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Caminho do PDF copiado", Toast.LENGTH_SHORT).show()
    }

    private fun shareCurrentPdf() {
        val pdfPath = currentPdfPath
        if (pdfPath.isNullOrBlank()) {
            Toast.makeText(this, "Nenhum PDF exportado para compartilhar", Toast.LENGTH_SHORT).show()
            return
        }

        val file = File(pdfPath)
        if (!file.exists()) {
            Toast.makeText(this, "Arquivo PDF nao encontrado no dispositivo", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        runCatching {
            startActivity(Intent.createChooser(intent, "Compartilhar PDF"))
        }.onFailure {
            Toast.makeText(this, "Falha ao compartilhar PDF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openCurrentAuditTxt() {
        val auditPath = currentAuditPath
        if (auditPath.isNullOrBlank()) {
            Toast.makeText(this, "Nenhum TXT de auditoria exportado", Toast.LENGTH_SHORT).show()
            return
        }

        val file = File(auditPath)
        if (!file.exists()) {
            Toast.makeText(this, "Arquivo de auditoria nao encontrado", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "text/plain")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        runCatching { startActivity(intent) }
            .onFailure {
                val msg = if (it is ActivityNotFoundException) {
                    "Nenhum app de texto encontrado no aparelho"
                } else {
                    "Falha ao abrir auditoria TXT"
                }
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
    }

    private fun shareCurrentAuditTxt() {
        val auditPath = currentAuditPath
        if (auditPath.isNullOrBlank()) {
            Toast.makeText(this, "Nenhum TXT de auditoria para compartilhar", Toast.LENGTH_SHORT).show()
            return
        }

        val file = File(auditPath)
        if (!file.exists()) {
            Toast.makeText(this, "Arquivo de auditoria nao encontrado", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        runCatching {
            startActivity(Intent.createChooser(intent, "Compartilhar auditoria TXT"))
        }.onFailure {
            Toast.makeText(this, "Falha ao compartilhar auditoria TXT", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportZipBundle() {
        val document = currentDocument
        if (document == null) {
            Toast.makeText(this, "Documento ainda nao carregado", Toast.LENGTH_SHORT).show()
            return
        }

        if (document.pdfPath.isNullOrBlank() && document.auditTxtPath.isNullOrBlank()) {
            Toast.makeText(this, "Nenhum arquivo para montar pacote ZIP", Toast.LENGTH_SHORT).show()
            return
        }

        safeLaunch("DocumentDetailActivity/exportZipBundle") {
            val result = withContext(Dispatchers.IO) {
                zipBundleExportService.exportDocumentBundle(
                    context = this@DocumentDetailActivity,
                    baseName = "document-bundle",
                    documents = listOf(ZipBundleExportService.BundleDocument.fromEntity(document))
                )
            }
            currentZipPath = result.zipFile.absolutePath
            Toast.makeText(
                this@DocumentDetailActivity,
                "ZIP exportado: ${result.zipFile.absolutePath} | ${result.filesAdded} artefato(s)",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun shareCurrentZip() {
        val zipPath = currentZipPath
        if (zipPath.isNullOrBlank()) {
            Toast.makeText(this, "Exporte o pacote ZIP antes de compartilhar", Toast.LENGTH_SHORT).show()
            return
        }

        val file = File(zipPath)
        if (!file.exists()) {
            Toast.makeText(this, "Arquivo ZIP nao encontrado", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND)
            .setType("application/zip")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        runCatching {
            startActivity(Intent.createChooser(intent, "Compartilhar pacote ZIP"))
        }.onFailure {
            Toast.makeText(this, "Falha ao compartilhar pacote ZIP", Toast.LENGTH_SHORT).show()
        }
    }

    private fun signCurrentDocument() {
        if (isOperationInProgress) {
            Toast.makeText(this, getString(R.string.document_detail_operation_in_progress), Toast.LENGTH_SHORT).show()
            return
        }
        val doc = currentDocument
        if (doc == null) {
            Toast.makeText(this, getString(R.string.document_detail_no_data), Toast.LENGTH_SHORT).show()
            return
        }
        if (doc.signedAt != null) {
            Toast.makeText(this, getString(R.string.document_detail_already_signed), Toast.LENGTH_SHORT).show()
            return
        }
        if (doc.payloadJson.isBlank()) {
            Toast.makeText(this, getString(R.string.document_detail_sign_requires_content), Toast.LENGTH_SHORT).show()
            return
        }
        if (!DocumentSignatureHelper.canAuthenticate(this)) {
            Toast.makeText(this, getString(R.string.document_detail_sign_requires_auth), Toast.LENGTH_SHORT).show()
            return
        }

        setOperationInProgress(true)
        Toast.makeText(this, getString(R.string.document_detail_signing_started), Toast.LENGTH_SHORT).show()
        val signer = resolveSigner(doc)
        DocumentSignatureHelper.signWithBiometric(
            activity = this,
            payload = doc.payloadJson,
            signedBy = signer,
            onSuccess = { record ->
                lifecycleScope.launch(Dispatchers.IO) {
                    runCatching {
                        DocumentLocalRepository.markSigned(
                            documentId = doc.documentId,
                            signedAt = record.signedAt,
                            signedBy = record.signedBy,
                            contentHash = record.contentHash,
                            signatureB64 = record.signatureB64,
                            publicKeyFingerprint = record.publicKeyFingerprint,
                            signatureAlgorithm = "SHA256withRSA"
                        )
                    }.onSuccess {
                        withContext(Dispatchers.Main) {
                            setOperationInProgress(false)
                            Toast.makeText(
                                this@DocumentDetailActivity,
                                getString(R.string.document_detail_sign_success_with_signer, signer),
                                Toast.LENGTH_SHORT
                            ).show()
                            loadDocument(doc.documentId)
                        }
                    }.onFailure {
                        withContext(Dispatchers.Main) {
                            setOperationInProgress(false)
                            Toast.makeText(this@DocumentDetailActivity, it.message ?: getString(R.string.document_detail_export_error), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onError = {
                setOperationInProgress(false)
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun exportPdfAndAudit() {
        if (isOperationInProgress) {
            Toast.makeText(this, getString(R.string.document_detail_operation_in_progress), Toast.LENGTH_SHORT).show()
            return
        }
        val doc = currentDocument
        if (doc == null) {
            Toast.makeText(this, getString(R.string.document_detail_no_data), Toast.LENGTH_SHORT).show()
            return
        }
        if (doc.payloadJson.isBlank()) {
            Toast.makeText(this, getString(R.string.document_detail_sign_requires_content), Toast.LENGTH_SHORT).show()
            return
        }

        setOperationInProgress(true)
        Toast.makeText(this, getString(R.string.document_detail_exporting_started), Toast.LENGTH_SHORT).show()
        safeLaunch("DocumentDetailActivity/exportPdfAndAudit") {
            try {
                runCatching {
                    withContext(Dispatchers.IO) {
                        artifactExportService.exportPlainTextPdfAndAudit(
                            context = this@DocumentDetailActivity,
                            documentId = doc.documentId,
                            title = "${doc.documentType} - ANDA",
                            body = doc.payloadJson,
                            filePrefix = doc.documentType.lowercase(Locale.ROOT)
                        )
                    }
                }.onSuccess {
                    Toast.makeText(
                        this@DocumentDetailActivity,
                        getString(R.string.document_detail_export_success_with_file, it.pdfFile.name),
                        Toast.LENGTH_SHORT
                    ).show()
                    loadDocument(doc.documentId)
                }.onFailure {
                    Toast.makeText(this@DocumentDetailActivity, getString(R.string.document_detail_export_error), Toast.LENGTH_SHORT).show()
                }
            } finally {
                setOperationInProgress(false)
            }
        }
    }

    private fun resolveSigner(doc: com.example.anda.data.local.entity.DocumentEntity): String {
        doc.signedBy?.trim()?.takeIf { it.isNotBlank() }?.let { return it }

        val displayName = ProfileManager.getDisplayName(this).trim()
        if (displayName.isNotBlank()) return displayName

        return when (ProfileManager.getProfile(this)) {
            UserProfile.TECHNICIAN -> "Tecnico SST"
            UserProfile.PRESTADORA -> "Prestadora/Clinica"
            else -> "Responsavel Tecnico"
        }
    }

    private fun setOperationInProgress(inProgress: Boolean) {
        isOperationInProgress = inProgress
        binding.signDocumentButton.isEnabled = !inProgress
        binding.exportArtifactsButton.isEnabled = !inProgress
    }

    companion object {
        const val EXTRA_DOCUMENT_ID = "extra_document_id"
    }
}

