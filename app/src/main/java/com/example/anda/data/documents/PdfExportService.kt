package com.example.anda.data.documents

import android.content.Context
import com.example.anda.core.security.LocalDataProtection
import com.example.anda.data.local.entity.DocumentEntity
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Service for exporting SST documents to PDF format.
 *
 * Features:
 * - Generates PDF from document entity
 * - Includes company and employee information
 * - Adds audit trail and metadata
 * - Prepares document for digital signature embedding
 * - Saves to device storage with audit logging
 *
 * Note: Digital signature embedding is handled by [DigitalSignatureManager].
 * This service generates the base PDF structure.
 */
class PdfExportService(private val context: Context) {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR"))

    /**
     * Export a document to PDF format.
     *
     * @param document The document entity to export
     * @param fileName Optional custom file name (defaults to document type + timestamp)
     * @return Path to generated PDF file, or null if export failed
     */
    suspend fun exportToPdf(document: DocumentEntity, fileName: String? = null): String? =
        withContext(Dispatchers.IO) {
            try {
                val pdfFileName = fileName ?: buildFileName(document)
                val pdfFile = File(context.getExternalFilesDir(null), "documents/$pdfFileName")
                
                // Ensure directory exists
                pdfFile.parentFile?.mkdirs()

                // Create PDF writer and document
                val writer = PdfWriter(pdfFile)
                val pdfDoc = PdfDocument(writer)
                val layoutDoc = Document(pdfDoc)

                // Build PDF content
                buildPdfContent(layoutDoc, document)

                // Close document (finalizes PDF)
                layoutDoc.close()
                pdfDoc.close()

                // Return path on success
                pdfFile.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    /**
     * Build the PDF content structure.
     */
    private fun buildPdfContent(document: Document, docEntity: DocumentEntity) {
        val now = dateFormat.format(Date())

        // Header
        addTitle(document, "ANDA - ${docEntity.documentType}")
        addSubtitle(document, "Documento de Segurança e Medicina do Trabalho")

        // Company Info Section
        addSection(document, "INFORMAÇÕES DO DOCUMENTO")
        addDocumentInfo(document, docEntity, now)

        // Document Content Section
        addSection(document, "CONTEÚDO DO DOCUMENTO")
        addDocumentContent(document, docEntity)

        // Audit Trail Section
        addSection(document, "REGISTRO DE AUDITORIA")
        addAuditTrail(document, docEntity, now)

        // Signature Block (placeholder for digital signature)
        addSignatureBlock(document, docEntity)
    }

    private fun addTitle(document: Document, title: String) {
        val titlePara = Paragraph(title)
            .setFontSize(18f)
            .setBold()
            .setTextAlignment(TextAlignment.CENTER)
        document.add(titlePara)
    }

    private fun addSubtitle(document: Document, subtitle: String) {
        val subtitlePara = Paragraph(subtitle)
            .setFontSize(11f)
            .setTextAlignment(TextAlignment.CENTER)
            .setItalic()
        document.add(subtitlePara)
        document.add(Paragraph(" "))  // Spacing
    }

    private fun addSection(document: Document, title: String) {
        val sectionPara = Paragraph(title)
            .setFontSize(12f)
            .setBold()
            .setMarginTop(12f)
        document.add(sectionPara)
        document.add(Paragraph("_".repeat(80)).setFontSize(9f))
    }

    private fun addDocumentInfo(document: Document, docEntity: DocumentEntity, now: String) {
        val docData = mapOf(
            "Tipo" to docEntity.documentType,
            "Título" to docEntity.title,
            "ID" to "DOC-${docEntity.id}",
            "CNPJ Empresa" to safeDecrypt(docEntity.companyCnpj),
            "Data Criação" to dateFormat.format(Date(docEntity.createdAt)),
            "Última Atualização" to dateFormat.format(Date(docEntity.updatedAt)),
            "Exportação PDF" to now
        )

        addInfoTable(document, docData)
    }

    private fun addDocumentContent(document: Document, docEntity: DocumentEntity) {
        // Decrypt payload if encrypted
        val payload = safeDecrypt(docEntity.payloadJson)

        if (payload.isNotEmpty()) {
            // Parse and display payload (format depends on document type)
            val contentPara = Paragraph(payload)
                .setFontSize(10f)
                .setMarginTop(6f)
            document.add(contentPara)
        } else {
            document.add(Paragraph("Nenhum conteúdo documentado.").setFontSize(10f))
        }
    }

    private fun addAuditTrail(document: Document, docEntity: DocumentEntity, now: String) {
        val auditData = mutableMapOf(
            "Documento Criado" to dateFormat.format(Date(docEntity.createdAt)),
            "Última Modificação" to dateFormat.format(Date(docEntity.updatedAt)),
            "Exportação PDF" to now,
            "Versão App" to "1.0"
        )

        // Add signature info if signed
        if (docEntity.signedAt != null && docEntity.signedAt!! > 0) {
            auditData["Data Assinatura"] = dateFormat.format(Date(docEntity.signedAt!!))
            auditData["Assinado Por"] = docEntity.signedBy ?: "Desconhecido"
            auditData["Algoritmo"] = docEntity.signatureAlgorithm ?: "SHA256withRSA"
        }

        addInfoTable(document, auditData)
    }

    private fun addSignatureBlock(document: Document, docEntity: DocumentEntity) {
        document.add(Paragraph(" ").setMarginTop(20f))
        document.add(Paragraph("_".repeat(80)).setFontSize(9f))

        val isSigned = docEntity.signedAt != null && docEntity.signedAt!! > 0
        val signaturePara = Paragraph(
            "Assinatura Digital: ${if (isSigned) "PRESENTE" else "NÃO ASSINADO"}"
        )
            .setFontSize(10f)
            .setBold()
            .setMarginTop(10f)
        document.add(signaturePara)

        if (isSigned && docEntity.signedAt != null && docEntity.signedAt!! > 0) {
            val signedDatePara = Paragraph(
                "Data de Assinatura: ${dateFormat.format(Date(docEntity.signedAt!!))}"
            )
                .setFontSize(9f)
                .setMarginTop(4f)
            document.add(signedDatePara)
        }

        val disclaimerPara = Paragraph(
            "Este documento foi gerado automaticamente pelo sistema ANDA.\n" +
            "A assinatura digital garante a integridade e autenticidade do documento."
        )
            .setFontSize(8f)
            .setItalic()
            .setMarginTop(10f)
        document.add(disclaimerPara)
    }

    private fun addInfoTable(document: Document, data: Map<String, String>) {
        val table = Table(2)
        table.setWidth(UnitValue.createPercentValue(100f))

        data.forEach { (key, value) ->
            table.addCell(Paragraph(key).setBold().setFontSize(9f))
            table.addCell(Paragraph(value).setFontSize(9f))
        }

        document.add(table)
        document.add(Paragraph(" ").setFontSize(6f))  // Small spacing
    }

    /**
     * Build a descriptive file name for the PDF.
     */
    private fun buildFileName(docEntity: DocumentEntity): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
        return "${docEntity.documentType}_${docEntity.id}_$timestamp.pdf"
    }

    /**
     * Safely decrypt fields, returning original value if decryption fails.
     */
    private fun safeDecrypt(encryptedValue: String?): String {
        return try {
            encryptedValue?.let { LocalDataProtection.decryptString(it) } ?: ""
        } catch (e: Exception) {
            encryptedValue ?: ""
        }
    }

    companion object {
        const val PDF_EXPORT_DIR = "documents"
    }
}

