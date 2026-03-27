package com.example.anda.data.services

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for SyncMonitoringService.
 * Tests retry schedule, health metrics, and monitoring capabilities.
 */
class SyncMonitoringServiceTest {

    private val syncService = SyncMonitoringService(
        documentDao = TestDocumentDaoStub(),
        syncAttemptLogDao = TestSyncAttemptLogDaoStub()
    )

    @Test
    fun `test exponential backoff schedule generates correct delays`() {
        val schedule = syncService.getRetrySchedule(maxAttempts = 5)
        
        assertEquals(5, schedule.size)
        assertEquals(5000L, schedule[0].delayMs) // 5 seconds
        assertEquals(10000L, schedule[1].delayMs) // 10 seconds
        assertEquals(20000L, schedule[2].delayMs) // 20 seconds
        assertEquals(40000L, schedule[3].delayMs) // 40 seconds
        assertEquals(80000L, schedule[4].delayMs) // 80 seconds
    }

    @Test
    fun `test retry schedule caps at 1 hour`() {
        val schedule = syncService.getRetrySchedule(maxAttempts = 15)
        
        // Last few attempts should all be capped at 1 hour
        val lastAttempt = schedule[14]
        assertEquals(3600000L, lastAttempt.delayMs) // 1 hour
    }

    @Test
    fun `test retry schedule descriptions are human readable`() {
        val schedule = syncService.getRetrySchedule()
        
        assertEquals("Immediate retry (5 seconds)", schedule[0].description)
        assertEquals("Short delay (10 seconds)", schedule[1].description)
        assertEquals("Medium delay (20 seconds)", schedule[2].description)
        assertEquals("42 min 40 sec", schedule[9].description)
    }

    @Test
    fun `test retry schedule descriptions match computed delays for attempts 5 to 9`() {
        val schedule = syncService.getRetrySchedule(maxAttempts = 9)

        assertEquals("1 min 20 sec", schedule[4].description)
        assertEquals("2 min 40 sec", schedule[5].description)
        assertEquals("5 min 20 sec", schedule[6].description)
        assertEquals("10 min 40 sec", schedule[7].description)
        assertEquals("21 min 20 sec", schedule[8].description)
    }

    @Test
    fun `test health stats calculates success rate correctly`() = runBlocking {
        val stats = syncService.getSyncHealthStats()
        
        // With stub data
        assertTrue(stats.totalDocuments >= 0)
        assertTrue(stats.successRate >= 0f && stats.successRate <= 1f)
    }

    @Test
    fun `test retry stats returns valid data`() = runBlocking {
        val stats = syncService.getRetryStats()
        
        assertNotNull(stats)
        assertTrue(stats.totalAttempts >= 0)
        assertTrue(stats.averageAttemptsPerDocument >= 0f)
    }

    @Test
    fun `test retry schedule has correct attempt numbering`() {
        val schedule = syncService.getRetrySchedule(maxAttempts = 5)
        
        for (i in schedule.indices) {
            assertEquals(i + 1, schedule[i].attempt)
        }
    }

    @Test
    fun `test exponential backoff formula is correct`() {
        val schedule = syncService.getRetrySchedule(maxAttempts = 8)
        
        for (i in 0 until 7) {
            val expectedDelaySeconds = Math.pow(2.0, i.toDouble()) * 5
            val expectedDelayMs = (expectedDelaySeconds * 1000).toLong()
            assertEquals(expectedDelayMs, schedule[i].delayMs)
        }
    }

    @Test
    fun `test retry stats returns zeroes when attempt log is empty`() = runBlocking {
        val stats = syncService.getRetryStats()

        assertEquals(0, stats.totalAttempts)
        assertEquals(0f, stats.averageAttemptsPerDocument, 0.001f)
        assertEquals(0, stats.maxAttemptsForDocument)
    }

    @Test
    fun `test health stats averageRetries reflects DAO data`() = runBlocking {
        val stats = syncService.getSyncHealthStats()

        assertTrue(stats.averageRetries >= 0f)
    }

    @Test
    fun `test retry stats with seeded stub returns correct counts`() = runBlocking {
        val seededStub = SeededSyncAttemptLogDaoStub(total = 15, avgPerItem = 3f, maxForItem = 7)
        val serviceWithData = SyncMonitoringService(TestDocumentDaoStub(), seededStub)

        val stats = serviceWithData.getRetryStats()

        assertEquals(15, stats.totalAttempts)
        assertEquals(3f, stats.averageAttemptsPerDocument, 0.001f)
        assertEquals(7, stats.maxAttemptsForDocument)
    }

