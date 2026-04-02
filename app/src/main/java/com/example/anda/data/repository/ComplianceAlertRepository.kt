package com.example.anda.data.repository

import com.example.anda.data.local.entity.DocumentEntity

data class ComplianceAlert(
    val documentId: String,
    val documentType: String,
    val companyCnpj: String,
    val title: String,
    val validUntil: Long?,
    val severity: String
)

object ComplianceAlertRepository {

    private const val DAY_MS = 24L * 60L * 60L * 1000L

    suspend fun loadActiveAlerts(includeInfo: Boolean = false): List<ComplianceAlert> {
        val now = System.currentTimeMillis()
        val docs = DocumentLocalRepository.listDocuments()
        return docs
            .mapNotNull { d -> d.toComplianceAlert(now, includeInfo) }
            .sortedWith(
                compareBy<ComplianceAlert> { severityRank(it.severity) }
                    .thenBy { it.validUntil ?: Long.MAX_VALUE }
                    .thenBy { it.title }
            )
    }

    // ...existing code...


// Top-level mapping extension used by the repository and tests
internal fun com.example.anda.data.local.entity.DocumentEntity.toComplianceAlert(now: Long, includeInfo: Boolean): ComplianceAlert? {
    val until = this.validUntil
    val sev = when {
        until == null && includeInfo -> "INFO"
        until == null -> null
        until < now -> "EXPIRED"
        until <= now + 7L * ComplianceAlertRepository.DAY_MS -> "CRITICAL"
        until <= now + 30L * ComplianceAlertRepository.DAY_MS -> "WARNING"
        includeInfo -> "INFO"
        else -> null
    } ?: return null

    return ComplianceAlert(
        documentId = this.documentId,
        documentType = this.documentType,
        companyCnpj = this.companyCnpj,
        title = this.title,
        validUntil = until,
        severity = sev
    )
}
    private fun severityRank(severity: String): Int {
        return when (severity) {
            "EXPIRED" -> 0
            "CRITICAL" -> 1
            "WARNING" -> 2
            else -> 3
        }
    }
}


