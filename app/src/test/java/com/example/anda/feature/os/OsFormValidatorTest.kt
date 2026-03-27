package com.example.anda.feature.os

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OsFormValidatorTest {

    @Test
    fun validate_returnsEmpty_whenAllRequiredFieldsAreValid() {
        val missing = OsFormValidator.validate(
            OsFormData(
                cnpj = "12.345.678/0001-90",
                employeeName = "Joao",
                activities = "Soldagem e montagem",
                responsible = "Eng. Carlos"
            )
        )

        assertTrue(missing.isEmpty())
    }

    @Test
    fun validate_returnsOrderedMissingFields() {
        val missing = OsFormValidator.validate(
            OsFormData(cnpj = "123", employeeName = "", activities = "", responsible = "")
        )

        assertEquals(
            listOf(
                OsField.COMPANY_CNPJ,
                OsField.EMPLOYEE_NAME,
                OsField.ACTIVITIES,
                OsField.RESPONSIBLE
            ),
            missing
        )
    }

    @Test
    fun digitsOnly_removesMask() {
        assertEquals("12345678000190", OsFormValidator.digitsOnly("12.345.678/0001-90"))
    }
}

