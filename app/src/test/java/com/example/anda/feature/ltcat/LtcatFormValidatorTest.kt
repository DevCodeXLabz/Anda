package com.example.anda.feature.ltcat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LtcatFormValidatorTest {

    @Test
    fun validate_returnsEmpty_whenAllFieldsValid() {
        val missing = LtcatFormValidator.validate(
            LtcatFormData(
                cnpj = "12.345.678/0001-90",
                employeeName = "Maria Silva",
                employeeCpf = "123.456.789-10",
                jobTitle = "Supervisor",
                department = "Production",
                hazardType = "Ruído",
                exposureTime = "8",
                measurements = "Noise level measured at 95 dB exceeds limit",
                recommendations = "Use hearing protection and reduce exposure time"
            )
        )
        assertTrue(missing.isEmpty())
    }

    @Test
    fun validate_requiresValidCpf() {
        val missing = LtcatFormValidator.validate(
            LtcatFormData(
                cnpj = "12.345.678/0001-90",
                employeeCpf = "000.000.000-00"  // Invalid
            )
        )
        assertTrue(missing.contains(LtcatField.EMPLOYEE_CPF))
    }
}

