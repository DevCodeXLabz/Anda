package com.example.anda.feature.nr10

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Nr10FormValidatorTest {

    @Test
    fun validate_returnsEmpty_whenAllFieldsValid() {
        val missing = Nr10FormValidator.validate(
            Nr10FormData(
                cnpj = "12.345.678/0001-90",
                installationType = "Alta tensão",
                area = "Electrical cabinet room with high voltage equipment",
                hazardsIdentified = "Risk of electrocution and arc flash incident",
                immediateActions = "Install warning signs and insulating barriers",
                recommendations = "Schedule monthly inspections and training",
                technician = "José Santos",
                evaluationDate = "2026-03-26"
            )
        )
        assertTrue(missing.isEmpty())
    }

    @Test
    fun validate_missingTechnician() {
        val missing = Nr10FormValidator.validate(
            Nr10FormData(
                cnpj = "12.345.678/0001-90",
                installationType = "Alta tensão",
                area = "Cabinet room",
                hazardsIdentified = "High voltage risk",
                immediateActions = "Install barriers",
                recommendations = "Monthly inspections",
                technician = "",
                evaluationDate = "2026-03-26"
            )
        )
        assertTrue(missing.contains(Nr10Field.TECHNICIAN))
    }
}

