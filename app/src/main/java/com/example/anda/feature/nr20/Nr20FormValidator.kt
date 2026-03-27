package com.example.anda.feature.nr20


/**
 * NR-20 Audit Report = Flammables & Combustibles Safety
 * Required: Substance inventory, storage conditions, hazard analysis
 * Critical for facilities handling flammable materials
 */
data class Nr20FormData(
    val cnpj: String = "",
    val substances: String = "",  // Flammable substances present
    val storageConditions: String = "",  // Temperature, containers, isolation
    val safetyDistance: String = "",  // Distance from occupied areas
    val hazardAnalysis: String = "",
    val fireSystemsPresent: Boolean = false,
    val recommendations: String = "",
    val technician: String = "",
    val evaluationDate: String = ""
)

enum class Nr20Field {
    COMPANY_CNPJ,
    SUBSTANCES,
    STORAGE_CONDITIONS,
    HAZARD_ANALYSIS,
    RECOMMENDATIONS,
    TECHNICIAN,
    EVALUATION_DATE
}

object Nr20FormValidator {
    
    private val MIN_CNPJ = 11
    private val MIN_TECH = 3
    private val MIN_DESC = 20
    
    fun validate(data: Nr20FormData): List<Nr20Field> {
        val missing = mutableListOf<Nr20Field>()
        
        if (digitsOnly(data.cnpj).length < MIN_CNPJ) {
            missing.add(Nr20Field.COMPANY_CNPJ)
        }
        
        if (data.substances.trim().length < MIN_DESC) {
            missing.add(Nr20Field.SUBSTANCES)
        }
        
        if (data.storageConditions.trim().length < MIN_DESC) {
            missing.add(Nr20Field.STORAGE_CONDITIONS)
        }
        
        if (data.hazardAnalysis.trim().length < MIN_DESC) {
            missing.add(Nr20Field.HAZARD_ANALYSIS)
        }
        
        if (data.recommendations.trim().length < MIN_DESC) {
            missing.add(Nr20Field.RECOMMENDATIONS)
        }
        
        if (data.technician.trim().length < MIN_TECH) {
            missing.add(Nr20Field.TECHNICIAN)
        }
        
        if (data.evaluationDate.isBlank()) {
            missing.add(Nr20Field.EVALUATION_DATE)
        }
        
        return missing
    }
    
    fun digitsOnly(value: String): String = value.filter { it.isDigit() }
}

