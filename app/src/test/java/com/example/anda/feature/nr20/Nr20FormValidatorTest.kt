package com.example.anda.feature.nr20

import org.junit.Assert.assertTrue
import org.junit.Test

class Nr20FormValidatorTest {

    @Test
    fun validate_returnsEmpty_whenAllFieldsValid() {
        val missing = Nr20FormValidator.validate(
            Nr20FormData(
                cnpj = "12.345.678/0001-90",
                substances = "Gasoline, diesel, acetone in storage tanks",
                storageConditions = "Temperature controlled at 15-25°C, isolated from ignition sources",
                safetyDistance = "50 meters from building",
                hazardAnalysis = "Fire risk if temperature exceeds safe limits",
                fireSystemsPresent = true,
                recommendations = "Install automatic fire suppression system",
                technician = "Ana Silva",
                evaluationDate = "2026-03-26"
            )
        )
        assertTrue(missing.isEmpty())
    }
}

