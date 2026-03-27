package com.example.anda.feature.insalubridade

import org.junit.Assert.assertTrue
import org.junit.Test

class InsalubridadeFormValidatorTest {

    @Test
    fun validate_returnsEmpty_whenAllFieldsValid() {
        val missing = InsalubridadeFormValidator.validate(
            InsalubridadeFormData(
                cnpj = "12.345.678/0001-90",
                employeeName = "Carlos Oliveira",
                employeeCpf = "123.456.789-10",
                jobTitle = "Factory Operator",
                hazardType = "Ruído",
                measurementValue = 95.0,  // dB
                regulatoryLimit = 85.0,   // dB
                exposureTime = 8.0,        // hours/day
                limitExceeded = true,
                compensationDegree = "Máximo"
            )
        )
        assertTrue(missing.isEmpty())
    }

    @Test
    fun validate_requiresPositiveValues() {
        val missing = InsalubridadeFormValidator.validate(
            InsalubridadeFormData(
                cnpj = "12.345.678/0001-90",
                employeeName = "Test",
                employeeCpf = "123.456.789-10",
                jobTitle = "Worker",
                hazardType = "Ruído",
                measurementValue = 0.0,  // Invalid
                regulatoryLimit = 85.0,
                exposureTime = 8.0,
                compensationDegree = "Máximo"
            )
        )
        assertTrue(missing.contains(InsalubridadeField.MEASUREMENT_VALUE))
    }
}

