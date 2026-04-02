package com.example.anda.data.repository

import com.example.anda.data.local.AppDatabase
import com.example.anda.data.local.dao.ServiceRequestDao
import com.example.anda.data.local.entity.DocumentEntity
import com.example.anda.data.local.entity.ServiceRequestEntity
import com.example.anda.data.requests.ServiceRequestLifecyclePolicy
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`


class DocumentLocalRepositoryFallbackTest {

    @Test
    fun fallback_tries_formatted_then_normalized() {
        runBlocking {
        val mockDao = Mockito.mock(ServiceRequestDao::class.java)

        // When searching by formatted CNPJ return empty, by normalized return a result
        `when`(mockDao.listActiveByContractorCnpj("12.345.678/0001-91")).thenReturn(emptyList())
        val active = listOf(
            ServiceRequestEntity(
                requestCode = "REQ-TEST",
                contractorName = "ACME",
                contractorCnpj = "12345678000191",
                requestedDocumentType = "ASO",
                status = "OPEN"
            )
        )
        `when`(mockDao.listActiveByContractorCnpj("12345678000191")).thenReturn(active)

        val doc = DocumentEntity(
            documentId = "doc-fallback-ux",
            documentType = "ASO",
            companyCnpj = "12.345.678/0001-91",
            title = "Doc fallback test",
            payloadJson = "{}",
            contentHash = "hash",
            sourceRequestCode = "NON_EXISTENT"
        )

        // Install provider that returns our mock DAO
        DocumentLocalRepository.setTestServiceRequestDaoProvider { mockDao }

        try {
            // Call the internal link method using null database (provider is used)
            DocumentLocalRepository.linkRequestForDocumentForTesting(
                testDatabase = Mockito.mock(AppDatabase::class.java), // not used by provider
                document = doc,
                event = ServiceRequestLifecyclePolicy.DocumentEvent.DRAFT_SAVED
            )

            // Verify that DAO was queried for both candidates (formatted and normalized)
            Mockito.verify(mockDao).listActiveByContractorCnpj("12.345.678/0001-91")
                    Mockito.verify(mockDao).listActiveByContractorCnpj("12345678000191")
                } finally {
                    DocumentLocalRepository.setTestServiceRequestDaoProvider(null)
                }
            }
    }
}




