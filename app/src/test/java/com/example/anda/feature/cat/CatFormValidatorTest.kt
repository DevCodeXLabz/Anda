package com.example.anda.feature.cat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatFormValidatorTest {

    @Test
    fun validate_returnsEmpty_whenAllRequiredFieldsAreValid() {
        val missing = CatFormValidator.validate(
            CatFormData(
                companyCnpj = "12.345.678/0001-90",
                companyName = "Empresa X",
                employeeName = "Joao",
                employeeCpf = "123.456.789-00",
                description = "Queda em nivel durante manutencao",
                reportedBy = "Tec. Carlos"
            )
        )

        assertTrue(missing.isEmpty())
    }

    @Test
    fun validate_returnsOrderedMissingFields() {
        val missing = CatFormValidator.validate(
            CatFormData(
                companyCnpj = "123",
                companyName = "",
                employeeName = "",
                employeeCpf = "",
                description = "",
                reportedBy = ""
            )
        )

        assertEquals(
            listOf(
                CatField.COMPANY_CNPJ,
                CatField.COMPANY_NAME,
                CatField.EMPLOYEE_NAME,
                CatField.EMPLOYEE_CPF,
                CatField.DESCRIPTION,
                CatField.REPORTED_BY
            ),
            missing
        )
    }

    @Test
    fun digitsOnly_removesMask() {
        assertEquals("12345678000190", CatFormValidator.digitsOnly("12.345.678/0001-90"))
    }
}

