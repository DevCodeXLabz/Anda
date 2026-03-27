package com.example.anda.feature.ltcat


/**
 * LTCAT = Laudo Técnico de Condições Ambientais do Trabalho
 * For INSS benefit assessment. Required: Company info, Employee, Environmental hazards
 * Used for retirement/disability benefits evaluation
 */
data class LtcatFormData(
    val cnpj: String = "",
    val employeeName: String = "",
    val employeeCpf: String = "",
    val jobTitle: String = "",
    val department: String = "",
    val hazardType: String = "",  // Ruído, Temperatura, Química, etc.
    val exposureTime: String = "",  // In hours per day
    val limitExceeded: Boolean = false,
    val measurements: String = "",
    val recommendations: String = ""
)

enum class LtcatField {
    COMPANY_CNPJ,
    EMPLOYEE_NAME,
    EMPLOYEE_CPF,
    JOB_TITLE,
    DEPARTMENT,
    HAZARD_TYPE,
    EXPOSURE_TIME,
    MEASUREMENTS,
    RECOMMENDATIONS
}

object LtcatFormValidator {
    
    private val MIN_CNPJ = 11
    private val MIN_NAME = 3
    private val MIN_HAZARD_DESC = 15
    
    /**
     * Validates LTCAT form for INSS submission.
     */
    fun validate(data: LtcatFormData): List<LtcatField> {
        val missing = mutableListOf<LtcatField>()
        
        if (digitsOnly(data.cnpj).length < MIN_CNPJ) {
            missing.add(LtcatField.COMPANY_CNPJ)
        }
        
        if (data.employeeName.trim().length < MIN_NAME) {
            missing.add(LtcatField.EMPLOYEE_NAME)
        }
        
        if (!isValidCpf(data.employeeCpf)) {
            missing.add(LtcatField.EMPLOYEE_CPF)
        }
        
        if (data.jobTitle.trim().length < MIN_NAME) {
            missing.add(LtcatField.JOB_TITLE)
        }
        
        if (data.department.trim().length < MIN_NAME) {
            missing.add(LtcatField.DEPARTMENT)
        }
        
        if (data.hazardType.isBlank()) {
            missing.add(LtcatField.HAZARD_TYPE)
        }
        
        if (data.exposureTime.isBlank() || data.exposureTime.toDoubleOrNull() == null) {
            missing.add(LtcatField.EXPOSURE_TIME)
        }
        
        if (data.measurements.trim().length < MIN_HAZARD_DESC) {
            missing.add(LtcatField.MEASUREMENTS)
        }
        
        if (data.recommendations.trim().length < MIN_HAZARD_DESC) {
            missing.add(LtcatField.RECOMMENDATIONS)
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

