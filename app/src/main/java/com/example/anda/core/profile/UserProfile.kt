package com.example.anda.core.profile

enum class UserProfile(val storageValue: String) {
    TECHNICIAN("technician"),
    PRESTADORA("prestadora"),
    COMPANY("company"),
    CLINIC("clinic");

    companion object {
        fun fromStorage(value: String?): UserProfile? {
            val normalized = value?.trim()?.lowercase()
            return when (normalized) {
                PRESTADORA.storageValue,
                COMPANY.storageValue,
                CLINIC.storageValue -> PRESTADORA
                TECHNICIAN.storageValue -> TECHNICIAN
                else -> null
            }
        }
    }
}