    @Test
    fun `test health stats averageRetries wired to DAO when data present`() = runBlocking {
        val seededStub = SeededSyncAttemptLogDaoStub(total = 9, avgPerItem = 4.5f, maxForItem = 6)
        val serviceWithData = SyncMonitoringService(TestDocumentDaoStub(), seededStub)

        val stats = serviceWithData.getSyncHealthStats()

        assertEquals(4.5f, stats.averageRetries, 0.001f)
    }

    @Test
    fun `test health stats fallback to safe defaults when document dao throws`() = runBlocking {
        val throwingDao = object : com.example.anda.data.local.dao.DocumentDao by TestDocumentDaoStub() {
            override suspend fun countAll(): Int = throw IllegalStateException("db unavailable")
            override suspend fun countUnsynced(): Int = throw IllegalStateException("db unavailable")
        }
        val serviceWithFailingDao = SyncMonitoringService(throwingDao, TestSyncAttemptLogDaoStub())

        val stats = serviceWithFailingDao.getSyncHealthStats()

        assertEquals(0, stats.totalDocuments)
        assertEquals(0, stats.unsyncedDocuments)
        assertEquals(0, stats.syncedDocuments)
        assertEquals(1f, stats.successRate, 0.001f)
    }

    @Test
    fun `test health stats clamps unsynced documents when dao returns inconsistent counts`() = runBlocking {
        val inconsistentDao = object : com.example.anda.data.local.dao.DocumentDao by TestDocumentDaoStub() {
            override suspend fun countAll(): Int = 3
            override suspend fun countUnsynced(): Int = 10
        }
        val serviceWithInconsistentDao = SyncMonitoringService(inconsistentDao, TestSyncAttemptLogDaoStub())

        val stats = serviceWithInconsistentDao.getSyncHealthStats()

        assertEquals(3, stats.totalDocuments)
        assertEquals(3, stats.unsyncedDocuments)
        assertEquals(0, stats.syncedDocuments)
        assertEquals(0f, stats.successRate, 0.001f)
    }

    @Test
    fun `test retry stats fallback to zeroes when aggregate queries throw`() = runBlocking {
        val throwingLogDao = object : com.example.anda.data.local.dao.SyncAttemptLogDao by TestSyncAttemptLogDaoStub() {
            override suspend fun countAll(): Int = throw IllegalStateException("query failed")
            override suspend fun getAverageAttemptsPerItem(): Float? = throw IllegalStateException("query failed")
            override suspend fun getMaxAttemptsForItem(): Int? = throw IllegalStateException("query failed")
        }
        val serviceWithFailingLogDao = SyncMonitoringService(TestDocumentDaoStub(), throwingLogDao)

        val stats = serviceWithFailingLogDao.getRetryStats()

        assertEquals(0, stats.totalAttempts)
        assertEquals(0f, stats.averageAttemptsPerDocument, 0.001f)
        assertEquals(0, stats.maxAttemptsForDocument)
    }

    @Test
    fun `test retry stats sanitizes NaN average to zero`() = runBlocking {
        val nanAverageStub = SeededSyncAttemptLogDaoStub(total = 6, avgPerItem = Float.NaN, maxForItem = 3)
        val serviceWithNanAverage = SyncMonitoringService(TestDocumentDaoStub(), nanAverageStub)

        val stats = serviceWithNanAverage.getRetryStats()

        assertEquals(6, stats.totalAttempts)
        assertEquals(0f, stats.averageAttemptsPerDocument, 0.001f)
        assertEquals(3, stats.maxAttemptsForDocument)
    }

    @Test
    fun `test health stats sanitizes NaN average retries to zero`() = runBlocking {
        val nanAverageStub = SeededSyncAttemptLogDaoStub(total = 4, avgPerItem = Float.NaN, maxForItem = 2)
        val serviceWithNanAverage = SyncMonitoringService(TestDocumentDaoStub(), nanAverageStub)

        val stats = serviceWithNanAverage.getSyncHealthStats()

        assertEquals(0f, stats.averageRetries, 0.001f)
    }

