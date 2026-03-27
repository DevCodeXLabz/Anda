package com.example.anda.feature.cat

data class CatFormData(
    val companyCnpj: String,
    val companyName: String,
    val employeeName: String,
    val employeeCpf: String,
    val description: String,
    val reportedBy: String
)

enum class CatField {
    COMPANY_CNPJ,
    COMPANY_NAME,
    EMPLOYEE_NAME,
    EMPLOYEE_CPF,
    DESCRIPTION,
    REPORTED_BY
}

object CatFormValidator {

    fun validate(data: CatFormData): List<CatField> {
        val missing = mutableListOf<CatField>()
        if (digitsOnly(data.companyCnpj).length != 14) {
            missing += CatField.COMPANY_CNPJ
        }
        if (data.companyName.isBlank()) {
            missing += CatField.COMPANY_NAME
        }
        if (data.employeeName.isBlank()) {
            missing += CatField.EMPLOYEE_NAME
        }
        if (digitsOnly(data.employeeCpf).length != 11) {
            missing += CatField.EMPLOYEE_CPF
        }
        if (data.description.isBlank()) {
            missing += CatField.DESCRIPTION
        }
        if (data.reportedBy.isBlank()) {
            missing += CatField.REPORTED_BY
        }
        return missing
    }

    fun digitsOnly(value: String): String = value.filter { it.isDigit() }
}

