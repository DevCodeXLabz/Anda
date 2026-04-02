package com.example.anda.data.repository

import com.example.anda.data.local.AppDatabase
import com.example.anda.data.local.dao.ServiceRequestDao
import com.example.anda.data.local.entity.DocumentEntity
import com.example.anda.data.local.entity.ServiceRequestEntity
import com.example.anda.data.requests.ServiceRequestLifecyclePolicy
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mockito.Mockito

class DocumentLocalRepositoryFallbackAdditionalTest {

    @Test
    fun explicitLookupThrows_fallsBackToCnpjAndUpdatesFound() {
        val mockDao = Mockito.mock(ServiceRequestDao::class.java)

        // explicit lookup will throw
        runBlocking {
            Mockito.`when`(mockDao.findByRequestCode("EX-ERR")).thenThrow(RuntimeException("boom"))
        }

        // fallback candidate returns one active request
        val fallback = ServiceRequestEntity(
            id = 42,
            requestCode = "FB-1",
            contractorName = "FB",
            contractorCnpj = "123",
            requestedDocumentType = "ASO",
            status = "OPEN"
        )

        runBlocking {
            Mockito.`when`(mockDao.listActiveByContractorCnpj("12.345.678/0001-91")).thenReturn(listOf(fallback))
            Mockito.`when`(mockDao.findByRequestCode("FB-1")).thenReturn(fallback)
        }

        DocumentLocalRepository.setTestServiceRequestDaoProvider { mockDao }
        try {
            val doc = DocumentEntity(
                documentId = "doc-err-explicit",
                documentType = "ASO",
                companyCnpj = "12.345.678/0001-91",
                title = "ExplicitErr",
                payloadJson = "{}",
                contentHash = "h",
                sourceRequestCode = "EX-ERR"
            )

            runBlocking {
                DocumentLocalRepository.linkRequestForDocumentForTesting(
                    testDatabase = Mockito.mock(AppDatabase::class.java),
                    document = doc,
                    event = ServiceRequestLifecyclePolicy.DocumentEvent.DRAFT_SAVED
                )
            }

            runBlocking {
                Mockito.verify(mockDao, Mockito.atLeastOnce()).listActiveByContractorCnpj("12.345.678/0001-91")
                Mockito.verify(mockDao, Mockito.atLeastOnce()).updateNotesAndStatus(Mockito.eq(42L), Mockito.anyString(), Mockito.anyString(), Mockito.anyLong())
            }
        } finally {
            DocumentLocalRepository.setTestServiceRequestDaoProvider(null)
        }
    }

    @Test
    fun candidateLookup_tries_formatted_then_normalized() {
        val mockDao = Mockito.mock(ServiceRequestDao::class.java)

        // first candidate (formatted) returns empty, second (normalized) returns one
        val found = ServiceRequestEntity(
            id = 77,
            requestCode = "NORM-1",
            contractorName = "Norm",
            contractorCnpj = "12345678000191",
            requestedDocumentType = "ASO",
            status = "OPEN"
        )

        runBlocking {
            Mockito.`when`(mockDao.listActiveByContractorCnpj("12.345.678/0001-91")).thenReturn(emptyList())
            Mockito.`when`(mockDao.listActiveByContractorCnpj("12345678000191")).thenReturn(listOf(found))
            Mockito.`when`(mockDao.findByRequestCode("NORM-1")).thenReturn(found)
        }

        DocumentLocalRepository.setTestServiceRequestDaoProvider { mockDao }
        try {
            val doc = DocumentEntity(
                documentId = "doc-order",
                documentType = "ASO",
                companyCnpj = "12.345.678/0001-91",
                title = "Order",
                payloadJson = "{}",
                contentHash = "h"
            )

            runBlocking {
                DocumentLocalRepository.linkRequestForDocumentForTesting(
                    testDatabase = Mockito.mock(AppDatabase::class.java),
                    document = doc,
                    event = ServiceRequestLifecyclePolicy.DocumentEvent.DRAFT_SAVED
                )
            }

            // Verify order: first called with formatted, then normalized
            val inOrder = Mockito.inOrder(mockDao)
            runBlocking {
                inOrder.verify(mockDao).listActiveByContractorCnpj("12.345.678/0001-91")
                inOrder.verify(mockDao).listActiveByContractorCnpj("12345678000191")
            }

            runBlocking {
                Mockito.verify(mockDao, Mockito.atLeastOnce()).updateNotesAndStatus(Mockito.eq(77L), Mockito.anyString(), Mockito.anyString(), Mockito.anyLong())
            }
        } finally {
            DocumentLocalRepository.setTestServiceRequestDaoProvider(null)
        }
    }

    @Test
    fun daoThrowsDuringCandidateLookup_continuesToNextCandidate() {
        val mockDao = Mockito.mock(ServiceRequestDao::class.java)

        // first candidate will throw, second will return a result
        val good = ServiceRequestEntity(
            id = 99,
            requestCode = "GOOD-1",
            contractorName = "G",
            contractorCnpj = "12345678000191",
            requestedDocumentType = "ASO",
            status = "OPEN"
        )

        runBlocking {
            Mockito.`when`(mockDao.listActiveByContractorCnpj("12.345.678/0001-91")).thenThrow(RuntimeException("boom candidate"))
            Mockito.`when`(mockDao.listActiveByContractorCnpj("12345678000191")).thenReturn(listOf(good))
            Mockito.`when`(mockDao.findByRequestCode("GOOD-1")).thenReturn(good)
        }

        DocumentLocalRepository.setTestServiceRequestDaoProvider { mockDao }
        try {
            val doc = DocumentEntity(
                documentId = "doc-exc",
                documentType = "ASO",
                companyCnpj = "12.345.678/0001-91",
                title = "ExceptionCandidate",
                payloadJson = "{}",
                contentHash = "h"
            )

            runBlocking {
                DocumentLocalRepository.linkRequestForDocumentForTesting(
                    testDatabase = Mockito.mock(AppDatabase::class.java),
                    document = doc,
                    event = ServiceRequestLifecyclePolicy.DocumentEvent.DRAFT_SAVED
                )
            }

            runBlocking {
                Mockito.verify(mockDao, Mockito.atLeastOnce()).listActiveByContractorCnpj("12.345.678/0001-91")
                Mockito.verify(mockDao, Mockito.atLeastOnce()).listActiveByContractorCnpj("12345678000191")
                Mockito.verify(mockDao, Mockito.atLeastOnce()).updateNotesAndStatus(Mockito.eq(99L), Mockito.anyString(), Mockito.anyString(), Mockito.anyLong())
            }
        } finally {
            DocumentLocalRepository.setTestServiceRequestDaoProvider(null)
        }
    }
}