    @Test
    fun `test health stats sanitizes negative last sync time to null`() = runBlocking {
        val negativeTimeStub = object : com.example.anda.data.local.dao.SyncAttemptLogDao by TestSyncAttemptLogDaoStub() {
            override suspend fun getLatestSuccessTime(): Long? = -1L
        }
        val serviceWithNegativeTime = SyncMonitoringService(TestDocumentDaoStub(), negativeTimeStub)

        val stats = serviceWithNegativeTime.getSyncHealthStats()

        assertEquals(null, stats.lastSyncTime)
    }

    @Test
    fun `test health stats preserves valid last sync time`() = runBlocking {
        val expectedTime = 1_700_000_000_000L
        val validTimeStub = object : com.example.anda.data.local.dao.SyncAttemptLogDao by TestSyncAttemptLogDaoStub() {
            override suspend fun getLatestSuccessTime(): Long? = expectedTime
        }
        val serviceWithValidTime = SyncMonitoringService(TestDocumentDaoStub(), validTimeStub)

        val stats = serviceWithValidTime.getSyncHealthStats()

        assertEquals(expectedTime, stats.lastSyncTime)
    }

    @Test
    fun `test retry stats sanitizes infinity average to zero`() = runBlocking {
        val infinityAverageStub = SeededSyncAttemptLogDaoStub(total = 5, avgPerItem = Float.POSITIVE_INFINITY, maxForItem = 4)
        val serviceWithInfinityAverage = SyncMonitoringService(TestDocumentDaoStub(), infinityAverageStub)

        val stats = serviceWithInfinityAverage.getRetryStats()

        assertEquals(5, stats.totalAttempts)
        assertEquals(0f, stats.averageAttemptsPerDocument, 0.001f)
        assertEquals(4, stats.maxAttemptsForDocument)
    }

    @Test
    fun `test health stats sanitizes negative infinity average retries to zero`() = runBlocking {
        val negativeInfinityAverageStub = SeededSyncAttemptLogDaoStub(total = 3, avgPerItem = Float.NEGATIVE_INFINITY, maxForItem = 1)
        val serviceWithNegativeInfinity = SyncMonitoringService(TestDocumentDaoStub(), negativeInfinityAverageStub)

        val stats = serviceWithNegativeInfinity.getSyncHealthStats()

        assertEquals(0f, stats.averageRetries, 0.001f)
    }

    @Test
    fun `test health stats returns null last sync time when latest success query throws`() = runBlocking {
        val throwingLatestSuccessStub = object : com.example.anda.data.local.dao.SyncAttemptLogDao by TestSyncAttemptLogDaoStub() {
            override suspend fun getLatestSuccessTime(): Long? = throw IllegalStateException("log unavailable")
        }
        val serviceWithThrowingLatestSuccess = SyncMonitoringService(TestDocumentDaoStub(), throwingLatestSuccessStub)

        val stats = serviceWithThrowingLatestSuccess.getSyncHealthStats()

        assertEquals(null, stats.lastSyncTime)
    }

    @Test
    fun `test retry schedule returns empty list for non positive attempts`() {
        val zeroSchedule = syncService.getRetrySchedule(maxAttempts = 0)
        val negativeSchedule = syncService.getRetrySchedule(maxAttempts = -3)

        assertTrue(zeroSchedule.isEmpty())
        assertTrue(negativeSchedule.isEmpty())
    }

    @Test
    fun `test retry schedule caps very large max attempts to safe bound`() {
        val schedule = syncService.getRetrySchedule(maxAttempts = 10_000)

        assertEquals(50, schedule.size)
        assertEquals(50, schedule.last().attempt)
        assertEquals(3_600_000L, schedule.last().delayMs)
        assertEquals("1 hour (max)", schedule.last().description)
    }

    @Test
    fun `test health stats clamps negative document counts to zero`() = runBlocking {
        val negativeCountsDao = object : com.example.anda.data.local.dao.DocumentDao by TestDocumentDaoStub() {
            override suspend fun countAll(): Int = -5
            override suspend fun countUnsynced(): Int = -2
        }
        val serviceWithNegativeCounts = SyncMonitoringService(negativeCountsDao, TestSyncAttemptLogDaoStub())

        val stats = serviceWithNegativeCounts.getSyncHealthStats()

        assertEquals(0, stats.totalDocuments)
        assertEquals(0, stats.unsyncedDocuments)
        assertEquals(0, stats.syncedDocuments)
        assertEquals(1f, stats.successRate, 0.001f)
    }

