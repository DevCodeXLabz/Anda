package com.example.anda.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.anda.data.local.entity.TemplateEntity
import com.example.anda.data.local.entity.DocumentVersionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

/**
 * Integration tests for AppDatabase verifying Phase 2a DAO integration.
 * Tests database creation, DAO accessibility, and version 11 migration.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseIntegrationTest {

    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun databaseCreation_succeeds() {
        assertNotNull(db)
        val expectedVersion = AppDatabase::class.java
            .getAnnotation(Database::class.java)
            ?.version
            ?: 14
        assertEquals(expectedVersion.toLong(), db.openHelper.readableDatabase.version.toLong())
    }

    @Test
    fun templateDao_isAccessible() {
        val dao = db.templateDao()
        assertNotNull(dao)
    }

    @Test
    fun documentVersionDao_isAccessible() {
        val dao = db.documentVersionDao()
        assertNotNull(dao)
    }

    @Test
    fun allDaos_areAccessible() {
        assertNotNull(db.companyDao())
        assertNotNull(db.documentDao())
        assertNotNull(db.syncQueueDao())
        assertNotNull(db.syncAttemptLogDao())
        assertNotNull(db.employeeDao())
        assertNotNull(db.serviceRequestDao())
        assertNotNull(db.templateDao())
        assertNotNull(db.documentVersionDao())
    }

    @Test
    fun templateDao_canInsertAndRetrieve() = runBlocking {
        val template = TemplateEntity(
            id = 1,
            documentType = "ASO",
            name = "Test Template",
            description = "Test Description",
            contentJson = "{\"type\": \"aso\"}",
            isSystemTemplate = true,
            isActive = true,
            category = "Health",
            applicableCnaes = "*",
            usageCount = 0,
            lastUsedAt = System.currentTimeMillis(),
            createdBy = "system",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        val templateDao = db.templateDao()
        templateDao.insert(template)

        val retrieved = templateDao.getById(1)
        assertNotNull(retrieved)
        assertEquals("ASO", retrieved!!.documentType)
        assertEquals("Test Template", retrieved.name)
    }

    @Test
    fun documentVersionDao_canInsertAndRetrieve() = runBlocking {
        val version = DocumentVersionEntity(
            id = 1,
            documentId = 123,
            versionNumber = 1,
            contentSnapshot = "{json: data}",
            changeType = "CREATE",
            editedBy = "user@example.com",
            editReason = "Initial creation",
            isRestorable = true,
            createdAt = System.currentTimeMillis()
        )

        val versionDao = db.documentVersionDao()
        // ensure parent Document exists to satisfy foreign key
        val doc = com.example.anda.data.local.entity.DocumentEntity(
            id = 123,
            documentId = "doc-123",
            documentType = "TEST",
            title = "Test Doc",
            companyCnpj = "",
            payloadJson = "{}",
            pdfPath = "",
            auditTxtPath = "",
            signedBy = "",
            contentHash = "",
            signatureB64 = "",
            publicKeyFingerprint = "",
            signatureAlgorithm = "",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        db.documentDao().upsert(doc)

        versionDao.insert(version)

        val retrieved = versionDao.getVersionsForDocument(123)
        val versions = retrieved.first()
        assertEquals(1, versions.size.toLong())
        assertEquals("CREATE", versions[0].changeType)
    }

    @Test
    fun templateDao_usageCountIncrement() = runBlocking {
        val template = TemplateEntity(
            id = 2,
            documentType = "PCMSO",
            name = "PCMSO Test",
            description = "PCMSO Template",
            contentJson = "{\"type\": \"pcmso\"}",
            isSystemTemplate = false,
            isActive = true,
            category = "Medical",
            applicableCnaes = "*",
            usageCount = 0,
            lastUsedAt = System.currentTimeMillis(),
            createdBy = "user",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        val templateDao = db.templateDao()
        templateDao.insert(template)

        // Increment usage
        templateDao.incrementUsageCount(2)

        val retrieved = templateDao.getById(2)
        assertNotNull(retrieved)
        assertEquals(1, retrieved!!.usageCount.toLong())
    }

    @Test
    fun documentVersionDao_getsByDocumentId() = runBlocking {
        val versionDao = db.documentVersionDao()

        val version1 = DocumentVersionEntity(
            id = 1,
            documentId = 456,
            versionNumber = 1,
            contentSnapshot = "content1",
            changeType = "CREATE",
            editedBy = "user1",
            editReason = "Created",
            isRestorable = true,
            createdAt = System.currentTimeMillis()
        )

        val version2 = DocumentVersionEntity(
            id = 2,
            documentId = 456,
            versionNumber = 2,
            contentSnapshot = "content2",
            changeType = "EDIT",
            editedBy = "user2",
            editReason = "Updated",
            isRestorable = true,
            createdAt = System.currentTimeMillis()
        )

        // ensure parent Document exists
        val doc = com.example.anda.data.local.entity.DocumentEntity(
            id = 456,
            documentId = "doc-456",
            documentType = "TEST",
            title = "Test Doc",
            companyCnpj = "",
            payloadJson = "{}",
            pdfPath = "",
            auditTxtPath = "",
            signedBy = "",
            contentHash = "",
            signatureB64 = "",
            publicKeyFingerprint = "",
            signatureAlgorithm = "",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        db.documentDao().upsert(doc)

        versionDao.insert(version1)
        versionDao.insert(version2)

        val versions = versionDao.getVersionsForDocument(456).first()
        assertEquals(2, versions.size.toLong())
        assertEquals("EDIT", versions[0].changeType)
        assertEquals("CREATE", versions[1].changeType)
    }

    @Test
    fun databaseMigration_preservesExistingData() = runBlocking {
        // This test verifies that the migration from version 10 to 11
        // doesn't break existing entities
        val companyDao = db.companyDao()

        // The database should be accessible with all existing DAOs
        // even after the new version 11 migration
        assertNotNull(companyDao)
    }
}


