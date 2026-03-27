package com.example.anda.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.anda.data.local.entity.DocumentEntity

@Dao
interface DocumentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: DocumentEntity)

    @Query("SELECT * FROM documents WHERE documentId = :documentId LIMIT 1")
    suspend fun findByDocumentId(documentId: String): DocumentEntity?

    @Query(
        """
        SELECT * FROM documents
        WHERE sourceRequestCode = :requestCode
        ORDER BY updatedAt DESC
        LIMIT 1
        """
    )
    suspend fun findLatestByRequestCode(requestCode: String): DocumentEntity?

    @Query("SELECT * FROM documents ORDER BY updatedAt DESC LIMIT 1")
    suspend fun findLatest(): DocumentEntity?

    @Query("SELECT * FROM documents WHERE companyCnpj = :companyCnpj ORDER BY updatedAt DESC LIMIT 1")
    suspend fun findLatestByCompanyCnpj(companyCnpj: String): DocumentEntity?

    @Query(
        """
        SELECT * FROM documents
        WHERE companyCnpj = :companyCnpj
          AND documentType = :documentType
        ORDER BY updatedAt DESC
        LIMIT 1
        """
    )
    suspend fun findLatestByCompanyAndType(companyCnpj: String, documentType: String): DocumentEntity?

    @Query("SELECT documentId FROM documents ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun listAllDocumentIds(limit: Int = 500): List<String>

    @Query(
        """
        SELECT documentId FROM documents
        WHERE signedAt IS NOT NULL AND isSynced = 0
        ORDER BY updatedAt DESC
        LIMIT :limit
        """
    )
    suspend fun listSignedUnsyncedDocumentIds(limit: Int = 100): List<String>

    @Query(
        """
        SELECT * FROM documents
        WHERE (:typeFilter = '' OR documentType = :typeFilter)
          AND (:companyFilter = '' OR companyCnpj LIKE '%' || :companyFilter || '%')
          AND (:titleFilter = '' OR title LIKE '%' || :titleFilter || '%')
        ORDER BY updatedAt DESC
        LIMIT :limit
        """
    )
    suspend fun listDocuments(typeFilter: String = "", companyFilter: String = "", titleFilter: String = "", limit: Int = 100): List<DocumentEntity>

    @Query("UPDATE documents SET isSynced = 1, updatedAt = :updatedAt WHERE documentId = :documentId")
    suspend fun markSynced(documentId: String, updatedAt: Long = System.currentTimeMillis())

    @Query(
        """
        UPDATE documents
        SET signedAt = :signedAt,
            signedBy = :signedBy,
            contentHash = :contentHash,
            signatureB64 = :signatureB64,
            publicKeyFingerprint = :publicKeyFingerprint,
            signatureAlgorithm = :signatureAlgorithm,
            isSynced = 0,
            updatedAt = :updatedAt
        WHERE documentId = :documentId
        """
    )
    suspend fun markSigned(
        documentId: String,
        signedAt: Long,
        signedBy: String,
        contentHash: String,
        signatureB64: String = "",
        publicKeyFingerprint: String = "",
        signatureAlgorithm: String = "SHA256withRSA",
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE documents
        SET pdfPath = :pdfPath,
            updatedAt = :updatedAt
        WHERE documentId = :documentId
        """
    )
    suspend fun markPdfExported(
        documentId: String,
        pdfPath: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE documents
        SET auditTxtPath = :auditTxtPath,
            updatedAt = :updatedAt
        WHERE documentId = :documentId
        """
    )
    suspend fun markAuditExported(
        documentId: String,
        auditTxtPath: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    /** Returns the auto-generated primary key (Long) for a given string documentId. */
    @Query("SELECT id FROM documents WHERE documentId = :documentId LIMIT 1")
    suspend fun getIdByDocumentId(documentId: String): Long?

    @Query("SELECT COUNT(*) FROM documents")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM documents WHERE isSynced = 0")
    suspend fun countUnsynced(): Int

    @Query("SELECT COUNT(*) FROM documents WHERE signedAt IS NOT NULL AND signedAt >= :sinceEpochMs")
    suspend fun countSignedSince(sinceEpochMs: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM documents
        WHERE validUntil IS NOT NULL
          AND validUntil >= :nowEpochMs
          AND validUntil <= :untilEpochMs
        """
    )
    suspend fun countExpiringBetween(nowEpochMs: Long, untilEpochMs: Long): Int

    // ─── LGPD / GDPR ──────────────────────────────────────────────────────────

    @Query("SELECT * FROM documents WHERE signedBy = :cpf ORDER BY updatedAt DESC")
    suspend fun getAllByEmployee(cpf: String): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE companyCnpj = :cnpj ORDER BY updatedAt DESC")
    suspend fun getAllByCompany(cnpj: String): List<DocumentEntity>

    @Query(
        "UPDATE documents SET payloadJson = :placeholder, signedBy = NULL, signatureB64 = NULL, " +
        "updatedAt = :now WHERE signedBy = :cpf"
    )
    suspend fun anonymiseByCpf(
        cpf: String,
        placeholder: String,
        now: Long = System.currentTimeMillis()
    ): Int
}
