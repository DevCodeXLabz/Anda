package com.example.anda.data.services

import android.content.Context
import com.example.anda.data.local.entity.DocumentEntity
import com.example.anda.data.local.entity.SyncAttemptLogEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AuditExportService {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.forLanguageTag("pt-BR"))

    fun exportAuditTxt(
        context: Context,
        document: DocumentEntity,
        attempts: List<SyncAttemptLogEntity>,
        fileName: String
    ): File {
        val outDir = File(context.filesDir, "exports")
        if (!outDir.exists()) outDir.mkdirs()

        val outFile = File(outDir, fileName)
        val content = buildString {
            appendLine("ANDA - RELATORIO DE AUDITORIA")
            appendLine("Gerado em: ${dateFormat.format(Date())}")
            appendLine(AsoOperationalPolicy.AUDIT_DISCLAIMER)
            appendLine()
            appendLine("Documento:")
            appendLine("- ID: ${document.documentId}")
            appendLine("- Tipo: ${document.documentType}")
            appendLine("- Empresa: ${document.companyCnpj}")
            appendLine("- Titulo: ${document.title}")
            appendLine("- Hash conteudo: ${document.contentHash}")
            appendLine("- Assinado em: ${document.signedAt?.let { dateFormat.format(Date(it)) } ?: "N/A"}")
            appendLine("- Assinado por: ${document.signedBy ?: "N/A"}")
            appendLine("- Sincronizado: ${if (document.isSynced) "SIM" else "NAO"}")
            appendLine("- Atualizado em: ${dateFormat.format(Date(document.updatedAt))}")
            appendLine()
            appendLine("Tentativas de sincronizacao:")
            if (attempts.isEmpty()) {
                appendLine("- Sem historico")
            } else {
                attempts.forEach { a ->
                    appendLine("- ${dateFormat.format(Date(a.createdAt))} | ${a.status} | tentativa ${a.attemptNumber} | ${a.message}")
                }
            }
        }

        outFile.writeText(content)
        return outFile
    }
}

