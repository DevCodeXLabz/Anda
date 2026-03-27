package com.example.anda.data.services

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Política operacional para o fluxo do ASO.
 * Assinatura via AndroidKeyStore RSA-SHA256 + biometria = Assinatura Eletrônica Avançada
 * conforme Lei 14.063/2020, Art. 4º, III. Válida para documentos SST do setor privado.
 */
object AsoOperationalPolicy {

    private val dtFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))

    const val UI_FLOW_DISCLAIMER =
        "Este documento gera Assinatura Eletrônica Avançada (Lei 14.063/2020) com chave " +
        "criptográfica RSA-SHA256 armazenada no AndroidKeyStore e protegida por biometria. " +
        "Válido para ASO, OS, APR e demais documentos SST do setor privado."

    const val INITIAL_STATUS = "Pronto para gerar. A assinatura digital tem validade jurídica (Lei 14.063/2020)."

    const val AUDIT_DISCLAIMER =
        "Trilha de auditoria com Assinatura Eletrônica Avançada — Lei 14.063/2020."

    const val PDF_EXPORT_HEADER =
        "DOCUMENTO COM ASSINATURA ELETRÔNICA AVANÇADA\n" +
        "Lei 14.063/2020, Art. 4º, III | ANDA — Plataforma SST\n" +
        "Algoritmo: SHA256withRSA | Chave: AndroidKeyStore (hardware-backed)\n"

    /** Alias preservado para compatibilidade com testes. */
    const val PDF_EXPORT_DISCLAIMER = PDF_EXPORT_HEADER

    const val HTML_FOOTER_DISCLAIMER =
        "Documento gerado com Assinatura Eletrônica Avançada conforme Lei 14.063/2020, Art. 4º, III. " +
        "Chave RSA-SHA256 vinculada ao dispositivo e protegida por biometria. " +
        "Responsabilidade técnica: médico do trabalho conforme NR-7."

    fun draftSavedStatus(documentId: String): String =
        "Rascunho salvo: $documentId. Assine para gerar assinatura com validade jurídica."

    fun signedStatus(signedAt: Long, signedBy: String): String {
        val dt = dtFmt.format(Date(signedAt))
        return "✅ Assinado por $signedBy em $dt\n" +
            "Assinatura Eletrônica Avançada — Lei 14.063/2020 | SHA256withRSA + AndroidKeyStore"
    }

    @Deprecated("Use signedStatus(signedAt, signedBy)")
    fun localSignatureStatus(signedAt: Long): String =
        signedStatus(signedAt, "Responsável Técnico")

    fun exportStatus(pdfPath: String, auditPath: String?): String {
        return if (auditPath.isNullOrBlank()) {
            "PDF exportado: $pdfPath"
        } else {
            "PDF: $pdfPath\nAuditoria: $auditPath"
        }
    }

    fun buildPdfBody(body: String): String =
        "$PDF_EXPORT_HEADER\n$body"
}
