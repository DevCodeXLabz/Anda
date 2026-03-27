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
    val companyScope: String? = null, // Optional company/CNPJ scope for safer cross-document prefill
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

    enum class FieldUpdateStatus {
        APPLIED,
        IGNORED_STALE
    }
    
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

    /** Reverse index for O(1) affected-count queries. */
    private val fieldToDocuments: Map<String, List<String>> = run {
        val index = mutableMapOf<String, MutableList<String>>()
        documentRegistry.forEach { (docType, fields) ->
            fields.forEach { fieldId ->
                index.getOrPut(fieldId) { mutableListOf() }.add(docType)
            }
        }
        index.mapValues { it.value.toList() }
    }
    
    /**
     * Cache of recently filled fields for cross-document updates
     */
    private val autofillCache = mutableMapOf<String, AutofillField>()

    /**
     * Company-scoped cache. Key is normalized company scope (e.g., CNPJ), value is field cache.
     */
    private val scopedAutofillCache = mutableMapOf<String, MutableMap<String, AutofillField>>()
    
    /**
     * Get all documents that should be updated when this field changes.
     * 
     * @param fieldId The field being modified (e.g., "company_cnpj")
     * @return List of document types that need this field
     */
    fun getAffectedDocuments(fieldId: String): List<String> {
        return fieldToDocuments[fieldId]?.toList() ?: emptyList()
    }

    /**
     * Fast count helper for UI messaging without allocating full lists repeatedly.
     */
    fun getAffectedDocumentsCount(fieldId: String): Int {
        return fieldToDocuments[fieldId]?.size ?: 0
    }
    
    /**
     * Register a field value change and suggest affected documents.
     * 
     * Call this whenever the user fills a field, and it returns:
     * - A list of "N documents will be updated"
     * - A confirmation prompt (optional)
     */
    suspend fun onFieldChanged(field: AutofillField, companyScope: String? = field.companyScope): List<String> = withContext(Dispatchers.Default) {
        // Store in company scope cache when scope is provided; otherwise store globally.
        val scoped = normalizeScope(companyScope)
        if (scoped != null) {
            val scopedMap = scopedAutofillCache.getOrPut(scoped) { mutableMapOf() }
            scopedMap[field.fieldId] = field.copy(companyScope = scoped)
        } else {
            autofillCache[field.fieldId] = field
        }
        
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
     * Batch variant of onFieldChanged for multi-field form saves.
     * Returns affected documents by field id.
     */
    suspend fun onFieldsChanged(
        fields: List<AutofillField>,
        companyScope: String? = null
    ): Map<String, List<String>> = withContext(Dispatchers.Default) {
        if (fields.isEmpty()) return@withContext emptyMap()

        val result = linkedMapOf<String, List<String>>()
        fields.forEach { field ->
            result[field.fieldId] = onFieldChanged(field, companyScope)
        }
        result
    }

    /**
     * Apply field update only if it is newer than cached value for same field/scope.
     */
    suspend fun onFieldChangedIfNewer(
        field: AutofillField,
        companyScope: String? = field.companyScope
    ): FieldUpdateStatus = withContext(Dispatchers.Default) {
        val scoped = normalizeScope(companyScope)
        val existing = if (scoped != null) {
            scopedAutofillCache[scoped]?.get(field.fieldId)
        } else {
            autofillCache[field.fieldId]
        }

        if (existing != null && field.timestamp <= existing.timestamp) {
            return@withContext FieldUpdateStatus.IGNORED_STALE
        }

        onFieldChanged(field, companyScope)
        FieldUpdateStatus.APPLIED
    }
    
    /**
     * Retrieve the most recent value for a field across all documents.
     * Used when initializing a form - if the field exists in cache, pre-fill it.
     * 
     * @param fieldId Field to retrieve
     * @return The most recent value, or null if never filled
     */
    fun getFieldValue(fieldId: String, companyScope: String? = null): String? {
        val scoped = normalizeScope(companyScope)
        if (scoped != null) {
            return scopedAutofillCache[scoped]?.get(fieldId)?.fieldValue ?: autofillCache[fieldId]?.fieldValue
        }
        return autofillCache[fieldId]?.fieldValue
    }
    
    /**
     * Batch update: Apply field values to a specific document form.
     * 
     * Example: User opens PCMSO form after filling ASO with company info.
     * This method populates the PCMSO form with cached company data.
     */
    suspend fun prefillDocument(documentType: String, companyScope: String? = null): Map<String, String> = withContext(Dispatchers.Default) {
        val fields = documentRegistry[documentType] ?: emptyList()
        val prefilled = mutableMapOf<String, String>()
        val scoped = normalizeScope(companyScope)
        val scopedValues = if (scoped != null) scopedAutofillCache[scoped] else null
        
        fields.forEach { fieldId ->
            (scopedValues?.get(fieldId)?.fieldValue ?: autofillCache[fieldId]?.fieldValue)?.let {
                prefilled[fieldId] = it
            }
        }
        
        prefilled
    }

    /**
     * Batch prefill for screens that prepare multiple documents in one flow.
     */
    suspend fun prefillDocuments(
        documentTypes: List<String>,
        companyScope: String? = null
    ): Map<String, Map<String, String>> = withContext(Dispatchers.Default) {
        if (documentTypes.isEmpty()) return@withContext emptyMap()

        val result = linkedMapOf<String, Map<String, String>>()
        documentTypes.forEach { documentType ->
            result[documentType] = prefillDocument(documentType, companyScope)
        }
        result
    }
    
    /**
     * Clear cache (e.g., when user starts a new project/company).
     */
    fun clearCache() {
        autofillCache.clear()
        scopedAutofillCache.clear()
        runCatching { android.util.Log.d("SmartAutofill", "Cache cleared") }
    }

    /**
     * Clear cache only for a specific company scope.
     */
    fun clearCacheForCompany(companyScope: String) {
        normalizeScope(companyScope)?.let { scopedAutofillCache.remove(it) }
    }
    
    /**
     * Export current autofill state (for debugging or audit).
     */
    fun exportState(): Map<String, AutofillField> {
        return autofillCache.toMap()
    }

    /**
     * Export scoped autofill state for diagnostics and tests.
     */
    fun exportStateForCompany(companyScope: String): Map<String, AutofillField> {
        val scope = normalizeScope(companyScope) ?: return emptyMap()
        return scopedAutofillCache[scope]?.toMap() ?: emptyMap()
    }

    private fun normalizeScope(companyScope: String?): String? {
        val trimmed = companyScope?.trim()
        return if (trimmed.isNullOrEmpty()) null else trimmed
    }
}

