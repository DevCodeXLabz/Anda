package com.example.anda.feature.pgr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PgrFormValidatorTest {

    @Test
    fun validate_returnsEmpty_whenAllRequiredFieldsAreValid() {
        val missing = PgrFormValidator.validate(
            PgrFormData(
                cnpj = "12.345.678/0001-90",
                companyName = "Empresa Segura",
                technician = "Tec. Ana"
            )
        )

        assertTrue(missing.isEmpty())
    }

    @Test
    fun validate_returnsOrderedMissingFields() {
        val missing = PgrFormValidator.validate(
            PgrFormData(cnpj = "123", companyName = "", technician = "")
        )

        assertEquals(
            listOf(PgrField.COMPANY_CNPJ, PgrField.COMPANY_NAME, PgrField.TECHNICIAN),
            missing
        )
    }

    @Test
    fun digitsOnly_removesMask() {
        assertEquals("12345678000190", PgrFormValidator.digitsOnly("12.345.678/0001-90"))
    }
}

