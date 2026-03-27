package com.example.anda.feature.apr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AprFormValidatorTest {

    @Test
    fun validate_returnsEmpty_whenAllRequiredFieldsAreValid() {
        val missing = AprFormValidator.validate(
            AprFormData(
                cnpj = "12.345.678/0001-90",
                taskName = "Manutencao de quadro eletrico",
                technician = "Tec. Carlos"
            )
        )

        assertTrue(missing.isEmpty())
    }

    @Test
    fun validate_returnsOrderedMissingFields() {
        val missing = AprFormValidator.validate(
            AprFormData(cnpj = "123", taskName = "", technician = "")
        )

        assertEquals(
            listOf(
                AprField.COMPANY_CNPJ,
                AprField.TASK_NAME,
                AprField.TECHNICIAN
            ),
            missing
        )
    }

    @Test
    fun digitsOnly_removesMask() {
        assertEquals("12345678000190", AprFormValidator.digitsOnly("12.345.678/0001-90"))
    }
}

