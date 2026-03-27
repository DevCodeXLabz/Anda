package com.example.anda.feature.ppp

data class PppFormData(
    val cnpj: String,
    val employeeName: String,
    val admissionDate: String,
    val responsibleName: String
)

enum class PppField {
    COMPANY_CNPJ,
    EMPLOYEE_NAME,
    ADMISSION_DATE,
    RESPONSIBLE_NAME
}

object PppFormValidator {

    fun validate(data: PppFormData): List<PppField> {
        val missing = mutableListOf<PppField>()
        if (digitsOnly(data.cnpj).length != 14) {
            missing += PppField.COMPANY_CNPJ
        }
        if (data.employeeName.isBlank()) {
            missing += PppField.EMPLOYEE_NAME
        }
        if (data.admissionDate.isBlank()) {
            missing += PppField.ADMISSION_DATE
        }
        if (data.responsibleName.isBlank()) {
            missing += PppField.RESPONSIBLE_NAME
        }
        return missing
    }

    fun digitsOnly(value: String): String = value.filter { it.isDigit() }
}

