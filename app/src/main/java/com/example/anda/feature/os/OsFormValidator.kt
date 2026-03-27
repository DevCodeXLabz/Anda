package com.example.anda.feature.os

data class OsFormData(
    val cnpj: String,
    val employeeName: String,
    val activities: String,
    val responsible: String
)

enum class OsField {
    COMPANY_CNPJ,
    EMPLOYEE_NAME,
    ACTIVITIES,
    RESPONSIBLE
}

object OsFormValidator {

    fun validate(data: OsFormData): List<OsField> {
        val missing = mutableListOf<OsField>()
        if (digitsOnly(data.cnpj).length != 14) {
            missing += OsField.COMPANY_CNPJ
        }
        if (data.employeeName.isBlank()) {
            missing += OsField.EMPLOYEE_NAME
        }
        if (data.activities.isBlank()) {
            missing += OsField.ACTIVITIES
        }
        if (data.responsible.isBlank()) {
            missing += OsField.RESPONSIBLE
        }
        return missing
    }

    fun digitsOnly(value: String): String = value.filter { it.isDigit() }
}

