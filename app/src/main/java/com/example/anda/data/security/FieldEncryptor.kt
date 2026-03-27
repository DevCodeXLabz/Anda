package com.example.anda.data.security

/**
 * Contract for field-level encryption of sensitive data (CPF, CNPJ, document content).
 *
 * Implementations must be deterministic only within a session; callers must not
 * compare ciphertexts across encrypt calls.
 *
 * Ciphertext format: `enc::<base64-payload>`
 * Plaintext is returned as-is (no prefix).
 */
interface FieldEncryptor {

    /**
     * Encrypts [plaintext] and returns a ciphertext string prefixed with [CIPHER_PREFIX].
     * Throws [EncryptionException] on failure.
     */
    fun encrypt(plaintext: String): String

    /**
     * Decrypts [ciphertext] (must start with [CIPHER_PREFIX]) and returns the original plaintext.
     * Throws [EncryptionException] on failure.
     */
    fun decrypt(ciphertext: String): String

    /**
     * Returns true when [value] is already an encrypted ciphertext (starts with [CIPHER_PREFIX]).
     */
    fun isEncrypted(value: String): Boolean = value.startsWith(CIPHER_PREFIX)

    companion object {
        const val CIPHER_PREFIX = "enc::"
    }
}

/** Thrown by [FieldEncryptor] implementations when encrypt or decrypt fails. */
class EncryptionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

