package com.example.anda.feature.pgr
data class PgrFormData(
    val cnpj: String,
    val companyName: String,
    val technician: String
)
enum class PgrField {
    COMPANY_CNPJ,
    COMPANY_NAME,
    TECHNICIAN
}
object PgrFormValidator {
    fun validate(data: PgrFormData): List<PgrField> {
        val missing = mutableListOf<PgrField>()
        if (digitsOnly(data.cnpj).length != 14) {
            missing += PgrField.COMPANY_CNPJ
        }
        if (data.companyName.isBlank()) {
            missing += PgrField.COMPANY_NAME
        }
        if (data.technician.isBlank()) {
            missing += PgrField.TECHNICIAN
        }
        return missing
    }
    fun digitsOnly(value: String): String = value.filter { it.isDigit() }
}