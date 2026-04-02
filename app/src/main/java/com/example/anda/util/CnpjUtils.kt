package com.example.anda.util

object CnpjUtils {
    /**
     * Normalize a CNPJ to digits-only string (remove punctuation). If input is blank, returns empty string.
     */
    fun normalizeCnpj(input: String?): String {
        if (input == null) return ""
        return input.filter { it.isDigit() }
    }
}

