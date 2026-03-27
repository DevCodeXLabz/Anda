package com.example.anda.feature.aet


/**
 * AET = Análise Ergonômica do Trabalho
 * Required fields: Company CNPJ, Employee name/CPF, Job title, Work station analysis, Risk factors
 * NR-17 requirement for ergonomic assessments
 */
data class AetFormData(
    val cnpj: String = "",
    val employeeName: String = "",
    val employeeCpf: String = "",
    val jobTitle: String = "",
    val workstationDescription: String = "",
    val riskFactors: String = "",  // Inadequate posture, repetitive movement, etc.
    val recommendations: String = "",
    val severity: String = ""  // Baixa, Media, Alta
)

enum class AetField {
    COMPANY_CNPJ,
    EMPLOYEE_NAME,
    EMPLOYEE_CPF,
    JOB_TITLE,
    WORKSTATION_DESCRIPTION,
    RISK_FACTORS,
    RECOMMENDATIONS,
    SEVERITY
}

object AetFormValidator {
    
    private val MIN_CNPJ = 11
    private val MIN_NAME = 3
    private val MIN_DESCRIPTION = 20
    
    /**
     * Validates AET form. All fields mandatory for ergonomic analysis.
     */
    fun validate(data: AetFormData): List<AetField> {
        val missing = mutableListOf<AetField>()
        
        if (digitsOnly(data.cnpj).length < MIN_CNPJ) {
            missing.add(AetField.COMPANY_CNPJ)
        }
        
        if (data.employeeName.trim().length < MIN_NAME) {
            missing.add(AetField.EMPLOYEE_NAME)
        }
        
        if (!isValidCpf(data.employeeCpf)) {
            missing.add(AetField.EMPLOYEE_CPF)
        }
        
        if (data.jobTitle.trim().length < MIN_NAME) {
            missing.add(AetField.JOB_TITLE)
        }
        
        if (data.workstationDescription.trim().length < MIN_DESCRIPTION) {
            missing.add(AetField.WORKSTATION_DESCRIPTION)
        }
        
        if (data.riskFactors.trim().length < MIN_DESCRIPTION) {
            missing.add(AetField.RISK_FACTORS)
        }
        
        if (data.recommendations.trim().length < MIN_DESCRIPTION) {
            missing.add(AetField.RECOMMENDATIONS)
        }
        
        if (data.severity.isBlank()) {
            missing.add(AetField.SEVERITY)
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

