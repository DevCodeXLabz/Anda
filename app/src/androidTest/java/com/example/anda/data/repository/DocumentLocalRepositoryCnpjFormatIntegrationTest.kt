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
class DocumentLocalRepositoryCnpjFormatIntegrationTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = AppDatabase.getInstance(context)
        DocumentLocalRepository.initialize(context)
        runBlocking {
            db.serviceRequestDao().listAll().forEach { db.serviceRequestDao().deleteById(it.id) }
        }
    }

    @After
    fun tearDown() {
        // no-op
    }

    @Test
    fun fallback_matches_when_cnpj_format_differs() = runBlocking {
        // Service request saved with digits-only CNPJ
        val request = ServiceRequestEntity(
            requestCode = "REQ-FORMAT",
            contractorName = "Gamma",
            contractorCnpj = "12345678000191",
            requestedDocumentType = "ASO",
            status = "OPEN"
        )
        db.serviceRequestDao().upsert(request)

        // Document saved with formatted CNPJ
        val doc = DocumentEntity(
            documentId = "doc-format-1",
            documentType = "ASO",
            companyCnpj = "12.345.678/0001-91",
            title = "Doc formatted cnpj",
            payloadJson = "{}",
            contentHash = "hash",
            sourceRequestCode = "NON_EXISTENT"
        )

        DocumentLocalRepository.saveDraft(doc)

        val updated = db.serviceRequestDao().findByRequestCode("REQ-FORMAT")
        assertNotNull(updated)
        assertTrue(updated!!.notes.contains("doc=doc-format-1;DRAFT"))
        assertEquals("IN_PROGRESS", updated.status)
    }
}

