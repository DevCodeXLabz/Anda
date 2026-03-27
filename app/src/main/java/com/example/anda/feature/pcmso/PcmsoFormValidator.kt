package com.example.anda.feature.pcmso

data class PcmsoFormData(
    val cnpj: String,
    val doctorName: String,
    val programName: String = "",
    val hasAtLeastOneExam: Boolean = true
)

enum class PcmsoField {
    COMPANY_CNPJ,
    DOCTOR_NAME,
    PROGRAM_NAME,
    AT_LEAST_ONE_EXAM
}

object PcmsoFormValidator {

    fun validate(data: PcmsoFormData): List<PcmsoField> {
        val missing = mutableListOf<PcmsoField>()
        if (digitsOnly(data.cnpj).length != 14) missing += PcmsoField.COMPANY_CNPJ
        if (data.doctorName.isBlank()) missing += PcmsoField.DOCTOR_NAME
        if (data.programName.trim().length < 10) missing += PcmsoField.PROGRAM_NAME
        if (!data.hasAtLeastOneExam) missing += PcmsoField.AT_LEAST_ONE_EXAM
        return missing
    }

    fun digitsOnly(value: String): String = value.filter { it.isDigit() }
}
