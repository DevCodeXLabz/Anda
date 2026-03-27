package com.example.anda.feature.aso

data class AsoFormData(
    val companyCnpj: String,
    val companyName: String,
    val companyCnae: String,
    val employeeName: String,
    val employeeCpf: String,
    val doctorName: String,
    /** "APT", "INAPTO", "INAPTO TEMPORÁRIO", or blank when not yet selected */
    val conclusionResult: String = ""
)

enum class AsoField {
    COMPANY_CNPJ,
    COMPANY_NAME,
    COMPANY_CNAE,
    EMPLOYEE_NAME,
    EMPLOYEE_CPF,
    DOCTOR_NAME,
    CONCLUSION
}

object AsoFormValidator {

    fun validate(data: AsoFormData): List<AsoField> {
        val missing = mutableListOf<AsoField>()

        if (data.companyCnpj.isBlank() || digitsOnly(data.companyCnpj).length != 14) {
            missing += AsoField.COMPANY_CNPJ
        }
        if (data.companyName.isBlank()) {
            missing += AsoField.COMPANY_NAME
        }
        if (data.companyCnae.isBlank()) {
            missing += AsoField.COMPANY_CNAE
        }
        if (data.employeeName.isBlank()) {
            missing += AsoField.EMPLOYEE_NAME
        }
        if (digitsOnly(data.employeeCpf).length != 11) {
            missing += AsoField.EMPLOYEE_CPF
        }
        if (data.doctorName.isBlank()) {
            missing += AsoField.DOCTOR_NAME
        }
        if (data.conclusionResult.isBlank()) {
            missing += AsoField.CONCLUSION
        }

        return missing
    }

    fun digitsOnly(value: String): String = value.filter { it.isDigit() }
}

