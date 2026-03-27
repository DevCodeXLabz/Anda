package com.example.anda.data.integration

import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceIntegrationHelperNormalizationTest {

    @Test
    fun normalizeFieldValue_stripsDigitsForCnpjAndCpf() {
        val cnpj = ServiceIntegrationHelper.normalizeFieldValue(
            "company_cnpj",
            "12.345.678/0001-90"
        )
        val cpf = ServiceIntegrationHelper.normalizeFieldValue(
            "employee_cpf",
            "123.456.789-10"
        )

        assertEquals("12345678000190", cnpj)
        assertEquals("12345678910", cpf)
    }

    @Test
    fun normalizeFieldValue_trimsTextForNonNumericFields() {
        val companyName = ServiceIntegrationHelper.normalizeFieldValue(
            "company_name",
            "  Empresa Exemplo  "
        )
        val employeeName = ServiceIntegrationHelper.normalizeFieldValue(
            "employee_name",
            "  Maria da Silva  "
        )

        assertEquals("Empresa Exemplo", companyName)
        assertEquals("Maria da Silva", employeeName)
    }
}


