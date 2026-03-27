package com.example.anda.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.anda.data.local.entity.TemplateEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Template operations.
 * Provides queries for template management, retrieval, and filtering.
 */
@Dao
interface TemplateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: TemplateEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(templates: List<TemplateEntity>)

    @Update
    suspend fun update(template: TemplateEntity)

    @Delete
    suspend fun delete(template: TemplateEntity)

    // Queries

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun getById(id: Long): TemplateEntity?

    @Query("SELECT * FROM templates WHERE documentType = :documentType AND isActive = 1 ORDER BY name ASC")
    fun getByDocumentType(documentType: String): Flow<List<TemplateEntity>>

    @Query("""
        SELECT * FROM templates 
        WHERE isActive = 1 
        AND (applicableCnaes = '*' OR applicableCnaes LIKE :cnaePattern)
        AND documentType = :documentType
        ORDER BY usageCount DESC, name ASC
    """)
    fun getByCANAE(cnaePattern: String, documentType: String): Flow<List<TemplateEntity>>

    @Query("""
        SELECT * FROM templates 
        WHERE isActive = 1 
        AND (name LIKE :searchTerm OR description LIKE :searchTerm)
        ORDER BY usageCount DESC, name ASC
    """)
    fun searchByName(searchTerm: String = "%"): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates WHERE isActive = 1 ORDER BY updatedAt DESC LIMIT :limit")
    fun getLatestVersions(limit: Int = 10): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates WHERE isActive = 1 ORDER BY usageCount DESC LIMIT :limit")
    fun getMostUsed(limit: Int = 5): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates WHERE isSystemTemplate = 1 AND isActive = 1 ORDER BY name ASC")
    fun getSystemTemplates(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates WHERE isSystemTemplate = 0 AND isActive = 1 ORDER BY name ASC")
    fun getUserTemplates(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates WHERE category = :category AND isActive = 1 ORDER BY name ASC")
    fun getByCategory(category: String): Flow<List<TemplateEntity>>

    @Query("SELECT DISTINCT category FROM templates WHERE isActive = 1 ORDER BY category ASC")
    fun getAllCategories(): Flow<List<String>>

    @Query("SELECT DISTINCT documentType FROM templates WHERE isActive = 1 ORDER BY documentType ASC")
    fun getAllDocumentTypes(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM templates WHERE isActive = 1 AND documentType = :documentType")
    fun countByDocumentType(documentType: String): Flow<Int>

    @Query("SELECT * FROM templates WHERE createdBy = :createdBy AND isActive = 1 ORDER BY createdAt DESC")
    fun getByCreatedBy(createdBy: String): Flow<List<TemplateEntity>>

    @Query("UPDATE templates SET usageCount = usageCount + 1, lastUsedAt = :timestamp WHERE id = :id")
    suspend fun incrementUsageCount(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM templates WHERE isActive = 1 AND isSystemTemplate = 0")
    suspend fun deleteAllUserTemplates()

    @Query("SELECT * FROM templates WHERE isActive = 1 LIMIT :limit OFFSET :offset")
    suspend fun getPagedTemplates(limit: Int, offset: Int): List<TemplateEntity>

    @Query("SELECT * FROM templates WHERE isActive = 1 AND documentType = :documentType LIMIT :limit OFFSET :offset")
    suspend fun getPagedTemplatesByType(documentType: String, limit: Int, offset: Int): List<TemplateEntity>

}

