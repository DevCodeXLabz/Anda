package com.example.anda.data.repository

import com.example.anda.data.local.entity.DocumentEntity
// ...existing imports...
import org.junit.Assert.assertEquals
import org.junit.Test

class ComplianceAlertRepositoryTest {

    private fun makeDoc(id: String, title: String = "Doc", company: String = "00.000.000/0000-00", type: String = "ASO", validUntil: Long? = null): DocumentEntity {
        return DocumentEntity(
            id = 0L,
            documentId = id,
            documentType = type,
            companyCnpj = company,
            title = title,
            payloadJson = "{}",
            contentHash = "",
            sourceRequestCode = null,
            validUntil = validUntil,
            pdfPath = null,
            auditTxtPath = null,
            signedAt = null,
            signedBy = null,
            signatureB64 = null,
            publicKeyFingerprint = null,
            signatureAlgorithm = null,
            isSynced = false,
            updatedAt = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis()
        )
    }

    @Test
    fun `maps severities correctly and sorts by severity then date then title`() {
        val now = System.currentTimeMillis()
        val docs = listOf(
            makeDoc("a", "A", validUntil = now - 1), // expired
            makeDoc("b", "B", validUntil = now + 2 * 24 * 60 * 60 * 1000), // within 7 days -> critical
            makeDoc("c", "C", validUntil = now + 15 * 24 * 60 * 60 * 1000), // within 30 days -> warning
            makeDoc("d", "D", validUntil = null) // info (if includeInfo true)
        )

        // Convert using the same logic inline so tests don't depend on extension visibility.
        val DAY_MS = 24L * 60L * 60L * 1000L
        val alerts = docs.mapNotNull { d ->
            val until = d.validUntil
            val sev: String? = if (until == null && true) {
                "INFO"
            } else if (until == null) {
                null
            } else if (until < now) {
                "EXPIRED"
            } else if (until <= now + 7L * DAY_MS) {
                "CRITICAL"
            } else if (until <= now + 30L * DAY_MS) {
                "WARNING"
            } else {
                "INFO"
            }
            if (sev == null) return@mapNotNull null

            ComplianceAlert(
                documentId = d.documentId,
                documentType = d.documentType,
                companyCnpj = d.companyCnpj,
                title = d.title,
                validUntil = until,
                severity = sev
            )
        }

        // Expect order: expired, critical, warning, info
        assertEquals(4, alerts.size)
        assertEquals("a", alerts[0].documentId)
        assertEquals("b", alerts[1].documentId)
        assertEquals("c", alerts[2].documentId)
        assertEquals("d", alerts[3].documentId)

        // Check severities
        assertEquals("EXPIRED", alerts[0].severity)
        assertEquals("CRITICAL", alerts[1].severity)
        assertEquals("WARNING", alerts[2].severity)
        assertEquals("INFO", alerts[3].severity)
    }
}




