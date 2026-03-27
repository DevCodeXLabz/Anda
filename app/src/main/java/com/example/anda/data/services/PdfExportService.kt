package com.example.anda.data.services

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PdfExportService {

    private val dtFmt = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.forLanguageTag("pt-BR"))

    fun exportPlainTextPdf(
        context: Context,
        title: String,
        body: String,
        fileName: String,
        signedBy: String? = null,
        signedAt: Long? = null,
        contentHash: String? = null,
        signatureB64: String? = null,
        publicKeyFingerprint: String? = null
    ): File {
        val document = PdfDocument()
        val titlePaint = Paint().apply { textSize = 18f; isFakeBoldText = true }
        val bodyPaint = Paint().apply { textSize = 12f }
        val footerPaint = Paint().apply { textSize = 10f }
        val sigPaint = Paint().apply { textSize = 10f; isFakeBoldText = true }
        val sigValuePaint = Paint().apply { textSize = 9f }

        // Build the full content
        val normalizedBody = PdfExportTextFormatter.normalizeForPdf(body)
        val signatureBlock = buildSignatureBlock(signedBy, signedAt, contentHash, signatureB64, publicKeyFingerprint)
        val fullBody = listOf(normalizedBody, signatureBlock)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")

        val wrapped = PdfExportTextFormatter.wrapForPdf(fullBody, maxChars = 105)
        var pageNumber = 1
        var lineIndex = 0

        while (lineIndex < wrapped.size || pageNumber == 1) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            var y = 40f
            if (pageNumber == 1) {
                canvas.drawText(title, 24f, y, titlePaint)
                y += 28f
            }

            while (lineIndex < wrapped.size && y < PAGE_HEIGHT - 45f) {
                val line = wrapped[lineIndex]
                val paint = when {
                    line.startsWith("═") || line.startsWith("─") -> sigPaint
                    line.startsWith("  ") -> sigValuePaint
                    else -> bodyPaint
                }
                canvas.drawText(line, 24f, y, paint)
                y += 16f
                lineIndex++
            }

            canvas.drawText("Pagina $pageNumber", 24f, PAGE_HEIGHT - 20f, footerPaint)
            document.finishPage(page)
            pageNumber++
        }

        val outDir = File(context.filesDir, "exports")
        if (!outDir.exists()) outDir.mkdirs()
        val outFile = File(outDir, fileName)

        FileOutputStream(outFile).use { output ->
            document.writeTo(output)
        }
        document.close()

        return outFile
    }

    private fun buildSignatureBlock(
        signedBy: String?,
        signedAt: Long?,
        contentHash: String?,
        signatureB64: String?,
        publicKeyFingerprint: String?
    ): String {
        if (signedBy.isNullOrBlank() || signedAt == null) return ""

        val sb = StringBuilder()
        sb.appendLine("═".repeat(80))
        sb.appendLine("ASSINATURA ELETRÔNICA AVANÇADA — Lei 14.063/2020, Art. 4º, III")
        sb.appendLine("─".repeat(80))
        sb.appendLine("  Signatário : $signedBy")
        sb.appendLine("  Data/Hora  : ${dtFmt.format(Date(signedAt))}")
        sb.appendLine("  Algoritmo  : SHA256withRSA | AndroidKeyStore (hardware-backed)")
        if (!contentHash.isNullOrBlank()) {
            sb.appendLine("  Hash (SHA-256): $contentHash")
        }
        if (!publicKeyFingerprint.isNullOrBlank()) {
            sb.appendLine("  Chave pública (fingerprint): $publicKeyFingerprint")
        }
        if (!signatureB64.isNullOrBlank()) {
            // Show first 64 chars of signature for verification reference
            val sigPreview = if (signatureB64.length > 64) "${signatureB64.take(64)}..." else signatureB64
            sb.appendLine("  Assinatura (Base64, trecho): $sigPreview")
        }
        sb.appendLine("═".repeat(80))
        sb.appendLine("Documento válido conforme Lei 14.063/2020. ANDA — Plataforma SST.")
        return sb.toString()
    }

    companion object {
        private const val PAGE_WIDTH = 595
        private const val PAGE_HEIGHT = 842
    }

}
