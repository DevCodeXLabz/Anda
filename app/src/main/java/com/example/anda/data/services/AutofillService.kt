package com.example.anda.data.services

import com.example.anda.domain.CompanyProfile
import com.example.anda.domain.EmployeeExaminationType
import com.example.anda.domain.EmployeeProfile
import com.example.anda.domain.OccupationalHealthCertificate
import com.example.anda.domain.OccupationalRisk
import com.example.anda.domain.RiskLevel

class AutofillService {

    data class PreviousAsoContext(
        val clinicName: String? = null,
        val clinicCnpj: String? = null,
        val doctorName: String? = null,
        val doctorCrm: String? = null
    )

    data class PreviousDocumentContext(
        val responsibleName: String? = null,
        val professionalRegistry: String? = null,
        val clinicName: String? = null,
        val clinicCnpj: String? = null,
        val reportedBy: String? = null
    )

    data class PreviousGenericContext(
        val objective: String? = null,
        val scope: String? = null,
        val technicalDetails: String? = null,
        val recommendations: String? = null,
        val responsibleName: String? = null
    )

    fun createAsoDraft(
        company: CompanyProfile,
        employee: EmployeeProfile,
        doctorName: String,
        doctorCrm: String,
        clinicName: String,
        clinicCnpj: String,
        examinationType: EmployeeExaminationType = EmployeeExaminationType.ADMISSION,
        examinationResult: String = "APT",
        restrictions: String = "",
        complementaryExams: String = "",
        observations: String = ""
    ): OccupationalHealthCertificate {
        val now = System.currentTimeMillis()
        val detectedRisks = mapRisksByCnae(company.cnae)
        val examsList = complementaryExams
            .split("\n", ",", ";")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return OccupationalHealthCertificate(
            id = "aso-$now",
            documentNumber = "ASO-${now.toString().takeLast(6)}",
            companyProfile = company,
            employee = employee.copy(riskExposures = detectedRisks),
            examinationType = examinationType,
            examinationDate = now,
            occupationalDoctor = doctorName,
            occupationalDoctorCRM = doctorCrm,
            clinicName = clinicName,
            clinicCNPJ = clinicCnpj,
            examinationResults = examinationResult.uppercase().ifBlank { "APT" },
            restrictions = restrictions.trim().ifBlank { null },
            complementaryExams = examsList,
            observations = observations.trim().ifBlank { null },
            validUntil = now + ONE_YEAR_MS,
            generatedBy = doctorName
        )
    }

    fun detectRisksByCnae(cnae: String): List<OccupationalRisk> = mapRisksByCnae(cnae)

    private fun mapRisksByCnae(cnae: String): List<OccupationalRisk> {
        val prefix = cnae.take(2)
        return when (prefix) {
            "35" -> listOf(
                OccupationalRisk(
                    name = "Energia eletrica",
                    description = "Exposicao a choque eletrico",
                    level = RiskLevel.CRITICAL,
                    normativeReference = "NR-10",
                    controlMeasures = listOf("Bloqueio e etiquetagem", "EPI isolante"),
                    epiRequired = listOf("Luva isolante", "Capacete")
                )
            )
            "41", "42", "43" -> listOf(
                OccupationalRisk(
                    name = "Trabalho em altura",
                    description = "Risco de queda",
                    level = RiskLevel.CRITICAL,
                    normativeReference = "NR-35",
                    controlMeasures = listOf("Linha de vida", "Treinamento NR-35"),
                    epiRequired = listOf("Cinturao paraquedista", "Capacete")
                )
            )
            else -> listOf(
                OccupationalRisk(
                    name = "Risco ocupacional geral",
                    description = "Risco padrao para CNAE $cnae",
                    level = RiskLevel.LOW,
                    normativeReference = "NR-1",
                    controlMeasures = listOf("Treinamento de seguranca")
                )
            )
        }
    }

