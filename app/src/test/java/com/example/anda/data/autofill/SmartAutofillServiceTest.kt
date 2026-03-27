package com.example.anda.data.autofill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.runBlocking

class SmartAutofillServiceTest {

    private lateinit var service: SmartAutofillService

    @Before
    fun setup() {
        service = SmartAutofillService()
    }

    @Test
    fun getAffectedDocuments_companyCnpj_affectsAllDocuments() {
        val affected = service.getAffectedDocuments("company_cnpj")
        
        // CNPJ should affect all 13 documents
        assertEquals(13, affected.size)
        assertTrue(affected.contains("ASO"))
        assertTrue(affected.contains("PCMSO"))
        assertTrue(affected.contains("PGR"))
    }

    @Test
    fun getAffectedDocuments_hazardType_affectsMultipleDocuments() {
        val affected = service.getAffectedDocuments("hazard_type")
        
        // Hazard should affect: PCMSO, PGR, APR, NR10, NR12, NR20, LTCAT, AET, INSALUBRIDADE, PERICULOSIDADE
        assertTrue(affected.size >= 8)
        assertTrue(affected.contains("PCMSO"))
        assertTrue(affected.contains("PGR"))
    }

    @Test
    fun onFieldChanged_cachesFldValue() {
        runBlocking {
        val field = AutofillField(
            fieldId = "company_cnpj",
            fieldValue = "12.345.678/0001-90",
            fieldType = AutofillFieldType.COMPANY_CNPJ,
            sourceDocument = "ASO"
        )
        
        service.onFieldChanged(field)
        
        val retrieved = service.getFieldValue("company_cnpj")
        assertEquals("12.345.678/0001-90", retrieved)
        }
    }

    @Test
    fun prefillDocument_returnsOnlyCachedFields() {
        runBlocking {
        // Cache a company CNPJ
        val cnpjField = AutofillField(
            fieldId = "company_cnpj",
            fieldValue = "12.345.678/0001-90",
            fieldType = AutofillFieldType.COMPANY_CNPJ,
            sourceDocument = "ASO"
        )
        service.onFieldChanged(cnpjField)
        
        // Prefill PCMSO form
        val prefilled = service.prefillDocument("PCMSO")
        
        assertTrue(prefilled.containsKey("company_cnpj"))
        assertEquals("12.345.678/0001-90", prefilled["company_cnpj"])
        }
    }

    @Test
    fun clearCache_removesAllValues() {
        runBlocking {
        val field = AutofillField(
            fieldId = "company_cnpj",
            fieldValue = "12.345.678/0001-90",
            fieldType = AutofillFieldType.COMPANY_CNPJ,
            sourceDocument = "ASO"
        )
        service.onFieldChanged(field)
        
        service.clearCache()
        
        val state = service.exportState()
        assertTrue(state.isEmpty())
        }
    }
}

