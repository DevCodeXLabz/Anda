package com.example.anda.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * JVM unit tests for [AesGcmFieldEncryptor].
 * Uses a locally-generated AES key (no Android KeyStore required).
 */
class AesGcmFieldEncryptorTest {

    private lateinit var encryptor: AesGcmFieldEncryptor
    private lateinit var testKey: SecretKey

    @Before
    fun setUp() {
        testKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        encryptor = AesGcmFieldEncryptor(keySupplier = { testKey })
    }

    // ── encrypt ──────────────────────────────────────────────────────────────

    @Test
    fun `encrypt returns ciphertext with enc prefix`() {
        val result = encryptor.encrypt("12345678901")
        assertTrue(result.startsWith(FieldEncryptor.CIPHER_PREFIX))
    }

    @Test
    fun `encrypt returns different ciphertext on each call for same input`() {
        val a = encryptor.encrypt("same value")
        val b = encryptor.encrypt("same value")
        // GCM uses random IV, so two encryptions must differ
        assertNotEquals(a, b)
    }

    @Test
    fun `encrypt does not return plaintext in output`() {
        val plain = "sensitive-cpf-00000000000"
        val cipher = encryptor.encrypt(plain)
        assertFalse(cipher.contains(plain))
    }

    // ── decrypt ──────────────────────────────────────────────────────────────

    @Test
    fun `decrypt round-trip preserves plaintext`() {
        val plain = "12345678901234"
        val cipher = encryptor.encrypt(plain)
        val recovered = encryptor.decrypt(cipher)
        assertEquals(plain, recovered)
    }

    @Test
    fun `decrypt round-trip for empty string`() {
        val plain = ""
        val cipher = encryptor.encrypt(plain)
        val recovered = encryptor.decrypt(cipher)
        assertEquals(plain, recovered)
    }

    @Test
    fun `decrypt round-trip for unicode content`() {
        val plain = "Companhia Ação Ltda — CNPJ 00.000.000/0001-00"
        val cipher = encryptor.encrypt(plain)
        val recovered = encryptor.decrypt(cipher)
        assertEquals(plain, recovered)
    }

    @Test
    fun `decrypt round-trip for long payload`() {
        val plain = "A".repeat(4096)
        val cipher = encryptor.encrypt(plain)
        val recovered = encryptor.decrypt(cipher)
        assertEquals(plain, recovered)
    }

    @Test
    fun `decrypt throws EncryptionException for tampered ciphertext`() {
        val cipher = encryptor.encrypt("original")
        val tampered = cipher.dropLast(4) + "XXXX"
        try {
            encryptor.decrypt(tampered)
            fail("Expected EncryptionException")
        } catch (_: EncryptionException) {
            // expected
        }
    }

    @Test
    fun `decrypt throws IllegalArgumentException for value without prefix`() {
        try {
            encryptor.decrypt("not-encrypted-at-all")
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `decrypt throws EncryptionException for invalid base64 payload`() {
        val invalid = FieldEncryptor.CIPHER_PREFIX + "!!!invalid-base64!!!"
        try {
            encryptor.decrypt(invalid)
            fail("Expected EncryptionException")
        } catch (_: EncryptionException) {
            // expected
        }
    }

    @Test
    fun `decrypt fails with different key`() {
        val cipher = encryptor.encrypt("secret")
        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val other = AesGcmFieldEncryptor(keySupplier = { otherKey })
        try {
            other.decrypt(cipher)
            fail("Expected EncryptionException")
        } catch (_: EncryptionException) {
            // expected
        }
    }

    // ── isEncrypted ───────────────────────────────────────────────────────────

    @Test
    fun `isEncrypted returns true for ciphertext`() {
        val cipher = encryptor.encrypt("test")
        assertTrue(encryptor.isEncrypted(cipher))
    }

    @Test
    fun `isEncrypted returns false for plaintext`() {
        assertFalse(encryptor.isEncrypted("plaintext value"))
        assertFalse(encryptor.isEncrypted(""))
        assertFalse(encryptor.isEncrypted("12345678901234"))
    }
}

