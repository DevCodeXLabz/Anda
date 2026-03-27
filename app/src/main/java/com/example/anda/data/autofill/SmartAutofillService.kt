package com.example.anda.data.autofill

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SmartAutofill System
 * 
 * Purpose: When a user fills any field in ANY document, automatically populate
 * the same field in ALL other documents that use it. This dramatically speeds up
 * document creation, especially for companies with many employees.
 * 
 * Strategy: 
 * 1. Create a "Field Registry" that maps each field type to documents that use it
 * 2. When field changes, broadcast the change to all related documents
 * 3. Show user a summary: "Updating 7 documents with this company info..."
 * 4. Support undo per document if user doesn't want update
 */
data class AutofillField(
    val fieldId: String,           // e.g., "company_cnpj"
    val fieldValue: String,        // e.g., "12.345.678/0001-90"
    val fieldType: AutofillFieldType,  // COMPANY, EMPLOYEE, MEDICAL, etc.
    val sourceDocument: String,    // e.g., "ASO", "PCMSO", "PGR"
    val timestamp: Long = System.currentTimeMillis()
)

enum class AutofillFieldType {
    COMPANY_CNPJ,
    COMPANY_NAME,
    EMPLOYEE_CPF,
    EMPLOYEE_NAME,
    EMPLOYEE_DEPARTMENT,
    HAZARD_TYPE,
    MEDICAL_EXAMINATION,
    RISK_GRADE,
    REGULATION_NR
}

/**
 * Tracks which documents need which fields.
 */
data class DocumentFieldMap(
    val documentType: String,       // ASO, PCMSO, PGR, etc.
    val requiredFields: List<String>
)

class SmartAutofillService {
    
    // Registry of all document → field mappings
    private val documentRegistry = mapOf(
        "ASO" to listOf("company_cnpj", "company_name", "employee_cpf", "employee_name", "employee_department"),
        "PCMSO" to listOf("company_cnpj", "company_name", "employee_cpf", "employee_name", "hazard_type", "risk_grade"),
        "PPP" to listOf("company_cnpj", "employee_cpf", "employee_name", "job_title", "hazard_type"),
        "PGR" to listOf("company_cnpj", "company_name", "hazard_type", "risk_grade"),
        "APR" to listOf("company_cnpj", "company_name", "job_title", "hazard_type"),
        "CAT" to listOf("company_cnpj", "company_name", "employee_cpf", "employee_name"),
        "NR10" to listOf("company_cnpj", "hazard_type"),
        "NR12" to listOf("company_cnpj", "hazard_type"),
        "NR20" to listOf("company_cnpj", "hazard_type"),
        "LTCAT" to listOf("company_cnpj", "employee_cpf", "employee_name", "hazard_type"),
        "AET" to listOf("company_cnpj", "employee_cpf", "employee_name"),
        "INSALUBRIDADE" to listOf("company_cnpj", "employee_cpf", "employee_name", "hazard_type"),
        "PERICULOSIDADE" to listOf("company_cnpj", "employee_cpf", "employee_name", "hazard_type")
    )
    
    /**
     * Cache of recently filled fields for cross-document updates
     */
    private val autofillCache = mutableMapOf<String, AutofillField>()
    
    /**
     * Get all documents that should be updated when this field changes.
     * 
     * @param fieldId The field being modified (e.g., "company_cnpj")
     * @return List of document types that need this field
     */
    fun getAffectedDocuments(fieldId: String): List<String> {
        return documentRegistry.filterValues { fields ->
            fields.contains(fieldId)
        }.keys.toList()
    }
    
    /**
     * Register a field value change and suggest affected documents.
     * 
     * Call this whenever the user fills a field, and it returns:
     * - A list of "N documents will be updated"
     * - A confirmation prompt (optional)
     */
    suspend fun onFieldChanged(field: AutofillField): List<String> = withContext(Dispatchers.Default) {
        // Store in cache
        autofillCache[field.fieldId] = field
        
        // Get all affected documents
        val affected = getAffectedDocuments(field.fieldId)
        
        // Optional debug logging (safe for JVM unit tests)
        runCatching {
            android.util.Log.d(
                "SmartAutofill",
                "Field '${field.fieldId}' from ${field.sourceDocument} " +
                    "affects ${affected.size} documents: $affected"
            )
        }
        
        affected
    }
    
    /**
     * Retrieve the most recent value for a field across all documents.
     * Used when initializing a form - if the field exists in cache, pre-fill it.
     * 
     * @param fieldId Field to retrieve
     * @return The most recent value, or null if never filled
     */
    fun getFieldValue(fieldId: String): String? {
        return autofillCache[fieldId]?.fieldValue
    }
    
    /**
     * Batch update: Apply field values to a specific document form.
     * 
     * Example: User opens PCMSO form after filling ASO with company info.
     * This method populates the PCMSO form with cached company data.
     */
    suspend fun prefillDocument(documentType: String): Map<String, String> = withContext(Dispatchers.Default) {
        val fields = documentRegistry[documentType] ?: emptyList()
        val prefilled = mutableMapOf<String, String>()
        
        fields.forEach { fieldId ->
            autofillCache[fieldId]?.fieldValue?.let {
                prefilled[fieldId] = it
            }
        }
        
        prefilled
    }
    
    /**
     * Clear cache (e.g., when user starts a new project/company).
     */
    fun clearCache() {
        autofillCache.clear()
        runCatching { android.util.Log.d("SmartAutofill", "Cache cleared") }
    }
    
    /**
     * Export current autofill state (for debugging or audit).
     */
    fun exportState(): Map<String, AutofillField> {
        return autofillCache.toMap()
    }
}

