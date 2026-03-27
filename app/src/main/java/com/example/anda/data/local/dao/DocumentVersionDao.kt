package com.example.anda.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.anda.data.local.entity.DocumentVersionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for document version history and audit trail.
 */
@Dao
interface DocumentVersionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(version: DocumentVersionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(versions: List<DocumentVersionEntity>)

    @Update
    suspend fun update(version: DocumentVersionEntity)

    @Delete
    suspend fun delete(version: DocumentVersionEntity)

    // Queries

    @Query("SELECT * FROM document_versions WHERE id = :id")
    suspend fun getById(id: Long): DocumentVersionEntity?

    @Query("""
        SELECT * FROM document_versions 
        WHERE documentId = :documentId 
        ORDER BY versionNumber DESC
    """)
    fun getVersionsForDocument(documentId: Long): Flow<List<DocumentVersionEntity>>

    @Query("""
        SELECT * FROM document_versions 
        WHERE documentId = :documentId 
        ORDER BY versionNumber DESC
        LIMIT 1
    """)
    suspend fun getCurrentVersion(documentId: Long): DocumentVersionEntity?

    @Query("""
        SELECT * FROM document_versions 
        WHERE documentId = :documentId AND versionNumber = :versionNumber
    """)
    suspend fun getSpecificVersion(documentId: Long, versionNumber: Int): DocumentVersionEntity?

    @Query("""
        SELECT * FROM document_versions 
        WHERE documentId = :documentId AND isRestorable = 1
        ORDER BY versionNumber DESC
    """)
    fun getRestorableVersions(documentId: Long): Flow<List<DocumentVersionEntity>>

    @Query("""
        SELECT * FROM document_versions 
        WHERE documentId = :documentId 
        AND createdAt BETWEEN :startTime AND :endTime
        ORDER BY createdAt DESC
    """)
    fun getVersionsByDateRange(documentId: Long, startTime: Long, endTime: Long): Flow<List<DocumentVersionEntity>>

    @Query("""
        SELECT * FROM document_versions 
        WHERE documentId = :documentId AND editedBy = :editor
        ORDER BY createdAt DESC
    """)
    fun getVersionsByEditor(documentId: Long, editor: String): Flow<List<DocumentVersionEntity>>

    @Query("""
        SELECT * FROM document_versions 
        WHERE documentId = :documentId AND changeType = :changeType
        ORDER BY createdAt DESC
    """)
    fun getVersionsByChangeType(documentId: Long, changeType: String): Flow<List<DocumentVersionEntity>>

    @Query("""
        SELECT COUNT(*) FROM document_versions WHERE documentId = :documentId
    """)
    fun getVersionCount(documentId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM document_versions WHERE documentId = :documentId")
    suspend fun getVersionCountDirect(documentId: Long): Int

    @Query("""
        DELETE FROM document_versions 
        WHERE documentId = :documentId 
        AND createdAt < :cutoffTime
        AND isCurrent = 0
    """)
    suspend fun deleteOldVersions(documentId: Long, cutoffTime: Long)

    @Query("""
        DELETE FROM document_versions WHERE documentId = :documentId
    """)
    suspend fun deleteAllVersionsForDocument(documentId: Long)

    @Query("""
        UPDATE document_versions 
        SET isCurrent = 0 
        WHERE documentId = :documentId
    """)
    suspend fun markAllAsNonCurrent(documentId: Long)

    @Query("""
        UPDATE document_versions 
        SET isCurrent = 1 
        WHERE documentId = :documentId AND id = :versionId
    """)
    suspend fun markAsCurrent(documentId: Long, versionId: Long)

}

