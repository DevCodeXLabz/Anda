package com.example.anda.core.stability

object DiagnosticsPrivacyFormatter {

    fun compactFatalForShareable(fatal: String?): String {
        if (fatal.isNullOrBlank()) return "(sem fatal)"
        val lines = fatal.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return "(sem fatal)"
        val keep = lines.take(3).joinToString("\n")
        return "$keep\nstack=(ocultado_privacidade)"
    }

    fun anonymizeOrigin(origin: String): String {
        val normalized = origin.trim()
        if (normalized.isBlank()) return "origem_indisponivel"
        val code = normalized.hashCode().toUInt().toString(16)
        return "origin-$code"
    }
}