    @Test
    fun `test retry stats clamps negative total max and average to zero`() = runBlocking {
        val negativeAggregateStub = object : com.example.anda.data.local.dao.SyncAttemptLogDao by TestSyncAttemptLogDaoStub() {
            override suspend fun countAll(): Int = -4
            override suspend fun getAverageAttemptsPerItem(): Float? = -1.5f
            override suspend fun getMaxAttemptsForItem(): Int? = -9
        }
        val serviceWithNegativeAggregate = SyncMonitoringService(TestDocumentDaoStub(), negativeAggregateStub)

        val stats = serviceWithNegativeAggregate.getRetryStats()

        assertEquals(0, stats.totalAttempts)
        assertEquals(0f, stats.averageAttemptsPerDocument, 0.001f)
        assertEquals(0, stats.maxAttemptsForDocument)
    }

}

/**
 * Unit tests for BatchOperationService.
 * Tests batch export, sync retry, and statistics gathering.
 */
class BatchOperationServiceTest {

    @Test
    fun `test batch operation result calculates statistics correctly`() {
        val result = BatchOperationService.BatchOperationResult(
            operationType = "EXPORT_ZIP",
            totalItems = 10,
            successCount = 8,
            failureCount = 2,
            errors = listOf("Document 1 not found", "Document 2 export failed")
        )

        assertEquals(10, result.totalItems)
        assertEquals(8, result.successCount)
        assertEquals(2, result.failureCount)
        assertEquals(2, result.errors.size)
    }

    @Test
    fun `test batch operation success rate calculation`() {
        val result = BatchOperationService.BatchOperationResult(
            operationType = "MARK_SYNCED",
            totalItems = 100,
            successCount = 95,
            failureCount = 5
        )

        val successRate = result.successCount.toFloat() / result.totalItems
        assertEquals(0.95f, successRate)
    }

    @Test
    fun `test empty batch operation result`() {
        val result = BatchOperationService.BatchOperationResult(
            operationType = "RETRY_UNSYNCED",
            totalItems = 0,
            successCount = 0,
            failureCount = 0
        )

        assertEquals(0, result.totalItems)
        assertTrue(result.errors.isEmpty())
    }

}

/**
 * Unit tests for TemplateEntity.
 * Tests template data validation and defaults.
 */
class TemplateEntityTest {

    @Test
    fun `test template entity with default values`() {
        val template = com.example.anda.data.local.entity.TemplateEntity(
            name = "ASO Template",
            documentType = "ASO",
            contentJson = "{}"
        )

        assertEquals("ASO Template", template.name)
        assertEquals("ASO", template.documentType)
        assertEquals("*", template.applicableCnaes) // All industries
        assertEquals(1, template.version)
        assertTrue(template.isActive)
        assertEquals(0, template.usageCount)
    }

    @Test
    fun `test template entity with CNAE applicability`() {
        val template = com.example.anda.data.local.entity.TemplateEntity(
            name = "LTCAT Template",
            documentType = "LTCAT",
            applicableCnaes = "2131-8-00,2139-3-00", // Specific CNAEs
            contentJson = "{}"
        )

        assertTrue(template.applicableCnaes.contains("2131"))
        assertTrue(template.applicableCnaes.contains("2139"))
    }

    @Test
    fun `test template versioning fields`() {
        val template = com.example.anda.data.local.entity.TemplateEntity(
            name = "PT Template",
            documentType = "PT",
            version = 3,
            isSystemTemplate = false,
            contentJson = "{}"
        )

        assertEquals(3, template.version)
        assertTrue(!template.isSystemTemplate)
    }

}

/**
 * Unit tests for DocumentVersionEntity.
 * Tests version tracking and audit trail functionality.
 */
class DocumentVersionEntityTest {

    @Test
    fun `test document version entity with change tracking`() {
        val version = com.example.anda.data.local.entity.DocumentVersionEntity(
            documentId = 123L,
            versionNumber = 1,
            contentSnapshot = """{"title":"ASO","status":"draft"}""",
            editedBy = "technician_001",
            changeType = "CREATE"
        )

        assertEquals(123L, version.documentId)
        assertEquals(1, version.versionNumber)
        assertEquals("CREATE", version.changeType)
        assertTrue(version.isRestorable)
    }

    @Test
    fun `test version entity with change summary`() {
        val version = com.example.anda.data.local.entity.DocumentVersionEntity(
            documentId = 456L,
            versionNumber = 2,
            contentSnapshot = """{"title":"Updated ASO"}""",
            changesSummary = """{"title":"ASO -> Updated ASO"}""",
            changeType = "EDIT",
            editReason = "Corrected employee name"
        )

        assertEquals("EDIT", version.changeType)
        assertEquals("Corrected employee name", version.editReason)
        assertTrue(version.changesSummary.contains("title"))
    }

