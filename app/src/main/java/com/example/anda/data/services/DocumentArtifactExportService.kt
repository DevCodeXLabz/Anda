package com.example.anda.data.services

import android.content.Context
import com.example.anda.data.repository.DocumentLocalRepository
import java.io.File

class DocumentArtifactExportService(
    private val pdfExportService: PdfExportService = PdfExportService(),
    private val auditExportService: AuditExportService = AuditExportService()
) {

    data class ExportResult(
        val pdfFile: File,
        val auditFile: File?
    )

    suspend fun exportPlainTextPdfAndAudit(
        context: Context,
        documentId: String?,
        title: String,
        body: String,
        filePrefix: String
    ): ExportResult {
        val normalizedDocumentId = documentId?.trim().orEmpty().ifBlank { null }
        val existingDocument = normalizedDocumentId?.let { DocumentLocalRepository.findByDocumentId(it) }
        val now = System.currentTimeMillis()

        val pdfFile = pdfExportService.exportPlainTextPdf(
            context = context,
            title = title,
            body = body,
            fileName = "$filePrefix-$now.pdf",
            signedBy = existingDocument?.signedBy,
            signedAt = existingDocument?.signedAt,
            contentHash = existingDocument?.contentHash,
            signatureB64 = existingDocument?.signatureB64,
            publicKeyFingerprint = existingDocument?.publicKeyFingerprint
        )

        var auditFile: File? = null
        if (normalizedDocumentId != null) {
            DocumentLocalRepository.markPdfExported(normalizedDocumentId, pdfFile.absolutePath)
            val refreshedDocument = DocumentLocalRepository.findByDocumentId(normalizedDocumentId)
            if (refreshedDocument != null) {
                val attempts = DocumentLocalRepository.listSyncAttempts(normalizedDocumentId)
                auditFile = auditExportService.exportAuditTxt(
                    context = context,
                    document = refreshedDocument,
                    attempts = attempts,
                    fileName = "$filePrefix-audit-$now.txt"
                )
                DocumentLocalRepository.markAuditExported(normalizedDocumentId, auditFile.absolutePath)
            }
        }

        return ExportResult(
            pdfFile = pdfFile,
            auditFile = auditFile
        )
    }
}

