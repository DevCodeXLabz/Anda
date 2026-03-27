package com.example.anda.feature.periculosidade


/**
 * PERICULOSIDADE = Hazard Bonus Assessment (NR-16)
 * Required: Hazard type (explosives, flammables, radioactivity), exposure verification
 * Determines if employee qualifies for hazard bonus
 */
data class PericulosidadeFormData(
    val cnpj: String = "",
    val employeeName: String = "",
    val employeeCpf: String = "",
    val jobTitle: String = "",
    val hazardType: String = "",  // Explosivos, Inflamáveis, Radiativo, etc.
    val exposureDescription: String = "",
    val frequencyOfExposure: String = "",  // Contínua, Intermitente, Ocasional
    val bonusEligibility: Boolean = false,
    val regulatoryBasis: String = ""  // NR-16 section reference
)

enum class PericulosidadeField {
    COMPANY_CNPJ,
    EMPLOYEE_NAME,
    EMPLOYEE_CPF,
    JOB_TITLE,
    HAZARD_TYPE,
    EXPOSURE_DESCRIPTION,
    FREQUENCY_OF_EXPOSURE,
    REGULATORY_BASIS
}

object PericulosidadeFormValidator {
    
    private val MIN_CNPJ = 11
    private val MIN_NAME = 3
    private val MIN_DESC = 15
    
    fun validate(data: PericulosidadeFormData): List<PericulosidadeField> {
        val missing = mutableListOf<PericulosidadeField>()
        
        if (digitsOnly(data.cnpj).length < MIN_CNPJ) {
            missing.add(PericulosidadeField.COMPANY_CNPJ)
        }
        
        if (data.employeeName.trim().length < MIN_NAME) {
            missing.add(PericulosidadeField.EMPLOYEE_NAME)
        }
        
        if (!isValidCpf(data.employeeCpf)) {
            missing.add(PericulosidadeField.EMPLOYEE_CPF)
        }
        
        if (data.jobTitle.trim().length < MIN_NAME) {
            missing.add(PericulosidadeField.JOB_TITLE)
        }
        
        if (data.hazardType.isBlank()) {
            missing.add(PericulosidadeField.HAZARD_TYPE)
        }
        
        if (data.exposureDescription.trim().length < MIN_DESC) {
            missing.add(PericulosidadeField.EXPOSURE_DESCRIPTION)
        }
        
        if (data.frequencyOfExposure.isBlank()) {
            missing.add(PericulosidadeField.FREQUENCY_OF_EXPOSURE)
        }
        
        if (data.regulatoryBasis.isBlank()) {
            missing.add(PericulosidadeField.REGULATORY_BASIS)
        }
        
        return missing
    }
    
    fun digitsOnly(value: String): String = value.filter { it.isDigit() }
    
    private fun isValidCpf(cpf: String): Boolean {
        if (cpf.isBlank()) return false
        val digits = digitsOnly(cpf)
        return digits.length == 11 && !isCpfAllSameDigit(digits)
    }
    
    private fun isCpfAllSameDigit(digits: String): Boolean {
        return digits.all { it == digits[0] }
    }
}

