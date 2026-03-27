package com.example.anda.domain

import java.util.Locale

/**
 * Normalizes incoming document type aliases to canonical internal type ids.
 */
object DocumentTypeNormalizer {

    fun normalize(rawType: String): String {
        val upper = rawType.trim().uppercase(Locale.ROOT)
        val compact = upper
            .replace("-", "")
            .replace("_", "")
            .replace(" ", "")

        return when {
            compact == "LAUDONR10" || compact == "NR10" -> "LAUDO_NR10"
            compact == "LAUDONR12" || compact == "NR12" -> "LAUDO_NR12"
            compact == "LAUDONR20" || compact == "NR20" -> "LAUDO_NR20"
            compact == "MAPARISCO" || compact == "MAPARISCOS" -> "MAPA_RISCO"
            compact == "PLANOACAO" -> "PLANO_ACAO"
            compact == "CERTIFICADOEPI" -> "CERTIFICADO_EPI"
            else -> upper
        }
    }
}
