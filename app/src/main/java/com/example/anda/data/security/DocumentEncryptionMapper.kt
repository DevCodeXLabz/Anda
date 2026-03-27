package com.example.anda.data.security

import com.example.anda.data.local.entity.DocumentEntity

/**
 * Maps [DocumentEntity] fields through [SafeFieldEncryptor] before persistence
 * and after retrieval from the local database.
 *
 * **Encrypted fields** (PII / LGPD-sensitive):
 * - [DocumentEntity.companyCnpj] — CNPJ da empresa (14 digits, uniquely identifiable)
 * - [DocumentEntity.signedBy]    — nome/CPF do signatário
 *
 * **Not encrypted** (needed for indexing/search or already hashed):
 * - [DocumentEntity.documentId], [DocumentEntity.documentType] — identifiers / routing
 * - [DocumentEntity.contentHash] — already a hash, not PII
 * - [DocumentEntity.signatureB64], [DocumentEntity.publicKeyFingerprint] — crypto material
 * - All timestamp and boolean fields
 *
 * This mapper is intentionally schema-transparent: it works on top of the current
 * Room entity without requiring a database migration.
 */
class DocumentEncryptionMapper(private val encryptor: SafeFieldEncryptor) {

    /**
     * Returns a copy of [entity] with sensitive fields encrypted.
     * Safe to call multiple times — already-encrypted fields are left unchanged.
     */
    fun encrypt(entity: DocumentEntity): DocumentEntity = entity.copy(
        companyCnpj = encryptor.encrypt(entity.companyCnpj),
        signedBy = encryptor.encryptNullable(entity.signedBy)
    )

    /**
     * Returns a copy of [entity] with sensitive fields decrypted.
     * Legacy rows without `enc::` prefix are returned as-is (transparent migration).
     */
    fun decrypt(entity: DocumentEntity): DocumentEntity = entity.copy(
        companyCnpj = encryptor.decrypt(entity.companyCnpj),
        signedBy = encryptor.decryptNullable(entity.signedBy)
    )

    /**
     * Encrypts a list of entities. Convenience wrapper over [encrypt].
     */
    fun encryptAll(entities: List<DocumentEntity>): List<DocumentEntity> =
        entities.map { encrypt(it) }

    /**
     * Decrypts a list of entities. Convenience wrapper over [decrypt].
     */
    fun decryptAll(entities: List<DocumentEntity>): List<DocumentEntity> =
        entities.map { decrypt(it) }
}

