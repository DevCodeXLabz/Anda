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

    private val autofillService = SmartAutofillService()

    /**
     * Call this when a user changes a form field.
     * Automatically broadcasts the change to other documents that need it.
     */
    fun onFieldChanged(
        fieldId: String,
        value: String,
        sourceDocument: String
    ) {
        scope.launch {
            val field = AutofillField(
                fieldId = fieldId,
                fieldValue = value,
                fieldType = mapFieldIdToType(fieldId),
                sourceDocument = sourceDocument
            )

            val affected = autofillService.onFieldChanged(field)
            
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
     * Call this when a form is first loaded.
     * Pre-fills any fields that were previously entered in other documents.
     */
    fun prefillForm(
        documentType: String,
        fieldMap: Map<String, EditText>
    ) {
        scope.launch {
            val prefilled = autofillService.prefillDocument(documentType)
            
            // Apply prefilled values to EditText fields
            prefilled.forEach { (fieldId, value) ->
                fieldMap[fieldId]?.setText(value)
            }
        }
    }

    /**
     * Clear autofill cache (use when starting new project/company).
     */
    fun clearAutofill() {
        autofillService.clearCache()
    }

    /**
     * Export autofill state (for debugging).
     */
    fun exportAutofillState() = autofillService.exportState()

    private fun mapFieldIdToType(fieldId: String): AutofillFieldType {
        return when (fieldId) {
            "company_cnpj" -> AutofillFieldType.COMPANY_CNPJ
            "company_name" -> AutofillFieldType.COMPANY_NAME
            "employee_cpf" -> AutofillFieldType.EMPLOYEE_CPF
            "employee_name" -> AutofillFieldType.EMPLOYEE_NAME
            "employee_department" -> AutofillFieldType.EMPLOYEE_DEPARTMENT
            "hazard_type" -> AutofillFieldType.HAZARD_TYPE
            "medical_examination" -> AutofillFieldType.MEDICAL_EXAMINATION
            "risk_grade" -> AutofillFieldType.RISK_GRADE
            "regulation_nr" -> AutofillFieldType.REGULATION_NR
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
    sourceDocument: String
) {
    this.setOnFocusChangeListener { _, hasFocus ->
        if (!hasFocus && text.isNotEmpty()) {
            // Field lost focus and has value → report change
            helper.onFieldChanged(fieldId, text.toString(), sourceDocument)
        }
    }
}

