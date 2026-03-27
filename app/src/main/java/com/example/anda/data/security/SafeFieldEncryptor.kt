package com.example.anda.data.security

/**
 * Repository-layer wrapper over [FieldEncryptor] that adds:
 * - **Null safety**: nullable fields pass through as null.
 * - **Idempotency**: already-encrypted values are not re-encrypted.
 * - **Transparent migration**: on decrypt, values without the `enc::` prefix are
 *   returned as-is so legacy plaintext rows keep working after a schema migration.
 * - **Graceful error handling**: encrypt failures propagate [EncryptionException];
 *   decrypt failures fall back to returning the original ciphertext and log a warning.
 *
 * All methods are intentionally non-suspending; they are designed to run inline
 * inside Room TypeConverters or repository mapping functions.
 */
class SafeFieldEncryptor(private val delegate: FieldEncryptor) {

    /**
     * Encrypts [value] if not already encrypted.
     * Returns null when [value] is null.
     * Throws [EncryptionException] on failure.
     */
    fun encryptNullable(value: String?): String? {
        value ?: return null
        if (delegate.isEncrypted(value)) return value
        return delegate.encrypt(value)
    }

    /**
     * Decrypts [value] if it carries the `enc::` prefix; otherwise returns [value] as-is
     * (transparent migration for pre-encryption rows).
     * Returns null when [value] is null.
     * Never throws — on decrypt failure returns the original [value] unchanged.
     */
    fun decryptNullable(value: String?): String? {
        value ?: return null
        if (!delegate.isEncrypted(value)) return value
        return runCatching { delegate.decrypt(value) }.getOrDefault(value)
    }

    /**
     * Encrypts non-nullable [value] if not already encrypted.
     * Throws [EncryptionException] on failure.
     */
    fun encrypt(value: String): String {
        if (delegate.isEncrypted(value)) return value
        return delegate.encrypt(value)
    }

    /**
     * Decrypts non-nullable [value] if it carries the `enc::` prefix; otherwise returns as-is.
     * Never throws — on decrypt failure returns the original [value].
     */
    fun decrypt(value: String): String {
        if (!delegate.isEncrypted(value)) return value
        return runCatching { delegate.decrypt(value) }.getOrDefault(value)
    }
}

