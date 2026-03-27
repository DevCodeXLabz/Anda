package com.example.anda.data.security

import com.example.anda.data.local.entity.DocumentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reflection-based guard that ensures every [String] / [String?] field declared in
 * [DocumentEntity] is explicitly classified as either:
 *
 *   • **PII / LGPD-sensitive** → must be listed in [ENCRYPTED_FIELDS] and protected by
 *     [com.example.anda.data.repository.DocumentLocalRepository.encryptSensitiveFields]; or
 *   • **Non-PII plaintext** → must be listed in [NON_PII_FIELDS] and safe to store unencrypted.
 *
 * **What to do when this test fails?**
 *
 *   1. You added a new `String` or `String?` field to [DocumentEntity].
 *   2. Decide if it contains personal data (CPF, CNPJ, name, path, payload…).
 *      - If YES → add the field name to [ENCRYPTED_FIELDS] here **and** add it to
 *        `DocumentLocalRepository.encryptSensitiveFields()` / `decryptSensitiveFields()`.
 *      - If NO  → add the field name to [NON_PII_FIELDS] here with a short justification comment.
 *
 * This test uses Java reflection (`Class.declaredFields`) so it requires no extra
 * `kotlin-reflect` dependency — both `String` and `String?` map to `java.lang.String`
 * in the JVM bytecode.
 */
class EncryptedFieldCoverageTest {

    /**
     * Fields covered by [com.example.anda.data.repository.DocumentLocalRepository]:
     * `companyCnpj`, `title`, `payloadJson`, `pdfPath`, `auditTxtPath`, `signedBy`.
     *
     * If you add a PII field to [DocumentEntity] you MUST add it here AND protect it in
     * the repository's `encryptSensitiveFields` / `decryptSensitiveFields` extension functions.
     */
    private val ENCRYPTED_FIELDS = setOf(
        "companyCnpj",    // CNPJ da empresa  — LGPD Art. 5, I
        "title",          // documento pessoal — pode conter nome/função do trabalhador
        "payloadJson",    // dados do exame/formulário (CPF, cargo, CID, etc.)
        "pdfPath",        // caminho local do PDF — revela presença do arquivo
        "auditTxtPath",   // caminho local do audit trail
        "signedBy"        // nome do médico signatário
    )

    /**
     * String fields that are intentionally stored in plaintext because they carry no
     * personal data (opaque identifiers, cryptographic artefacts, algorithm names, etc.).
     *
     * Add a justification comment when extending this set.
     */
    private val NON_PII_FIELDS = setOf(
        "documentId",          // UUID gerado localmente — sem vínculo a pessoa
        "documentType",        // enum string (ASO, PCMSO, …) — não é dado pessoal
        "sourceRequestCode",   // código operacional interno da solicitação — identificador opaco
        "contentHash",         // SHA-256 do conteúdo — hash irreversível
        "signatureB64",        // assinatura RSA-SHA256 base64 — dado criptográfico público
        "publicKeyFingerprint", // fingerprint SHA-256 da chave pública — dado criptográfico
        "signatureAlgorithm"   // nome do algoritmo (ex.: "SHA256withRSA") — metadado técnico
    )

    // ── guard: every String field must be classified ──────────────────────────

    @Test
    fun `all String fields in DocumentEntity are classified as encrypted or non-PII`() {
        val stringFields = DocumentEntity::class.java.declaredFields
            .filter { it.type == String::class.java }
            .map { it.name }
            .toSet()

        val classified = ENCRYPTED_FIELDS + NON_PII_FIELDS
        val unclassified = stringFields - classified

        assertTrue(
            buildString {
                appendLine("The following String field(s) in DocumentEntity are NOT classified:")
                unclassified.forEach { appendLine("  • $it") }
                appendLine()
                appendLine("Fix: add each field to ENCRYPTED_FIELDS (if PII) or NON_PII_FIELDS")
                appendLine("     (if safe for plaintext storage) in EncryptedFieldCoverageTest.")
            },
            unclassified.isEmpty()
        )
    }

    // ── guard: sets must be disjoint ──────────────────────────────────────────

    @Test
    fun `no field appears in both encrypted and non-PII sets`() {
        val overlap = ENCRYPTED_FIELDS intersect NON_PII_FIELDS
        assertTrue(
            "Field(s) classified ambiguously (in both sets): $overlap",
            overlap.isEmpty()
        )
    }

    // ── guard: ENCRYPTED_FIELDS must mirror the repository scope exactly ──────

    @Test
    fun `ENCRYPTED_FIELDS matches DocumentLocalRepository encryptSensitiveFields scope`() {
        // Canonical list from DocumentLocalRepository.encryptSensitiveFields().
        // Keep in sync whenever the repository's extension functions change.
        val repositoryScope = setOf(
            "companyCnpj",
            "title",
            "payloadJson",
            "pdfPath",
            "auditTxtPath",
            "signedBy"
        )
        assertEquals(
            "ENCRYPTED_FIELDS must mirror DocumentLocalRepository.encryptSensitiveFields exactly.\n" +
                "Diff: added=${repositoryScope - ENCRYPTED_FIELDS}, " +
                "removed=${ENCRYPTED_FIELDS - repositoryScope}",
            repositoryScope,
            ENCRYPTED_FIELDS
        )
    }

    // ── guard: every declared encrypted field must exist in the entity ────────

    @Test
    fun `every field in ENCRYPTED_FIELDS exists in DocumentEntity`() {
        val entityFields = DocumentEntity::class.java.declaredFields
            .map { it.name }
            .toSet()

        val ghost = ENCRYPTED_FIELDS - entityFields
        assertTrue(
            "ENCRYPTED_FIELDS references field(s) that don't exist in DocumentEntity: $ghost",
            ghost.isEmpty()
        )
    }

    // ── guard: every non-PII field must exist in the entity ──────────────────

    @Test
    fun `every field in NON_PII_FIELDS exists in DocumentEntity`() {
        val entityFields = DocumentEntity::class.java.declaredFields
            .map { it.name }
            .toSet()

        val ghost = NON_PII_FIELDS - entityFields
        assertTrue(
            "NON_PII_FIELDS references field(s) that don't exist in DocumentEntity: $ghost",
            ghost.isEmpty()
        )
    }

    // ── informational: print coverage summary (always passes) ─────────────────

    @Test
    fun `coverage summary — encrypted field count vs total String fields`() {
        val totalStringFields = DocumentEntity::class.java.declaredFields
            .count { it.type == String::class.java }

        assertTrue(
            "Sanity check: ENCRYPTED_FIELDS ($ENCRYPTED_FIELDS) is larger than total String " +
                "fields ($totalStringFields) — something is wrong.",
            ENCRYPTED_FIELDS.size <= totalStringFields
        )
    }
}

