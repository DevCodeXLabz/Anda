package com.example.anda.feature.aet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AetFormValidatorTest {

    @Test
    fun validate_returnsEmpty_whenAllFieldsValid() {
        val missing = AetFormValidator.validate(
            AetFormData(
                cnpj = "12.345.678/0001-90",
                employeeName = "João da Silva",
                employeeCpf = "123.456.789-10",
                jobTitle = "Operador",
                workstationDescription = "Workstation located at assembly line with repetitive movement tasks",
                riskFactors = "Inadequate postural alignment causing muscle strain",
                recommendations = "Install ergonomic support equipment and rotate tasks",
                severity = "Media"
            )
        )
        assertTrue(missing.isEmpty())
    }

    @Test
    fun validate_returnsMissingFields_whenRequiredFieldsBlank() {
        val missing = AetFormValidator.validate(
            AetFormData(cnpj = "", employeeName = "", employeeCpf = "")
        )
        assertTrue(missing.isNotEmpty())
    }

    @Test
    fun validate_returnsMissingInOrder() {
        val missing = AetFormValidator.validate(AetFormData())
        assertEquals(AetField.COMPANY_CNPJ, missing.first())
    }
}

