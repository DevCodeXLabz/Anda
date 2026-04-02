package com.example.anda.data.repository

import com.example.anda.data.local.AppDatabase
import com.example.anda.data.local.dao.ServiceRequestDao
import com.example.anda.data.local.entity.DocumentEntity
import com.example.anda.data.local.entity.ServiceRequestEntity
import com.example.anda.data.requests.ServiceRequestLifecyclePolicy
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mockito.Mockito

class DocumentLocalRepositoryFallbackEdgeCasesTest {

    @Test
    fun no_company_cnpj_does_not_query_dao() {
        val mockDao = Mockito.mock(ServiceRequestDao::class.java)

        DocumentLocalRepository.setTestServiceRequestDaoProvider { mockDao }
        try {
            val doc = DocumentEntity(
                documentId = "doc-no-cnpj",
                documentType = "ASO",
                companyCnpj = "",
                title = "No CNPJ",
                payloadJson = "{}",
                contentHash = "hash"
            )

            runBlocking {
                DocumentLocalRepository.linkRequestForDocumentForTesting(
                    testDatabase = Mockito.mock(AppDatabase::class.java),
                    document = doc,
                    event = ServiceRequestLifecyclePolicy.DocumentEvent.DRAFT_SAVED
                )
            }

            // Should not query for active requests when no CNPJ provided
            runBlocking {
                Mockito.verify(mockDao, Mockito.never()).listActiveByContractorCnpj(Mockito.anyString())
            }
        } finally {
            DocumentLocalRepository.setTestServiceRequestDaoProvider(null)
        }
    }

    @Test
    fun multiple_active_requests_updates_first_found() {
        val mockDao = Mockito.mock(ServiceRequestDao::class.java)

        val first = ServiceRequestEntity(
            id = 1,
            requestCode = "REQ-1",
            contractorName = "A",
            contractorCnpj = "123",
            requestedDocumentType = "ASO",
            status = "OPEN"
        )
        val second = ServiceRequestEntity(
            id = 2,
            requestCode = "REQ-2",
            contractorName = "B",
            contractorCnpj = "123",
            requestedDocumentType = "ASO",
            status = "OPEN"
        )

        runBlocking {
            Mockito.`when`(mockDao.listActiveByContractorCnpj("12.345.678/0001-91")).thenReturn(listOf(first, second))
            Mockito.`when`(mockDao.findByRequestCode("REQ-1")).thenReturn(first)
        }

        DocumentLocalRepository.setTestServiceRequestDaoProvider { mockDao }
        try {
            val doc = DocumentEntity(
                documentId = "doc-multi-active",
                documentType = "ASO",
                companyCnpj = "12.345.678/0001-91",
                title = "Multi active",
                payloadJson = "{}",
                contentHash = "hash",
                sourceRequestCode = "NON_EXISTENT"
            )

            runBlocking {
                DocumentLocalRepository.linkRequestForDocumentForTesting(
                    testDatabase = Mockito.mock(AppDatabase::class.java),
                    document = doc,
                    event = ServiceRequestLifecyclePolicy.DocumentEvent.DRAFT_SAVED
                )
            }

            // Expect that updateNotesAndStatus was invoked at least once
            runBlocking {
                Mockito.verify(mockDao, Mockito.atLeastOnce()).updateNotesAndStatus(Mockito.anyLong(), Mockito.anyString(), Mockito.anyString(), Mockito.anyLong())
            }
        } finally {
            DocumentLocalRepository.setTestServiceRequestDaoProvider(null)
        }
    }

    @Test
    fun explicit_request_code_preferred_over_fallback() {
        val mockDao = Mockito.mock(ServiceRequestDao::class.java)

        val explicit = ServiceRequestEntity(
            id = 10,
            requestCode = "REQ-EX",
            contractorName = "EX",
            contractorCnpj = "123",
            requestedDocumentType = "ASO",
            status = "OPEN"
        )

        runBlocking {
            Mockito.`when`(mockDao.findByRequestCode("REQ-EX")).thenReturn(explicit)
        }

        DocumentLocalRepository.setTestServiceRequestDaoProvider { mockDao }
        try {
            val doc = DocumentEntity(
                documentId = "doc-explicit",
                documentType = "ASO",
                companyCnpj = "12.345.678/0001-91",
                title = "Explicit",
                payloadJson = "{}",
                contentHash = "hash",
                sourceRequestCode = "REQ-EX"
            )

            runBlocking {
                DocumentLocalRepository.linkRequestForDocumentForTesting(
                    testDatabase = Mockito.mock(AppDatabase::class.java),
                    document = doc,
                    event = ServiceRequestLifecyclePolicy.DocumentEvent.DRAFT_SAVED
                )
            }

            // Should have used explicit path and updated notes/status on explicit request
            runBlocking {
                Mockito.verify(mockDao, Mockito.atLeastOnce()).findByRequestCode("REQ-EX")
                Mockito.verify(mockDao, Mockito.atLeastOnce()).updateNotesAndStatus(Mockito.anyLong(), Mockito.anyString(), Mockito.anyString(), Mockito.anyLong())
                // No fallback queries expected
                Mockito.verify(mockDao, Mockito.never()).listActiveByContractorCnpj(Mockito.anyString())
            }
        } finally {
            DocumentLocalRepository.setTestServiceRequestDaoProvider(null)
        }
    }
}

