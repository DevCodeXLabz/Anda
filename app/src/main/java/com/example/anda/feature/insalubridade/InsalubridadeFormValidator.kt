package com.example.anda.feature.insalubridade


/**
 * INSALUBRIDADE = Hazard Bonus Assessment (NR-15)
 * Required: Environmental measurements, hazard type, exposure time, limits exceeded
 * Determines if employee is entitled to hazard bonus (add compensation)
 */
data class InsalubridadeFormData(
    val cnpj: String = "",
    val employeeName: String = "",
    val employeeCpf: String = "",
    val jobTitle: String = "",
    val hazardType: String = "",  // Ruído, Temperatura, Poeira, Química, Biológico
    val measurementValue: Double = 0.0,
    val regulatoryLimit: Double = 0.0,
    val exposureTime: Double = 0.0,  // hours per day
    val limitExceeded: Boolean = false,
    val compensationDegree: String = ""  // Minimo, Médio, Máximo
)

enum class InsalubridadeField {
    COMPANY_CNPJ,
    EMPLOYEE_NAME,
    EMPLOYEE_CPF,
    JOB_TITLE,
    HAZARD_TYPE,
    MEASUREMENT_VALUE,
    REGULATORY_LIMIT,
    EXPOSURE_TIME,
    COMPENSATION_DEGREE
}

object InsalubridadeFormValidator {
    
    private val MIN_CNPJ = 11
    private val MIN_NAME = 3
    
    fun validate(data: InsalubridadeFormData): List<InsalubridadeField> {
        val missing = mutableListOf<InsalubridadeField>()
        
        if (digitsOnly(data.cnpj).length < MIN_CNPJ) {
            missing.add(InsalubridadeField.COMPANY_CNPJ)
        }
        
        if (data.employeeName.trim().length < MIN_NAME) {
            missing.add(InsalubridadeField.EMPLOYEE_NAME)
        }
        
        if (!isValidCpf(data.employeeCpf)) {
            missing.add(InsalubridadeField.EMPLOYEE_CPF)
        }
        
        if (data.jobTitle.trim().length < MIN_NAME) {
            missing.add(InsalubridadeField.JOB_TITLE)
        }
        
        if (data.hazardType.isBlank()) {
            missing.add(InsalubridadeField.HAZARD_TYPE)
        }
        
        if (data.measurementValue <= 0) {
            missing.add(InsalubridadeField.MEASUREMENT_VALUE)
        }
        
        if (data.regulatoryLimit <= 0) {
            missing.add(InsalubridadeField.REGULATORY_LIMIT)
        }
        
        if (data.exposureTime <= 0) {
            missing.add(InsalubridadeField.EXPOSURE_TIME)
        }
        
        if (data.compensationDegree.isBlank()) {
            missing.add(InsalubridadeField.COMPENSATION_DEGREE)
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

