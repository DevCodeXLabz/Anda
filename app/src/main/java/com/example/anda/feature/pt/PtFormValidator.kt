package com.example.anda.feature.pt


/**
 * PT = Plano de Trabalho (Work Plan)
 * Defines procedures, hazards, PPE requirements for specific tasks
 * Essential for high-risk operations
 */
data class PtFormData(
    val cnpj: String = "",
    val jobTitle: String = "",
    val taskDescription: String = "",
    val hazardIdentification: String = "",
    val preventiveMeasures: String = "",
    val ppeRequired: String = "",  // Personal protective equipment
    val emergencyProcedures: String = "",
    val technician: String = "",
    val creationDate: String = ""
)

enum class PtField {
    COMPANY_CNPJ,
    JOB_TITLE,
    TASK_DESCRIPTION,
    HAZARD_IDENTIFICATION,
    PREVENTIVE_MEASURES,
    PPE_REQUIRED,
    EMERGENCY_PROCEDURES,
    TECHNICIAN,
    CREATION_DATE
}

object PtFormValidator {
    
    private val MIN_CNPJ = 11
    private val MIN_TECH = 3
    private val MIN_DESC = 20
    
    fun validate(data: PtFormData): List<PtField> {
        val missing = mutableListOf<PtField>()
        
        if (digitsOnly(data.cnpj).length < MIN_CNPJ) {
            missing.add(PtField.COMPANY_CNPJ)
        }
        
        if (data.jobTitle.trim().length < 3) {
            missing.add(PtField.JOB_TITLE)
        }
        
        if (data.taskDescription.trim().length < MIN_DESC) {
            missing.add(PtField.TASK_DESCRIPTION)
        }
        
        if (data.hazardIdentification.trim().length < MIN_DESC) {
            missing.add(PtField.HAZARD_IDENTIFICATION)
        }
        
        if (data.preventiveMeasures.trim().length < MIN_DESC) {
            missing.add(PtField.PREVENTIVE_MEASURES)
        }
        
        if (data.ppeRequired.trim().length < MIN_DESC) {
            missing.add(PtField.PPE_REQUIRED)
        }
        
        if (data.emergencyProcedures.trim().length < MIN_DESC) {
            missing.add(PtField.EMERGENCY_PROCEDURES)
        }
        
        if (data.technician.trim().length < MIN_TECH) {
            missing.add(PtField.TECHNICIAN)
        }
        
        if (data.creationDate.isBlank()) {
            missing.add(PtField.CREATION_DATE)
        }
        
        return missing
    }
    
    fun digitsOnly(value: String): String = value.filter { it.isDigit() }
}

