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

    @Test
    fun scopedCache_keepsValuesIsolatedPerCompany() {
        runBlocking {
            val fieldA = AutofillField(
                fieldId = "company_name",
                fieldValue = "Empresa A",
                fieldType = AutofillFieldType.COMPANY_NAME,
                sourceDocument = "ASO"
            )
            val fieldB = AutofillField(
                fieldId = "company_name",
                fieldValue = "Empresa B",
                fieldType = AutofillFieldType.COMPANY_NAME,
                sourceDocument = "ASO"
            )

            service.onFieldChanged(fieldA, companyScope = "CNPJ_A")
            service.onFieldChanged(fieldB, companyScope = "CNPJ_B")

            assertEquals("Empresa A", service.getFieldValue("company_name", "CNPJ_A"))
            assertEquals("Empresa B", service.getFieldValue("company_name", "CNPJ_B"))
        }
    }

    @Test
    fun prefillDocument_scopedPrefersCompanyValueAndFallsBackToGlobal() {
        runBlocking {
            val globalCnpj = AutofillField(
                fieldId = "company_cnpj",
                fieldValue = "11.111.111/0001-11",
                fieldType = AutofillFieldType.COMPANY_CNPJ,
                sourceDocument = "ASO"
            )
            val scopedName = AutofillField(
                fieldId = "company_name",
                fieldValue = "Empresa Escopo",
                fieldType = AutofillFieldType.COMPANY_NAME,
                sourceDocument = "ASO"
            )

            service.onFieldChanged(globalCnpj)
            service.onFieldChanged(scopedName, companyScope = "CNPJ_X")

            val prefilled = service.prefillDocument("ASO", companyScope = "CNPJ_X")
            assertEquals("Empresa Escopo", prefilled["company_name"])
            assertEquals("11.111.111/0001-11", prefilled["company_cnpj"])
        }
    }

    @Test
    fun clearCacheForCompany_removesOnlyScopedValues() {
        runBlocking {
            val fieldA = AutofillField(
                fieldId = "company_name",
                fieldValue = "Empresa A",
                fieldType = AutofillFieldType.COMPANY_NAME,
                sourceDocument = "ASO"
            )
            val fieldB = AutofillField(
                fieldId = "company_name",
                fieldValue = "Empresa B",
                fieldType = AutofillFieldType.COMPANY_NAME,
                sourceDocument = "ASO"
            )

            service.onFieldChanged(fieldA, companyScope = "CNPJ_A")
            service.onFieldChanged(fieldB, companyScope = "CNPJ_B")
            service.clearCacheForCompany("CNPJ_A")

            assertEquals(null, service.getFieldValue("company_name", "CNPJ_A"))
            assertEquals("Empresa B", service.getFieldValue("company_name", "CNPJ_B"))
        }
    }

    @Test
    fun blankScope_behavesLikeGlobalCache() {
        runBlocking {
            val field = AutofillField(
                fieldId = "company_name",
                fieldValue = "Empresa Global",
                fieldType = AutofillFieldType.COMPANY_NAME,
                sourceDocument = "ASO"
            )

            service.onFieldChanged(field, companyScope = "   ")

            assertEquals("Empresa Global", service.getFieldValue("company_name"))
            assertEquals("Empresa Global", service.getFieldValue("company_name", "   "))
        }
    }

    @Test
    fun scopeNormalization_trimsKeysForReadAndClear() {
        runBlocking {
            val field = AutofillField(
                fieldId = "company_name",
                fieldValue = "Empresa Escopo",
                fieldType = AutofillFieldType.COMPANY_NAME,
                sourceDocument = "ASO"
            )

            service.onFieldChanged(field, companyScope = "  CNPJ_TRIM  ")
            assertEquals("Empresa Escopo", service.getFieldValue("company_name", "CNPJ_TRIM"))

            service.clearCacheForCompany("   CNPJ_TRIM   ")
            assertEquals(null, service.getFieldValue("company_name", "CNPJ_TRIM"))
        }
    }

    @Test
    fun getAffectedDocumentsCount_matchesListSize() {
        val list = service.getAffectedDocuments("company_cnpj")
        val count = service.getAffectedDocumentsCount("company_cnpj")

        assertEquals(list.size, count)
    }

    @Test
    fun onFieldsChanged_cachesAllAndReturnsAffectedMap() {
        runBlocking {
            val fields = listOf(
                AutofillField(
                    fieldId = "company_cnpj",
                    fieldValue = "22.222.222/0001-22",
                    fieldType = AutofillFieldType.COMPANY_CNPJ,
                    sourceDocument = "ASO"
                ),
                AutofillField(
                    fieldId = "company_name",
                    fieldValue = "Empresa Lote",
                    fieldType = AutofillFieldType.COMPANY_NAME,
                    sourceDocument = "ASO"
                )
            )

            val result = service.onFieldsChanged(fields)

            assertEquals(2, result.size)
            assertTrue((result["company_cnpj"] ?: emptyList()).contains("ASO"))
            assertEquals("22.222.222/0001-22", service.getFieldValue("company_cnpj"))
            assertEquals("Empresa Lote", service.getFieldValue("company_name"))
        }
    }

    @Test
    fun onFieldsChanged_scopedIsolationPreserved() {
        runBlocking {
            val fields = listOf(
                AutofillField(
                    fieldId = "company_name",
                    fieldValue = "Empresa Escopo Lote",
                    fieldType = AutofillFieldType.COMPANY_NAME,
                    sourceDocument = "ASO"
                )
            )

            service.onFieldsChanged(fields, companyScope = "CNPJ_BATCH")

            assertEquals("Empresa Escopo Lote", service.getFieldValue("company_name", "CNPJ_BATCH"))
            assertEquals(null, service.getFieldValue("company_name"))
        }
    }

    @Test
    fun prefillDocuments_returnsPerDocumentMaps() {
        runBlocking {
            service.onFieldChanged(
                AutofillField(
                    fieldId = "company_cnpj",
                    fieldValue = "33.333.333/0001-33",
                    fieldType = AutofillFieldType.COMPANY_CNPJ,
                    sourceDocument = "ASO"
                )
            )

            val prefilled = service.prefillDocuments(listOf("ASO", "PGR"))

            assertTrue(prefilled.containsKey("ASO"))
            assertTrue(prefilled.containsKey("PGR"))
            assertEquals("33.333.333/0001-33", prefilled["ASO"]?.get("company_cnpj"))
            assertEquals("33.333.333/0001-33", prefilled["PGR"]?.get("company_cnpj"))
        }
    }

    @Test
    fun prefillDocuments_unknownDocumentReturnsEmptyEntry() {
        runBlocking {
            val prefilled = service.prefillDocuments(listOf("UNKNOWN_DOC"))
            assertTrue(prefilled.containsKey("UNKNOWN_DOC"))
            assertTrue((prefilled["UNKNOWN_DOC"] ?: emptyMap()).isEmpty())
        }
    }

    @Test
    fun onFieldChangedIfNewer_ignoresOlderGlobal() {
        runBlocking {
            val newer = AutofillField(
                fieldId = "company_name",
                fieldValue = "Valor Novo",
                fieldType = AutofillFieldType.COMPANY_NAME,
                sourceDocument = "ASO",
                timestamp = 2000L
            )
            val older = AutofillField(
                fieldId = "company_name",
                fieldValue = "Valor Antigo",
                fieldType = AutofillFieldType.COMPANY_NAME,
                sourceDocument = "ASO",
                timestamp = 1000L
            )

            assertEquals(SmartAutofillService.FieldUpdateStatus.APPLIED, service.onFieldChangedIfNewer(newer))
            assertEquals(SmartAutofillService.FieldUpdateStatus.IGNORED_STALE, service.onFieldChangedIfNewer(older))
            assertEquals("Valor Novo", service.getFieldValue("company_name"))
        }
    }

    @Test
    fun onFieldChangedIfNewer_ignoresOlderScoped() {
        runBlocking {
            val newer = AutofillField(
                fieldId = "company_name",
                fieldValue = "Escopo Novo",
                fieldType = AutofillFieldType.COMPANY_NAME,
                sourceDocument = "ASO",
                timestamp = 3000L
            )
            val older = AutofillField(
                fieldId = "company_name",
                fieldValue = "Escopo Antigo",
                fieldType = AutofillFieldType.COMPANY_NAME,
                sourceDocument = "ASO",
                timestamp = 2500L
            )

            assertEquals(
                SmartAutofillService.FieldUpdateStatus.APPLIED,
                service.onFieldChangedIfNewer(newer, companyScope = "CNPJ_STALE")
            )
            assertEquals(
                SmartAutofillService.FieldUpdateStatus.IGNORED_STALE,
                service.onFieldChangedIfNewer(older, companyScope = "CNPJ_STALE")
            )
            assertEquals("Escopo Novo", service.getFieldValue("company_name", "CNPJ_STALE"))
        }
    }
}

