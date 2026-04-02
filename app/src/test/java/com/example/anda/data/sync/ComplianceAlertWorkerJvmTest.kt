package com.example.anda.data.sync

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class ComplianceAlertWorkerJvmTest {

    @After
    fun tearDown() {
        ComplianceAlertWorker.testHook = null
    }

    @Test
    fun `runComplianceCheckForTest captures notification counts when positive`() = runBlocking {
        val captured = mutableListOf<NotificationCountsRecord>()
        ComplianceAlertWorker.testHook = TestHook(countsProvider = { _ -> Pair(3, 7) }, notificationCountsCaptor = captured)

        val now = System.currentTimeMillis()
        val result = runComplianceCheckForTest(now)

        assertEquals(1, captured.size)
        val rec = captured.first()
        assertEquals(3, rec.expired)
        assertEquals(7, rec.expiring)
        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
    }

    @Test
    fun `runComplianceCheckForTest does not capture when zero`() = runBlocking {
        val captured = mutableListOf<NotificationCountsRecord>()
        ComplianceAlertWorker.testHook = TestHook(countsProvider = { _ -> Pair(0, 0) }, notificationCountsCaptor = captured)

        val now = System.currentTimeMillis()
        val result = runComplianceCheckForTest(now)

        assertEquals(0, captured.size)
        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
    }
}

