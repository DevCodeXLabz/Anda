package com.example.anda.feature.nr12

import org.junit.Assert.assertTrue
import org.junit.Test

class Nr12FormValidatorTest {

    @Test
    fun validate_returnsEmpty_whenAllFieldsValid() {
        val missing = Nr12FormValidator.validate(
            Nr12FormData(
                cnpj = "12.345.678/0001-90",
                equipmentList = "Industrial press, hydraulic cutter, CNC milling machine",
                guardsInstalled = true,
                emergencyStopsQty = 5,
                hazardAnalysis = "Crushing hazards and entanglement risks identified",
                nonConformities = 2,
                recommendations = "Install presence-sensing safeguards on all presses",
                technician = "Paulo Costa",
                evaluationDate = "2026-03-26"
            )
        )
        assertTrue(missing.isEmpty())
    }
}

