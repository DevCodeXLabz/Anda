package com.example.anda.feature.pt

import org.junit.Assert.assertTrue
import org.junit.Test

class PtFormValidatorTest {

    @Test
    fun validate_returnsEmpty_whenAllFieldsValid() {
        val missing = PtFormValidator.validate(
            PtFormData(
                cnpj = "12.345.678/0001-90",
                jobTitle = "Hot Work Technician",
                taskDescription = "Welding and cutting operations on pressure vessels",
                hazardIdentification = "Fire risk, exposure to extreme heat and metal fumes",
                preventiveMeasures = "Use fire watch, maintain 35-foot safety perimeter",
                ppeRequired = "Face shield, welding gloves, leather apron, steel-toed boots",
                emergencyProcedures = "Evacuate and call emergency services immediately",
                technician = "Roberto Silva",
                creationDate = "2026-03-26"
            )
        )
        assertTrue(missing.isEmpty())
    }
}

