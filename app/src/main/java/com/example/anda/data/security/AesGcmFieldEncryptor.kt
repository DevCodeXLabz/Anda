package com.example.anda.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM field encryptor backed by Android KeyStore.
 *
 * Ciphertext format: `enc::<base64(12-byte IV || ciphertext || 16-byte GCM tag)>`
 *
 * @param keyAlias  Alias used to store/retrieve the AES key in the KeyStore.
 * @param keySupplier  Injectable key supplier for testability; defaults to Android KeyStore.
 */
class AesGcmFieldEncryptor(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
    private val keySupplier: () -> SecretKey = { getOrCreateKeyStoreKey(keyAlias) }
) : FieldEncryptor {

    override fun encrypt(plaintext: String): String {
        return try {
            val key = keySupplier()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv                               // 12 bytes (GCM default)
            val cipherBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val payload = iv + cipherBytes                   // prepend IV for decrypt
            FieldEncryptor.CIPHER_PREFIX + Base64.getEncoder().encodeToString(payload)
        } catch (e: Exception) {
            throw EncryptionException("Encryption failed for field", e)
        }
    }

    override fun decrypt(ciphertext: String): String {
        require(isEncrypted(ciphertext)) {
            "Value is not encrypted (missing '${FieldEncryptor.CIPHER_PREFIX}' prefix)"
        }
        return try {
            val payload = Base64.getDecoder().decode(
                ciphertext.removePrefix(FieldEncryptor.CIPHER_PREFIX)
            )
            if (payload.size < MIN_PAYLOAD_LENGTH) {
                throw EncryptionException("Decryption failed for field: invalid payload length")
            }
            val iv = payload.copyOfRange(0, GCM_IV_LENGTH)
            val encryptedBytes = payload.copyOfRange(GCM_IV_LENGTH, payload.size)

            val key = keySupplier()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(encryptedBytes).toString(Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            throw EncryptionException("Decryption failed for field: invalid base64 payload", e)
        } catch (e: Exception) {
            throw EncryptionException("Decryption failed for field", e)
        }
    }

    companion object {
        const val DEFAULT_KEY_ALIAS = "anda_field_encryption_key_v1"

        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_BITS = 128
        private const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
        private const val MIN_PAYLOAD_LENGTH = GCM_IV_LENGTH + GCM_TAG_BYTES
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AES_KEY_SIZE = 256

        /**
         * Retrieves an existing AES key from Android KeyStore, or creates and stores a new one.
         * Must be called on an Android device/emulator.
         */
        fun getOrCreateKeyStoreKey(alias: String = DEFAULT_KEY_ALIAS): SecretKey {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).also { it.load(null) }

            keyStore.getKey(alias, null)?.let { return it as SecretKey }

            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(AES_KEY_SIZE)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()

            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        }
    }
}