    @Test
    fun `test version immutability flags`() {
        val mutableVersion = com.example.anda.data.local.entity.DocumentVersionEntity(
            documentId = 789L,
            versionNumber = 1,
            contentSnapshot = "{}",
            isRestorable = true
        )

        val immutableVersion = com.example.anda.data.local.entity.DocumentVersionEntity(
            documentId = 789L,
            versionNumber = 2,
            contentSnapshot = "{}",
            isRestorable = false,
            changeType = "SIGNED"
        )

        assertTrue(mutableVersion.isRestorable)
        assertTrue(!immutableVersion.isRestorable)
    }


}

// Stub implementations for testing
internal class TestDocumentDaoStub : com.example.anda.data.local.dao.DocumentDao {
    override suspend fun upsert(document: com.example.anda.data.local.entity.DocumentEntity) {}
    override suspend fun findByDocumentId(documentId: String): com.example.anda.data.local.entity.DocumentEntity? = null
    override suspend fun findLatestByRequestCode(requestCode: String): com.example.anda.data.local.entity.DocumentEntity? = null
    override suspend fun findLatest(): com.example.anda.data.local.entity.DocumentEntity? = null
    override suspend fun findLatestByCompanyCnpj(companyCnpj: String): com.example.anda.data.local.entity.DocumentEntity? = null
    override suspend fun findLatestByCompanyAndType(companyCnpj: String, documentType: String): com.example.anda.data.local.entity.DocumentEntity? = null
    override suspend fun listAllDocumentIds(limit: Int): List<String> = emptyList()
    override suspend fun listSignedUnsyncedDocumentIds(limit: Int): List<String> = emptyList()
    override suspend fun listDocuments(typeFilter: String, companyFilter: String, titleFilter: String, limit: Int): List<com.example.anda.data.local.entity.DocumentEntity> = emptyList()
    override suspend fun markSynced(documentId: String, updatedAt: Long) {}
    override suspend fun markSigned(documentId: String, signedAt: Long, signedBy: String, contentHash: String, signatureB64: String, publicKeyFingerprint: String, signatureAlgorithm: String, updatedAt: Long) {}
    override suspend fun markPdfExported(documentId: String, pdfPath: String, updatedAt: Long) {}
    override suspend fun markAuditExported(documentId: String, auditTxtPath: String, updatedAt: Long) {}
    override suspend fun getIdByDocumentId(documentId: String): Long? = null
    override suspend fun countAll(): Int = 10
    override suspend fun countUnsynced(): Int = 2
    override suspend fun countSignedSince(sinceEpochMs: Long): Int = 5
    override suspend fun countExpiringBetween(nowEpochMs: Long, untilEpochMs: Long): Int = 3
}

internal class TestSyncAttemptLogDaoStub : com.example.anda.data.local.dao.SyncAttemptLogDao {
    override suspend fun insert(item: com.example.anda.data.local.entity.SyncAttemptLogEntity) {}
    override suspend fun listByItemRef(itemRef: String, limit: Int): List<com.example.anda.data.local.entity.SyncAttemptLogEntity> = emptyList()
    override suspend fun getLatestSuccessTime(): Long? = null
    override suspend fun countAll(): Int = 0
    override suspend fun getAverageAttemptsPerItem(): Float? = null
    override suspend fun getMaxAttemptsForItem(): Int? = null
}

/** Configurable stub for tests that need non-trivial aggregate return values. */
internal class SeededSyncAttemptLogDaoStub(
    private val total: Int,
    private val avgPerItem: Float,
    private val maxForItem: Int
) : com.example.anda.data.local.dao.SyncAttemptLogDao {
    override suspend fun insert(item: com.example.anda.data.local.entity.SyncAttemptLogEntity) {}
    override suspend fun listByItemRef(itemRef: String, limit: Int): List<com.example.anda.data.local.entity.SyncAttemptLogEntity> = emptyList()
    override suspend fun getLatestSuccessTime(): Long? = null
    override suspend fun countAll(): Int = total
    override suspend fun getAverageAttemptsPerItem(): Float = avgPerItem
    override suspend fun getMaxAttemptsForItem(): Int = maxForItem
}

