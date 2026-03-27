package com.example.anda.feature.apr

data class AprFormData(
    val cnpj: String,
    val taskName: String,
    val technician: String
)

enum class AprField {
    COMPANY_CNPJ,
    TASK_NAME,
    TECHNICIAN
}

object AprFormValidator {

    fun validate(data: AprFormData): List<AprField> {
        val missing = mutableListOf<AprField>()
        if (digitsOnly(data.cnpj).length != 14) {
            missing += AprField.COMPANY_CNPJ
        }
        if (data.taskName.isBlank()) {
            missing += AprField.TASK_NAME
        }
        if (data.technician.isBlank()) {
            missing += AprField.TECHNICIAN
        }
        return missing
    }

    fun digitsOnly(value: String): String = value.filter { it.isDigit() }
}