    fun extractPreviousAsoContextFromHtml(payload: String): PreviousAsoContext {
        fun extract(vararg labelPatterns: String): String? {
            val combined = labelPatterns.joinToString("|")
            val options = setOf(RegexOption.IGNORE_CASE)
            val strongPattern = Regex("<strong>\\s*($combined)\\s*:</strong>\\s*([^<]+)", options)
            val plainPattern = Regex("(?:^|\\n|\\r)\\s*($combined)\\s*:\\s*([^\\n\\r<]+)", options)
            return strongPattern.find(payload)?.groupValues?.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() }
                ?: plainPattern.find(payload)?.groupValues?.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() }
        }

        return PreviousAsoContext(
            clinicName = extract("Cl[ií]nica"),
            clinicCnpj = extract("CNPJ")?.filter { it.isDigit() }?.takeIf { it.isNotBlank() },
            doctorName = extract("M[eé]dico"),
            doctorCrm = extract("CRM")
        )
    }

    fun extractPreviousDocumentContext(payload: String, signedBy: String? = null): PreviousDocumentContext {
        fun extract(vararg labelPatterns: String): String? {
            val combined = labelPatterns.joinToString("|")
            val options = setOf(RegexOption.IGNORE_CASE)
            val strongPattern = Regex("<strong>\\s*($combined)\\s*:</strong>\\s*([^<]+)", options)
            val plainPattern = Regex("(?:^|\\n|\\r)\\s*($combined)\\s*:\\s*([^\\n\\r<]+)", options)
            return strongPattern.find(payload)?.groupValues?.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() }
                ?: plainPattern.find(payload)?.groupValues?.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() }
        }

        val responsibleRaw = extract(
            "Respons[aá]vel T[eé]cnico",
            "Respons[aá]vel",
            "M[eé]dico Coordenador",
            "M[eé]dico",
            "Respons[aá]vel pela Emiss[aã]o",
            "Emitente"
        )
        val responsible = sanitizeResponsibleName(responsibleRaw)

        val reporter = extract("Respons[aá]vel pela Emiss[aã]o", "Emitente", "Reported By")
        val inlineRegistry = extractInlineRegistry(responsibleRaw)

        return PreviousDocumentContext(
            responsibleName = signedBy?.trim()?.takeIf { it.isNotBlank() } ?: responsible,
            professionalRegistry = extract("CRM", "CREA", "Registro Profissional") ?: inlineRegistry,
            clinicName = extract("Cl[ií]nica"),
            clinicCnpj = extract("CNPJ")?.filter { it.isDigit() }?.takeIf { it.isNotBlank() },
            reportedBy = reporter ?: signedBy?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    private fun sanitizeResponsibleName(value: String?): String? {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return null
        return raw
            .split('|')
            .firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun extractInlineRegistry(value: String?): String? {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return null
        val pattern = Regex("(CRM|CREA)\\s*:?\\s*([A-Za-z0-9./-]+)", RegexOption.IGNORE_CASE)
        val match = pattern.find(raw) ?: return null
        val kind = match.groupValues.getOrNull(1)?.uppercase() ?: return null
        val number = match.groupValues.getOrNull(2)?.trim().orEmpty()
        if (number.isBlank()) return null
        return "$kind: $number"
    }

    fun extractPreviousGenericContextFromHtml(payload: String, signedBy: String? = null): PreviousGenericContext {
        fun stripHtml(value: String): String {
            return value
                .replace("<br\\s*/?>".toRegex(RegexOption.IGNORE_CASE), "\n")
                .replace("<[^>]+>".toRegex(), " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("\\s+".toRegex(), " ")
                .trim()
        }

        fun captureSection(sectionNumber: String, sectionTitle: String): String? {
            val pattern = Regex(
                "<h3>\\s*$sectionNumber\\.\\s*$sectionTitle\\s*</h3>\\s*<div[^>]*>(.*?)</div>",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
            return pattern.find(payload)
                ?.groupValues
                ?.getOrNull(1)
                ?.let(::stripHtml)
                ?.takeIf { it.isNotBlank() && !it.equals("Não informado", ignoreCase = true) && !it.equals("Nao informado", ignoreCase = true) }
        }

        fun captureFirstRecommendation(): String? {
            val rowPattern = Regex(
                "<section[^>]*>\\s*<h3>\\s*6\\.\\s*RECOMENDAÇÕES E PLANO DE AÇÃO\\s*</h3>.*?<tbody>\\s*<tr>(.*?)</tr>",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
            val actionCellPattern = Regex(
                "<td[^>]*>\\s*\\d+\\s*</td>\\s*<td[^>]*>(.*?)</td>",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
            val row = rowPattern.find(payload)?.groupValues?.getOrNull(1) ?: return null
            return actionCellPattern.find(row)
                ?.groupValues
                ?.getOrNull(1)
                ?.let(::stripHtml)
                ?.takeIf { it.isNotBlank() && !it.equals("Não informado", ignoreCase = true) && !it.equals("Nao informado", ignoreCase = true) }
        }

        return PreviousGenericContext(
            objective = captureSection("2", "OBJETIVO"),
            scope = captureSection("3", "ESCOPO"),
            technicalDetails = captureSection("5", "DESENVOLVIMENTO T[ÉE]CNICO"),
            recommendations = captureFirstRecommendation(),
            responsibleName = signedBy?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    companion object {
        private const val ONE_YEAR_MS = 365L * 24L * 60L * 60L * 1000L
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PCMSO – exam and schedule suggestions based on risk level
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns the predominant risk level for a given CNAE code prefix.
     */
    fun detectRiskLevelByCnae(cnae: String): RiskLevel {
        val risks = mapRisksByCnae(cnae)
        return risks.maxByOrNull { it.level.ordinal }?.level ?: RiskLevel.LOW
    }

    /**
     * Returns the recommended list of occupational health exams for a given risk level.
     */
    fun suggestPcmsoExamsForRisk(riskLevel: RiskLevel): List<String> {
        return when (riskLevel) {
            RiskLevel.MINIMAL -> listOf(
                "Avaliação Médica Clínica"
            )
            RiskLevel.LOW -> listOf(
                "Avaliação Médica Clínica",
                "Acuidade Visual"
            )
            RiskLevel.MODERATE -> listOf(
                "Avaliação Médica Clínica",
                "Audiometria Tonal",
                "Espirometria",
                "Acuidade Visual"
            )
            RiskLevel.HIGH -> listOf(
                "Avaliação Médica Clínica",
                "Audiometria Tonal",
                "Espirometria",
                "Eletrocardiograma",
                "Hemograma Completo",
                "Acuidade Visual"
            )
            RiskLevel.CRITICAL -> listOf(
                "Avaliação Médica Clínica",
                "Audiometria Tonal",
                "Espirometria",
                "Eletrocardiograma",
                "Hemograma Completo",
                "Ressonância Magnética (se indicada)",
                "Tomografia (se indicada)",
                "Acuidade Visual",
                "Exames Toxicológicos"
            )
        }
    }

    /**
     * Returns recommended exam frequencies (in months) for a given risk level.
     */
    fun suggestPcmsoScheduleForRisk(riskLevel: RiskLevel): Map<String, Int> {
        return when (riskLevel) {
            RiskLevel.MINIMAL -> mapOf(
                "Avaliação Médica Clínica" to 24
            )
            RiskLevel.LOW -> mapOf(
                "Avaliação Médica Clínica" to 24,
                "Acuidade Visual" to 24
            )
            RiskLevel.MODERATE -> mapOf(
                "Avaliação Médica Clínica" to 12,
                "Audiometria Tonal" to 12,
                "Espirometria" to 24,
                "Acuidade Visual" to 24
            )
            RiskLevel.HIGH -> mapOf(
                "Avaliação Médica Clínica" to 12,
                "Audiometria Tonal" to 6,
                "Espirometria" to 12,
                "Eletrocardiograma" to 12,
                "Hemograma Completo" to 12,
                "Acuidade Visual" to 12
            )
            RiskLevel.CRITICAL -> mapOf(
                "Avaliação Médica Clínica" to 6,
                "Audiometria Tonal" to 6,
                "Espirometria" to 6,
                "Eletrocardiograma" to 6,
                "Hemograma Completo" to 6,
                "Acuidade Visual" to 6,
                "Exames Toxicológicos" to 12
            )
        }
    }

    /**
     * Builds a formatted risk-assessment text suggestion for PCMSO,
     * including risk level and recommended exams with frequencies.
     */
    fun buildPcmsoRiskAssessmentSuggestion(cnae: String): String {
        val riskLevel = detectRiskLevelByCnae(cnae)
        val exams = suggestPcmsoExamsForRisk(riskLevel)
        val schedule = suggestPcmsoScheduleForRisk(riskLevel)
        val levelLabel = when (riskLevel) {
            RiskLevel.MINIMAL  -> "Mínimo"
            RiskLevel.LOW      -> "Baixo"
            RiskLevel.MODERATE -> "Moderado"
            RiskLevel.HIGH     -> "Alto"
            RiskLevel.CRITICAL -> "Crítico"
        }
        val examLines = exams.joinToString("\n") { exam ->
            val freq = schedule[exam]
            if (freq != null) "- $exam (a cada $freq meses)" else "- $exam"
        }
        return "Nível de risco estimado: $levelLabel (CNAE $cnae)\n" +
               "Exames recomendados:\n$examLines"
    }
}

