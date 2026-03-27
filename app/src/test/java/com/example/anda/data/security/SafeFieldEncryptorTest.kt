package com.example.anda.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import javax.crypto.KeyGenerator

/**
 * JVM unit tests for [SafeFieldEncryptor].
 * Uses a JVM-generated AES key via [AesGcmFieldEncryptor] as delegate.
 */
class SafeFieldEncryptorTest {

    private lateinit var safe: SafeFieldEncryptor

    @Before
    fun setUp() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        safe = SafeFieldEncryptor(AesGcmFieldEncryptor(keySupplier = { key }))
    }

    // ── encryptNullable ───────────────────────────────────────────────────────

    @Test
    fun `encryptNullable returns null for null input`() {
        assertNull(safe.encryptNullable(null))
    }

    @Test
    fun `encryptNullable adds enc prefix to plaintext`() {
        val result = safe.encryptNullable("12345678901")!!
        assertTrue(result.startsWith(FieldEncryptor.CIPHER_PREFIX))
    }

    @Test
    fun `encryptNullable is idempotent for already-encrypted value`() {
        val first = safe.encryptNullable("CPF-value")!!
        val second = safe.encryptNullable(first)!!
        assertEquals(first, second)
    }

    // ── decryptNullable ───────────────────────────────────────────────────────

    @Test
    fun `decryptNullable returns null for null input`() {
        assertNull(safe.decryptNullable(null))
    }

    @Test
    fun `decryptNullable round-trip restores plaintext`() {
        val plain = "00.000.000/0001-99"
        val encrypted = safe.encryptNullable(plain)!!
        val recovered = safe.decryptNullable(encrypted)
        assertEquals(plain, recovered)
    }

    @Test
    fun `decryptNullable returns value as-is for legacy plaintext (migration)`() {
        val legacy = "legacy-plain-value"
        val result = safe.decryptNullable(legacy)
        assertEquals(legacy, result)
    }

    @Test
    fun `decryptNullable returns original ciphertext on decryption failure`() {
        val malformed = FieldEncryptor.CIPHER_PREFIX + "garbage-not-real-base64!!!"
        val result = safe.decryptNullable(malformed)
        assertEquals(malformed, result)
    }

    // ── encrypt (non-nullable) ────────────────────────────────────────────────

    @Test
    fun `encrypt adds enc prefix`() {
        val result = safe.encrypt("valor-sensitivo")
        assertTrue(result.startsWith(FieldEncryptor.CIPHER_PREFIX))
    }

    @Test
    fun `encrypt is idempotent`() {
        val once = safe.encrypt("dado")
        val twice = safe.encrypt(once)
        assertEquals(once, twice)
    }

    // ── decrypt (non-nullable) ────────────────────────────────────────────────

    @Test
    fun `decrypt round-trip restores plaintext`() {
        val plain = "12345678901234"
        assertEquals(plain, safe.decrypt(safe.encrypt(plain)))
    }

    @Test
    fun `decrypt returns value as-is for plaintext (migration)`() {
        assertEquals("old-plain", safe.decrypt("old-plain"))
    }

    @Test
    fun `decrypt returns original on failure without throwing`() {
        val malformed = FieldEncryptor.CIPHER_PREFIX + "!!!invalid"
        val result = safe.decrypt(malformed)
        assertEquals(malformed, result)
    }

    // ── cross-check nullable and non-nullable symmetry ────────────────────────

    @Test
    fun `nullable and non-nullable encrypt produce same-format output`() {
        val plain = "test-field"
        val a = safe.encryptNullable(plain)!!
        val b = safe.encrypt(plain)
        assertTrue(a.startsWith(FieldEncryptor.CIPHER_PREFIX))
        assertTrue(b.startsWith(FieldEncryptor.CIPHER_PREFIX))
        // Both should decrypt back to the same plaintext
        assertEquals(plain, safe.decryptNullable(a))
        assertEquals(plain, safe.decrypt(b))
    }
}

