package com.example.anda.util

/**
 * Helpers related to document -> service request lookup strategies.
 */
object DocumentLookupUtils {

    /**
     * Returns an ordered list of candidate CNPJ values to try when performing lookups.
     * The list contains the original value first (if present) and then the digits-only
     * normalized form if it differs. Empty or null input returns an empty list.
     */
    fun candidateCnpjLookupValues(companyCnpj: String?): List<String> {
        if (companyCnpj.isNullOrBlank()) return emptyList()
        val trimmed = companyCnpj.trim()
        val normalized = CnpjUtils.normalizeCnpj(trimmed)
        return if (normalized == trimmed) listOf(trimmed) else listOf(trimmed, normalized)
    }
}

