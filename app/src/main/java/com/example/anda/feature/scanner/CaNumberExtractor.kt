package com.example.anda.feature.scanner

object CaNumberExtractor {

    // Aceita formatos: CA12345, C.A. 12345, C A:12345
    private val caRegex = Regex("""\bC\s*\.?\s*A\s*\.?\s*[:\-]?\s*(\d{3,8})\b""", RegexOption.IGNORE_CASE)

    fun extract(text: String): String? {
        if (text.isBlank()) return null
        return caRegex.find(text)?.groupValues?.getOrNull(1)
    }
}

