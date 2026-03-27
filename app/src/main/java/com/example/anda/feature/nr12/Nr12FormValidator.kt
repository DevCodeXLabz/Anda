package com.example.anda.feature.nr12


/**
 * NR-12 Audit Report = Machinery & Equipment Safety
 * Required: Equipment list, guards, emergency stops, hazard analysis
 * Compliance for safe operation of machinery
 */
data class Nr12FormData(
    val cnpj: String = "",
    val equipmentList: String = "",  // Machines in the facility
    val guardsInstalled: Boolean = false,
    val emergencyStopsQty: Int = 0,
    val hazardAnalysis: String = "",
    val nonConformities: Int = 0,
    val recommendations: String = "",
    val technician: String = "",
    val evaluationDate: String = ""
)

enum class Nr12Field {
    COMPANY_CNPJ,
    EQUIPMENT_LIST,
    HAZARD_ANALYSIS,
    NONCONFORMITIES,
    RECOMMENDATIONS,
    TECHNICIAN,
    EVALUATION_DATE
}

object Nr12FormValidator {
    
    private val MIN_CNPJ = 11
    private val MIN_TECH = 3
    private val MIN_DESC = 20
    
    fun validate(data: Nr12FormData): List<Nr12Field> {
        val missing = mutableListOf<Nr12Field>()
        
        if (digitsOnly(data.cnpj).length < MIN_CNPJ) {
            missing.add(Nr12Field.COMPANY_CNPJ)
        }
        
        if (data.equipmentList.trim().length < MIN_DESC) {
            missing.add(Nr12Field.EQUIPMENT_LIST)
        }
        
        if (data.hazardAnalysis.trim().length < MIN_DESC) {
            missing.add(Nr12Field.HAZARD_ANALYSIS)
        }
        
        if (data.recommendations.trim().length < MIN_DESC) {
            missing.add(Nr12Field.RECOMMENDATIONS)
        }
        
        if (data.technician.trim().length < MIN_TECH) {
            missing.add(Nr12Field.TECHNICIAN)
        }
        
        if (data.evaluationDate.isBlank()) {
            missing.add(Nr12Field.EVALUATION_DATE)
        }
        
        return missing
    }
    
    fun digitsOnly(value: String): String = value.filter { it.isDigit() }
}

