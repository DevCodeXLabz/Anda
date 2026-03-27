package com.example.anda.feature.ppp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PppFormValidatorTest {

    @Test
    fun validate_returnsEmpty_whenAllRequiredFieldsAreValid() {
        val missing = PppFormValidator.validate(
            PppFormData(
                cnpj = "12.345.678/0001-90",
                employeeName = "Joao Silva",
                admissionDate = "10/01/2025",
                responsibleName = "Tec. Ana"
            )
        )

        assertTrue(missing.isEmpty())
    }

    @Test
    fun validate_returnsOrderedMissingFields() {
        val missing = PppFormValidator.validate(
            PppFormData(cnpj = "123", employeeName = "", admissionDate = "", responsibleName = "")
        )

        assertEquals(
            listOf(
                PppField.COMPANY_CNPJ,
                PppField.EMPLOYEE_NAME,
                PppField.ADMISSION_DATE,
                PppField.RESPONSIBLE_NAME
            ),
            missing
        )
    }

    @Test
    fun digitsOnly_removesMask() {
        assertEquals("12345678000190", PppFormValidator.digitsOnly("12.345.678/0001-90"))
    }
}

