package com.example.anda.data.integration

import android.content.Context
import android.widget.EditText
import com.example.anda.data.autofill.AutofillField
import com.example.anda.data.autofill.AutofillFieldType
import com.example.anda.data.autofill.SmartAutofillService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Integration Helper for Services
 *
 * Provides easy-to-use methods for Activities to integrate with:
 * - SmartAutofill
 * - Analytics
 * - Service Requests
 *
 * Usage in Activity:
 * ```kotlin
 * private val helper = ServiceIntegrationHelper(this, viewModelScope)
 * 
 * // On CNPJ field change:
 * binding.asoC npjInput.doAfterTextChanged { text ->
 *     helper.onFieldChanged("company_cnpj", text.toString(), "ASO")
 * }
 * 
 * // When form loads:
 * helper.prefillForm("PCMSO")
 * ```
 */
class ServiceIntegrationHelper(
    private val context: Context,
    private val scope: CoroutineScope
) {

    object FieldIds {
        const val COMPANY_CNPJ = "company_cnpj"
        const val COMPANY_NAME = "company_name"
        const val EMPLOYEE_CPF = "employee_cpf"
        const val EMPLOYEE_NAME = "employee_name"
        const val EMPLOYEE_DEPARTMENT = "employee_department"
        const val HAZARD_TYPE = "hazard_type"
        const val MEDICAL_EXAMINATION = "medical_examination"
        const val RISK_GRADE = "risk_grade"
        const val REGULATION_NR = "regulation_nr"
    }

    companion object {
        fun normalizeFieldValue(fieldId: String, value: String): String {
            return when (fieldId) {
                FieldIds.COMPANY_CNPJ, FieldIds.EMPLOYEE_CPF -> value.filter(Char::isDigit)
                else -> value.trim()
            }
        }
    }

    private val autofillService = SmartAutofillService()

    /**
     * Call this when a user changes a form field.
     * Automatically broadcasts the change to other documents that need it.
     */
    fun onFieldChanged(
        fieldId: String,
        value: String,
        sourceDocument: String,
        companyScope: String? = null
    ) {
        scope.launch {
            val field = AutofillField(
                fieldId = fieldId,
                fieldValue = value,
                fieldType = mapFieldIdToType(fieldId),
                sourceDocument = sourceDocument
            )

            val updateStatus = autofillService.onFieldChangedIfNewer(field, companyScope)
            val affected = if (updateStatus == SmartAutofillService.FieldUpdateStatus.APPLIED) {
                autofillService.getAffectedDocuments(fieldId)
            } else {
                emptyList()
            }

            if (affected.isNotEmpty() && affected.size > 1) {
                // Show user feedback (optional)
                android.util.Log.d(
                    "SmartAutofill",
                    "Field will update ${affected.size} documents"
                )
            }
        }
    }

    /**
     * Normalized single-field variant to keep behavior consistent across integrations.
     */
    fun onFieldChangedNormalized(
        fieldId: String,
        value: String,
        sourceDocument: String,
        companyScope: String? = null
    ) {
        val normalized = normalizeFieldValue(fieldId, value)
        if (normalized.isBlank()) return
        onFieldChanged(fieldId, normalized, sourceDocument, companyScope)
    }

    /**
     * Batch field update for forms that emit multiple related values together.
     */
    fun onFieldsChanged(
        fields: Map<String, String>,
        sourceDocument: String,
        companyScope: String? = null
    ) {
        if (fields.isEmpty()) return
        scope.launch {
            val autofillFields = fields.map { (fieldId, value) ->
                AutofillField(
                    fieldId = fieldId,
                    fieldValue = value,
                    fieldType = mapFieldIdToType(fieldId),
                    sourceDocument = sourceDocument
                )
            }

            var appliedCount = 0
            autofillFields.forEach { field ->
                val status = autofillService.onFieldChangedIfNewer(field, companyScope)
                if (status == SmartAutofillService.FieldUpdateStatus.APPLIED) {
                    appliedCount++
                }
            }

            if (appliedCount > 0) {
                android.util.Log.d(
                    "SmartAutofill",
                    "Batch update applied for $appliedCount/${autofillFields.size} fields"
                )
            }
        }
    }

    /**
     * Normalized batch variant used by form-level sync points.
     */
    fun onFieldsChangedNormalized(
        fields: Map<String, String>,
        sourceDocument: String,
        companyScope: String? = null
    ) {
        val normalized = fields.mapNotNull { (fieldId, value) ->
            val clean = normalizeFieldValue(fieldId, value)
            if (clean.isBlank()) null else fieldId to clean
        }.toMap()
        if (normalized.isEmpty()) return
        onFieldsChanged(normalized, sourceDocument, companyScope)
    }

    /**
     * Call this when a form is first loaded.
     * Pre-fills any fields that were previously entered in other documents.
     */
    fun prefillForm(
        documentType: String,
        fieldMap: Map<String, EditText>,
        companyScope: String? = null
    ) {
        scope.launch {
            val prefilled = autofillService.prefillDocument(documentType, companyScope)

            // Apply prefilled values to EditText fields
            prefilled.forEach { (fieldId, value) ->
                fieldMap[fieldId]?.setText(value)
            }
        }
    }

    /**
     * Safe prefill mode that only applies values to currently blank fields.
     * Useful after intent/company context is applied and we do not want to overwrite user input.
     */
    fun prefillFormIfBlank(
        documentType: String,
        fieldMap: Map<String, EditText>,
        companyScope: String? = null
    ) {
        scope.launch {
            val prefilled = autofillService.prefillDocument(documentType, companyScope)

            prefilled.forEach { (fieldId, value) ->
                val target = fieldMap[fieldId] ?: return@forEach
                if (target.text.isNullOrBlank()) {
                    target.setText(value)
                }
            }
        }
    }

    /**
     * Clear autofill cache (use when starting new project/company).
     */
    fun clearAutofill() {
        autofillService.clearCache()
    }

    fun clearAutofillForCompany(companyScope: String) {
        autofillService.clearCacheForCompany(companyScope)
    }

    /**
     * Export autofill state (for debugging).
     */
    fun exportAutofillState() = autofillService.exportState()

    private fun mapFieldIdToType(fieldId: String): AutofillFieldType {
        return when (fieldId) {
            FieldIds.COMPANY_CNPJ -> AutofillFieldType.COMPANY_CNPJ
            FieldIds.COMPANY_NAME -> AutofillFieldType.COMPANY_NAME
            FieldIds.EMPLOYEE_CPF -> AutofillFieldType.EMPLOYEE_CPF
            FieldIds.EMPLOYEE_NAME -> AutofillFieldType.EMPLOYEE_NAME
            FieldIds.EMPLOYEE_DEPARTMENT -> AutofillFieldType.EMPLOYEE_DEPARTMENT
            FieldIds.HAZARD_TYPE -> AutofillFieldType.HAZARD_TYPE
            FieldIds.MEDICAL_EXAMINATION -> AutofillFieldType.MEDICAL_EXAMINATION
            FieldIds.RISK_GRADE -> AutofillFieldType.RISK_GRADE
            FieldIds.REGULATION_NR -> AutofillFieldType.REGULATION_NR
            else -> AutofillFieldType.COMPANY_CNPJ  // Default
        }
    }
}

/**
 * Quick extension for EditText to enable SmartAutofill on field change.
 *
 * Usage:
 * ```kotlin
 * binding.asoC npjInput.setupSmartAutofill(helper, "company_cnpj", "ASO")
 * ```
 */
fun EditText.setupSmartAutofill(
    helper: ServiceIntegrationHelper,
    fieldId: String,
    sourceDocument: String,
    companyScopeProvider: (() -> String?)? = null
) {
    this.setOnFocusChangeListener { _, hasFocus ->
        if (!hasFocus && text.isNotEmpty()) {
            // Field lost focus and has value → report change
            helper.onFieldChangedNormalized(
                fieldId = fieldId,
                value = text.toString(),
                sourceDocument = sourceDocument,
                companyScope = companyScopeProvider?.invoke()
            )
        }
    }
}

