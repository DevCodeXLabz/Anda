package com.example.anda.feature.periculosidade

import org.junit.Assert.assertTrue
import org.junit.Test

class PericulosidadeFormValidatorTest {

    @Test
    fun validate_returnsEmpty_whenAllFieldsValid() {
        val missing = PericulosidadeFormValidator.validate(
            PericulosidadeFormData(
                cnpj = "12.345.678/0001-90",
                employeeName = "Felipe Santos",
                employeeCpf = "123.456.789-10",
                jobTitle = "Explosives Handler",
                hazardType = "Explosivos",
                exposureDescription = "Handling dynamite and TNT in mining operations",
                frequencyOfExposure = "Contínua",
                bonusEligibility = true,
                regulatoryBasis = "NR-16, item 16.3"
            )
        )
        assertTrue(missing.isEmpty())
    }
}

