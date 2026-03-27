package com.example.anda.feature.pcmso

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmsoFormValidatorTest {

    @Test
    fun validate_returnsEmpty_whenCnpjAndDoctorAreValid() {
        val missing = PcmsoFormValidator.validate(
            PcmsoFormData(
                cnpj = "12.345.678/0001-90",
                doctorName = "Dra Ana",
                programName = "Programa de Controle Médico Ocupacional"  // ≥ 10 chars
            )
        )

        assertTrue(missing.isEmpty())
    }

    @Test
    fun validate_returnsOrderedMissingFields() {
        val missing = PcmsoFormValidator.validate(
            PcmsoFormData(cnpj = "123", doctorName = "")  // programName defaults to "" → also invalid
        )

        assertEquals(
            listOf(PcmsoField.COMPANY_CNPJ, PcmsoField.DOCTOR_NAME, PcmsoField.PROGRAM_NAME),
            missing
        )
    }

    @Test
    fun digitsOnly_removesMask() {
        assertEquals("12345678000190", PcmsoFormValidator.digitsOnly("12.345.678/0001-90"))
    }
}

