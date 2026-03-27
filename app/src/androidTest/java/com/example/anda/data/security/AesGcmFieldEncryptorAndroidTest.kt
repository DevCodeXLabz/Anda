package com.example.anda.data.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [AesGcmFieldEncryptor] using the Android device's
 * native KeyStore (AndroidKeyStore).
 *
 * These tests require a device or emulator and cannot run in the JVM.
 * They verify that:
 *  - AES-256-GCM encryption/decryption round-trips correctly with Android KeyStore.
 *  - Ciphertext format (`enc::<base64>`) is preserved.
 *  - Different plaintexts produce different ciphertexts (due to random IV).
 *  - Invalid ciphertexts are rejected.
 *  - Empty and whitespace strings are handled.
 *
 * Run with:
 *   ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunner="androidx.test.runner.AndroidJUnitRunner"
 *
 * or target this test directly:
 *   ./gradlew :app:connectedAndroidTest --tests "*AesGcmFieldEncryptorAndroidTest*"
 */
@RunWith(AndroidJUnit4::class)
class AesGcmFieldEncryptorAndroidTest {

    private lateinit var encryptor: AesGcmFieldEncryptor
    private val testKeyAlias = "test_aes_gcm_key_${System.currentTimeMillis()}"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Initialize LocalDataProtection to set up the AndroidKeyStore
        com.example.anda.core.security.LocalDataProtection.initialize(context)
        // Create encryptor with a unique test alias
        encryptor = AesGcmFieldEncryptor(keyAlias = testKeyAlias)
    }

    // ── Encrypt/decrypt round-trip ──────────────────────────────────────────

    @Test
    fun encryptDecrypt_roundTrip_simple() {
        val plaintext = "Hello, World!"
        val ciphertext = encryptor.encrypt(plaintext)
        val decrypted = encryptor.decrypt(ciphertext)
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun encryptDecrypt_roundTrip_withSpecialChars() {
        val plaintext = "Médico: Dr. João Silva\nCPF: 123.456.789-00"
        val ciphertext = encryptor.encrypt(plaintext)
        val decrypted = encryptor.decrypt(ciphertext)
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun encryptDecrypt_roundTrip_emptyString() {
        val plaintext = ""
        val ciphertext = encryptor.encrypt(plaintext)
        val decrypted = encryptor.decrypt(ciphertext)
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun encryptDecrypt_roundTrip_longPayload() {
        val plaintext = "x".repeat(10_000)  // 10 KB string
        val ciphertext = encryptor.encrypt(plaintext)
        val decrypted = encryptor.decrypt(ciphertext)
        assertEquals(plaintext, decrypted)
    }

    // ── Ciphertext format ──────────────────────────────────────────────────

    @Test
    fun ciphertext_hasCorrectPrefix() {
        val ciphertext = encryptor.encrypt("test")
        assertTrue(
            "Ciphertext must start with 'enc::'",
            ciphertext.startsWith(FieldEncryptor.CIPHER_PREFIX)
        )
    }

    @Test
    fun isEncrypted_returnsTrue_forEncryptedText() {
        val ciphertext = encryptor.encrypt("test")
        assertTrue("isEncrypted must return true for 'enc::' prefixed text", encryptor.isEncrypted(ciphertext))
    }

    @Test
    fun isEncrypted_returnsFalse_forPlaintext() {
        val plaintext = "plaintext"
        assertFalse("isEncrypted must return false for text without prefix", encryptor.isEncrypted(plaintext))
    }

    // ── Randomness (IV) ────────────────────────────────────────────────────

    @Test
    fun samePayload_producesDifferentCiphertexts() {
        // GCM uses a random 12-byte IV; same plaintext encrypted twice must
        // produce different ciphertexts due to the different IV.
        val plaintext = "fixed plaintext"
        val ct1 = encryptor.encrypt(plaintext)
        val ct2 = encryptor.encrypt(plaintext)

        assertNotEquals(
            "Different encryptions of the same plaintext must produce different ciphertexts " +
                "(due to random IV)",
            ct1,
            ct2
        )

        // But decrypting both must yield the same plaintext
        assertEquals(plaintext, encryptor.decrypt(ct1))
        assertEquals(plaintext, encryptor.decrypt(ct2))
    }

    @Test
    fun sameAlias_instancesCanDecryptEachOther() {
        val plaintext = "shared keystore alias"
        val first = AesGcmFieldEncryptor(keyAlias = testKeyAlias)
        val second = AesGcmFieldEncryptor(keyAlias = testKeyAlias)

        val ciphertext = first.encrypt(plaintext)
        assertEquals(plaintext, second.decrypt(ciphertext))
    }

    @Test
    fun differentAlias_decryptThrowsEncryptionException() {
        val first = AesGcmFieldEncryptor(keyAlias = "${testKeyAlias}_a")
        val second = AesGcmFieldEncryptor(keyAlias = "${testKeyAlias}_b")
        val ciphertext = first.encrypt("alias scoped secret")

        try {
            second.decrypt(ciphertext)
            fail("Decrypting with a different alias must throw")
        } catch (_: EncryptionException) {
            // Expected: each alias maps to a different AndroidKeyStore key.
        }
    }

    // ── Error handling ────────────────────────────────────────────────────

    @Test
    fun decrypt_throwsException_forTamperedCiphertext() {
        val plaintext = "sensitive data"
        val ciphertext = encryptor.encrypt(plaintext)

        // Tamper deterministically by changing the last Base64 char.
        val payload = ciphertext.removePrefix(FieldEncryptor.CIPHER_PREFIX)
        val last = payload.last()
        val replacement = if (last != 'A') 'A' else 'B'
        val tampered = FieldEncryptor.CIPHER_PREFIX + payload.dropLast(1) + replacement

        try {
            encryptor.decrypt(tampered)
            fail("Decrypting tampered ciphertext must throw")
        } catch (e: EncryptionException) {
            assertTrue("Exception message should mention decryption", e.message?.lowercase()?.contains("decrypt") ?: false)
        } catch (_: IllegalArgumentException) {
            // Some providers may surface malformed payloads as IllegalArgumentException.
        }
    }

    @Test
    fun decrypt_throwsException_forMissingPrefix() {
        val notEncrypted = "no_prefix_here"
        try {
            encryptor.decrypt(notEncrypted)
            fail("Decrypting non-encrypted text must throw IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun decrypt_throwsException_forInvalidBase64() {
        val invalidCiphertext = FieldEncryptor.CIPHER_PREFIX + "!!!invalid-base64!!!"
        try {
            encryptor.decrypt(invalidCiphertext)
            fail("Decrypting invalid base64 must throw EncryptionException")
        } catch (_: EncryptionException) {
            // Expected
        }
    }

    @Test
    fun decrypt_throwsException_forInvalidBase64Character() {
        val invalidCiphertext = FieldEncryptor.CIPHER_PREFIX + "!!!"
        try {
            encryptor.decrypt(invalidCiphertext)
            fail("Decrypting malformed payload must throw EncryptionException")
        } catch (_: EncryptionException) {
            // Expected
        }
    }

    // ── Idempotency ────────────────────────────────────────────────────

    @Test
    fun encrypt_idempotent_alreadyEncrypted() {
        val plaintext = "original"
        val encrypted = encryptor.encrypt(plaintext)
        // Encrypting an already-encrypted value should NOT double-encrypt; 
        // callers are responsible for checking isEncrypted() if they want idempotency.
        // This test verifies we don't have accidental idempotency.
        val reencrypted = encryptor.encrypt(encrypted)
        assertNotEquals(
            "Encrypting an encrypted value produces a different ciphertext " +
                "(not idempotent, as expected)",
            encrypted,
            reencrypted
        )
    }

    // ── Unicode and encoding ────────────────────────────────────────────

    @Test
    fun encryptDecrypt_roundTrip_emoji() {
        val plaintext = "😊 Expressão de satisfação 🎉"
        val ciphertext = encryptor.encrypt(plaintext)
        val decrypted = encryptor.decrypt(ciphertext)
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun encryptDecrypt_roundTrip_chineseCharacters() {
        val plaintext = "中文字符 和 日本語"
        val ciphertext = encryptor.encrypt(plaintext)
        val decrypted = encryptor.decrypt(ciphertext)
        assertEquals(plaintext, decrypted)
    }
}


