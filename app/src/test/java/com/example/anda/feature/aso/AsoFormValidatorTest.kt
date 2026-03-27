package com.example.anda.feature.aso

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AsoFormValidatorTest {

    @Test
    fun validate_returnsEmptyList_whenAllFieldsAreValid() {
        val data = AsoFormData(
            companyCnpj = "12.345.678/0001-90",
            companyName = "Empresa Segura",
            companyCnae = "43.21-5",
            employeeName = "Joao Silva",
            employeeCpf = "123.456.789-00",
            doctorName = "Dra Maria",
            conclusionResult = "APT"
        )

        val missing = AsoFormValidator.validate(data)

        assertTrue(missing.isEmpty())
    }

    @Test
    fun validate_returnsFieldsInExpectedOrder_whenDataIsMissing() {
        val data = AsoFormData(
            companyCnpj = "123",
            companyName = "",
            companyCnae = "",
            employeeName = "",
            employeeCpf = "",
            doctorName = "",
            conclusionResult = ""
        )

        val missing = AsoFormValidator.validate(data)

        assertEquals(
            listOf(
                AsoField.COMPANY_CNPJ,
                AsoField.COMPANY_NAME,
                AsoField.COMPANY_CNAE,
                AsoField.EMPLOYEE_NAME,
                AsoField.EMPLOYEE_CPF,
                AsoField.DOCTOR_NAME,
                AsoField.CONCLUSION
            ),
            missing
        )
    }

    @Test
    fun validate_acceptsFormattedCpfAndCnpj_whenDigitCountsAreValid() {
        val data = AsoFormData(
            companyCnpj = "12.345.678/0001-90",
            companyName = "Empresa Segura",
            companyCnae = "43.21-5",
            employeeName = "Joao Silva",
            employeeCpf = "123.456.789-00",
            doctorName = "Dra Maria",
            conclusionResult = "INAPTO"
        )

        val missing = AsoFormValidator.validate(data)

        assertTrue(missing.isEmpty())
    }

    @Test
    fun validate_requiresConclusionResult() {
        val data = AsoFormData(
            companyCnpj = "12.345.678/0001-90",
            companyName = "Empresa Segura",
            companyCnae = "43.21-5",
            employeeName = "Joao Silva",
            employeeCpf = "123.456.789-00",
            doctorName = "Dra Maria",
            conclusionResult = ""   // not selected
        )

        val missing = AsoFormValidator.validate(data)

        assertEquals(listOf(AsoField.CONCLUSION), missing)
    }

    @Test
    fun digitsOnly_removesMaskCharacters() {
        val cleaned = AsoFormValidator.digitsOnly("12.345.678/0001-90")

        assertEquals("12345678000190", cleaned)
    }
}

