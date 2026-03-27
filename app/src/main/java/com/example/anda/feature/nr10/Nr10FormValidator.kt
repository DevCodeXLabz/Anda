package com.example.anda.feature.nr10


/**
 * NR-10 Audit Report = Electrical Installation Safety
 * Required: Company, installation type, identified hazards, recommendations
 * Compliance document for electrical work safety
 */
data class Nr10FormData(
    val cnpj: String = "",
    val installationType: String = "",  // Baixa tensão, Alta tensão, etc.
    val area: String = "",  // Description of electrical area
    val hazardsIdentified: String = "",  // List hazards found
    val nonConformities: Int = 0,  // Number of issues found
    val immediateActions: String = "",  // Urgent fixes needed
    val recommendations: String = "",
    val technician: String = "",
    val evaluationDate: String = ""
)

enum class Nr10Field {
    COMPANY_CNPJ,
    INSTALLATION_TYPE,
    AREA,
    HAZARDS_IDENTIFIED,
    NONCONFORMITIES,
    IMMEDIATE_ACTIONS,
    RECOMMENDATIONS,
    TECHNICIAN,
    EVALUATION_DATE
}

object Nr10FormValidator {
    
    private val MIN_CNPJ = 11
    private val MIN_TECH = 3
    private val MIN_DESC = 15
    
    fun validate(data: Nr10FormData): List<Nr10Field> {
        val missing = mutableListOf<Nr10Field>()
        
        if (digitsOnly(data.cnpj).length < MIN_CNPJ) {
            missing.add(Nr10Field.COMPANY_CNPJ)
        }
        
        if (data.installationType.isBlank()) {
            missing.add(Nr10Field.INSTALLATION_TYPE)
        }
        
        if (data.area.trim().length < MIN_DESC) {
            missing.add(Nr10Field.AREA)
        }
        
        if (data.hazardsIdentified.trim().length < MIN_DESC) {
            missing.add(Nr10Field.HAZARDS_IDENTIFIED)
        }
        
        if (data.immediateActions.isBlank()) {
            missing.add(Nr10Field.IMMEDIATE_ACTIONS)
        }
        
        if (data.recommendations.trim().length < MIN_DESC) {
            missing.add(Nr10Field.RECOMMENDATIONS)
        }
        
        if (data.technician.trim().length < MIN_TECH) {
            missing.add(Nr10Field.TECHNICIAN)
        }
        
        if (data.evaluationDate.isBlank()) {
            missing.add(Nr10Field.EVALUATION_DATE)
        }
        
        return missing
    }
    
    fun digitsOnly(value: String): String = value.filter { it.isDigit() }
}

