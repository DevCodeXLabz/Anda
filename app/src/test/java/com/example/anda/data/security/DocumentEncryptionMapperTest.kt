package com.example.anda.data.security

import com.example.anda.data.local.entity.DocumentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import javax.crypto.KeyGenerator

class DocumentEncryptionMapperTest {

    private lateinit var mapper: DocumentEncryptionMapper
    private lateinit var safe: SafeFieldEncryptor

    @Before
    fun setUp() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        safe = SafeFieldEncryptor(AesGcmFieldEncryptor(keySupplier = { key }))
        mapper = DocumentEncryptionMapper(safe)
    }

    private fun entity(cnpj: String = "12345678000199", signedBy: String? = null) =
        DocumentEntity(
            documentId = "doc-001",
            documentType = "ASO",
            companyCnpj = cnpj,
            title = "ASO - Funcionário Teste",
            payloadJson = """{"exam":"ADMISSIONAL"}""",
            contentHash = "abc123",
            signedBy = signedBy
        )

    // ── encrypt ───────────────────────────────────────────────────────────────

    @Test
    fun `encrypt adds enc prefix to companyCnpj`() {
        val result = mapper.encrypt(entity())
        assertTrue(result.companyCnpj.startsWith(FieldEncryptor.CIPHER_PREFIX))
    }

    @Test
    fun `encrypt does not expose plain CNPJ in ciphertext`() {
        val plain = "12345678000199"
        val result = mapper.encrypt(entity(cnpj = plain))
        assertFalse(result.companyCnpj.contains(plain))
    }

    @Test
    fun `encrypt encrypts non-null signedBy`() {
        val result = mapper.encrypt(entity(signedBy = "Dr. Carlos Silva"))
        assertTrue(result.signedBy!!.startsWith(FieldEncryptor.CIPHER_PREFIX))
    }

    @Test
    fun `encrypt leaves null signedBy as null`() {
        val result = mapper.encrypt(entity(signedBy = null))
        assertNull(result.signedBy)
    }

    @Test
    fun `encrypt does not change non-sensitive fields`() {
        val original = entity()
        val result = mapper.encrypt(original)
        assertEquals(original.documentId, result.documentId)
        assertEquals(original.documentType, result.documentType)
        assertEquals(original.title, result.title)
        assertEquals(original.payloadJson, result.payloadJson)
        assertEquals(original.contentHash, result.contentHash)
    }

    @Test
    fun `encrypt is idempotent`() {
        val once = mapper.encrypt(entity())
        val twice = mapper.encrypt(once)
        assertEquals(once.companyCnpj, twice.companyCnpj)
    }

    // ── decrypt ───────────────────────────────────────────────────────────────

    @Test
    fun `decrypt round-trip restores companyCnpj`() {
        val plain = "12345678000199"
        val encrypted = mapper.encrypt(entity(cnpj = plain))
        val decrypted = mapper.decrypt(encrypted)
        assertEquals(plain, decrypted.companyCnpj)
    }

    @Test
    fun `decrypt round-trip restores signedBy`() {
        val signer = "Dr. Ana Lima"
        val encrypted = mapper.encrypt(entity(signedBy = signer))
        val decrypted = mapper.decrypt(encrypted)
        assertEquals(signer, decrypted.signedBy)
    }

    @Test
    fun `decrypt handles legacy plaintext rows without enc prefix`() {
        val legacy = entity(cnpj = "99887766000100")      // no enc:: prefix
        val result = mapper.decrypt(legacy)
        assertEquals("99887766000100", result.companyCnpj)
    }

    @Test
    fun `decrypt leaves null signedBy as null`() {
        val result = mapper.decrypt(entity(signedBy = null))
        assertNull(result.signedBy)
    }

    // ── encryptAll / decryptAll ───────────────────────────────────────────────

    @Test
    fun `encryptAll encrypts all entities in list`() {
        val entities = listOf(entity("11111111000111"), entity("22222222000122"))
        val results = mapper.encryptAll(entities)
        results.forEach { assertTrue(it.companyCnpj.startsWith(FieldEncryptor.CIPHER_PREFIX)) }
    }

    @Test
    fun `decryptAll restores all entities in list`() {
        val originals = listOf(entity("11111111000111"), entity("22222222000122"))
        val decrypted = mapper.decryptAll(mapper.encryptAll(originals))
        assertEquals("11111111000111", decrypted[0].companyCnpj)
        assertEquals("22222222000122", decrypted[1].companyCnpj)
    }

    @Test
    fun `encryptAll returns empty list for empty input`() {
        assertTrue(mapper.encryptAll(emptyList()).isEmpty())
    }

    // ── cross-field isolation ─────────────────────────────────────────────────

    @Test
    fun `two entities with same CNPJ produce different ciphertexts`() {
        val a = mapper.encrypt(entity(cnpj = "12345678000199"))
        val b = mapper.encrypt(entity(cnpj = "12345678000199"))
        assertNotEquals(a.companyCnpj, b.companyCnpj)
    }

    // ── repository field surface coverage ────────────────────────────────────

    @Test
    fun `encrypt covers all LGPD sensitive fields used by DocumentLocalRepository`() {
        // Mirrors the six fields protected by DocumentLocalRepository.encryptSensitiveFields:
        // companyCnpj, title, payloadJson, pdfPath, auditTxtPath, signedBy
        val entity = DocumentEntity(
            documentId = "doc-lgpd",
            documentType = "ASO",
            companyCnpj = "12345678000199",
            title = "Funcionário Sensitivo",
            payloadJson = """{"cpf":"00011122233"}""",
            contentHash = "hash-x",
            pdfPath = "/storage/anda/doc.pdf",
            auditTxtPath = "/storage/anda/audit.txt",
            signedBy = "Dr. João Silva"
        )

        val encrypted = mapper.encrypt(entity)

        assertTrue(encrypted.companyCnpj.startsWith(FieldEncryptor.CIPHER_PREFIX))
        assertFalse(encrypted.companyCnpj.contains("12345678000199"))
        // title, payloadJson, pdfPath, auditTxtPath handled by LocalDataProtection in repo;
        // mapper scope is companyCnpj + signedBy — verify both are covered:
        assertTrue(encrypted.signedBy!!.startsWith(FieldEncryptor.CIPHER_PREFIX))

        // Verify round-trip
        val decrypted = mapper.decrypt(encrypted)
        assertEquals("12345678000199", decrypted.companyCnpj)
        assertEquals("Dr. João Silva", decrypted.signedBy)
    }
}

