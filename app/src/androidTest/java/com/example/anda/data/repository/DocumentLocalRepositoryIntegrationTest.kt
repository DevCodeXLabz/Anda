package com.example.anda.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.anda.data.local.AppDatabase
import com.example.anda.data.local.entity.DocumentEntity
import com.example.anda.data.local.entity.ServiceRequestEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentLocalRepositoryIntegrationTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Use the real AppDatabase instance (instrumentation environment).
        db = AppDatabase.getInstance(context)
        // Ensure repository initialized to use same DB instance
        DocumentLocalRepository.initialize(context)
        // Clean tables to avoid interference
        runBlocking {
            db.serviceRequestDao().listAll().forEach { db.serviceRequestDao().deleteById(it.id) }
            db.documentDao().listAllDocumentIds(1000).forEach { id -> /* no-op */ }
        }
    }

    @After
    fun tearDown() {
        // No-op: keep DB as-is for instrumentation environment
    }

    @Test
    fun explicit_sourceCode_links_to_request() = runBlocking {
        val request = ServiceRequestEntity(
            requestCode = "REQ-EXPLICIT",
            contractorName = "ACME",
            contractorCnpj = "00000000000191",
            requestedDocumentType = "ASO",
            status = "OPEN"
        )
        val id = db.serviceRequestDao().upsert(request)

        val doc = DocumentEntity(
            documentId = "doc-explicit-1",
            documentType = "ASO",
            companyCnpj = "00000000000191",
            title = "Doc explicit",
            payloadJson = "{}",
            contentHash = "hash",
            sourceRequestCode = "REQ-EXPLICIT"
        )

        DocumentLocalRepository.saveDraft(doc)

        val updated = db.serviceRequestDao().findByRequestCode("REQ-EXPLICIT")
        assertNotNull(updated)
        assertTrue(updated!!.notes.contains("doc=doc-explicit-1;DRAFT"))
        assertEquals("IN_PROGRESS", updated.status)
    }

    @Test
    fun explicit_code_not_found_falls_back_to_active_request() = runBlocking {
        val request = ServiceRequestEntity(
            requestCode = "REQ-ACTIVE",
            contractorName = "Beta",
            contractorCnpj = "00000000000191",
            requestedDocumentType = "ASO",
            status = "OPEN"
        )
        db.serviceRequestDao().upsert(request)

        val doc = DocumentEntity(
            documentId = "doc-fallback-1",
            documentType = "ASO",
            companyCnpj = "00000000000191",
            title = "Doc fallback",
            payloadJson = "{}",
            contentHash = "hash",
            sourceRequestCode = "NON_EXISTENT"
        )

        DocumentLocalRepository.saveDraft(doc)

        val updated = db.serviceRequestDao().findByRequestCode("REQ-ACTIVE")
        assertNotNull(updated)
        assertTrue(updated!!.notes.contains("doc=doc-fallback-1;DRAFT"))
        assertEquals("IN_PROGRESS", updated.status)
    }

    @Test
    fun explicit_code_not_found_and_no_active_requests_does_nothing() = runBlocking {
        // Ensure no active requests exist for this CNPJ
        val docsBefore = db.serviceRequestDao().listAll()
        // Use a unique CNPJ
        val doc = DocumentEntity(
            documentId = "doc-noactive-1",
            documentType = "ASO",
            companyCnpj = "99999999999999",
            title = "Doc no active",
            payloadJson = "{}",
            contentHash = "hash",
            sourceRequestCode = "NON_EXISTENT"
        )

        DocumentLocalRepository.saveDraft(doc)

        val docsAfter = db.serviceRequestDao().listAll()
        // No new service requests should have been created by the linking
        assertEquals(docsBefore.size.toLong(), docsAfter.size.toLong())
    }
}

