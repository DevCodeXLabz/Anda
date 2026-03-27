package com.example.anda.data.services

import com.example.anda.domain.CompanyProfile
import com.example.anda.domain.EmployeeProfile
import com.example.anda.feature.security.DocumentSignatureHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutofillServiceTest {

    @Test
    fun createAsoDraft_shouldInjectNr10RiskForElectricCnae() {
        val service = AutofillService()
        val company = CompanyProfile(cnpj = "123", legalName = "Energia", cnae = "35.11-5")
        val employee = EmployeeProfile(
            name = "Joao",
            cpf = "12345678900",
            rg = "123",
            birthDate = 0L,
            gender = "M"
        )

        val aso = service.createAsoDraft(
            company = company,
            employee = employee,
            doctorName = "Dra Ana",
            doctorCrm = "CRM123",
            clinicName = "Clinica X",
            clinicCnpj = "999"
        )

        assertTrue(aso.employee.riskExposures.any { it.normativeReference == "NR-10" })
        assertEquals("APT", aso.examinationResults)
    }

    @Test
    fun sha256_shouldBeDeterministic() {
        val first = DocumentSignatureHelper.sha256("payload-teste")
        val second = DocumentSignatureHelper.sha256("payload-teste")
        assertEquals(first, second)
        assertEquals(64, first.length)
    }

    @Test
    fun extractPreviousAsoContextFromHtml_shouldParseAccentedHtmlLabels() {
        val service = AutofillService()
        val payload = """
            <strong>Clínica:</strong> Arauclin
            <strong>CNPJ:</strong> 12.345.678/0001-90
            <strong>Médico:</strong> Dra Maria
            <strong>CRM:</strong> CRM-PR-12345
        """.trimIndent()

        val context = service.extractPreviousAsoContextFromHtml(payload)
        assertEquals("Arauclin", context.clinicName)
        assertEquals("12345678000190", context.clinicCnpj)
        assertEquals("Dra Maria", context.doctorName)
        assertEquals("CRM-PR-12345", context.doctorCrm)
    }

    @Test
    fun extractPreviousAsoContextFromHtml_shouldParsePlainUnaccentedLabels() {
        val service = AutofillService()
        val payload = """
            Clinica: Clinica Segura
            CNPJ: 00999888000177
            Medico: Dr Joao
            CRM: 99887
        """.trimIndent()

        val context = service.extractPreviousAsoContextFromHtml(payload)
        assertEquals("Clinica Segura", context.clinicName)
        assertEquals("00999888000177", context.clinicCnpj)
        assertEquals("Dr Joao", context.doctorName)
        assertEquals("99887", context.doctorCrm)
    }

    @Test
    fun extractPreviousGenericContextFromHtml_shouldParseCoreSectionsAndSignedBy() {
        val service = AutofillService()
        val payload = """
            <h3>2. OBJETIVO</h3>
            <div>Reduzir exposicao ocupacional a ruido.</div>
            <h3>3. ESCOPO</h3>
            <div>Setor de manutencao e operacao.</div>
            <h3>5. DESENVOLVIMENTO TÉCNICO</h3>
            <div>Foram avaliadas fontes geradoras e controles ativos.</div>
            <section>
              <h3>6. RECOMENDAÇÕES E PLANO DE AÇÃO</h3>
              <table><tbody><tr>
                <td>1</td>
                <td>Implementar enclausuramento acustico</td>
                <td>Tecnico</td>
                <td>Definir</td>
              </tr></tbody></table>
            </section>
        """.trimIndent()

        val context = service.extractPreviousGenericContextFromHtml(payload, signedBy = "Eng. Maria")
        assertEquals("Reduzir exposicao ocupacional a ruido.", context.objective)
        assertEquals("Setor de manutencao e operacao.", context.scope)
        assertEquals("Foram avaliadas fontes geradoras e controles ativos.", context.technicalDetails)
        assertEquals("Implementar enclausuramento acustico", context.recommendations)
        assertEquals("Eng. Maria", context.responsibleName)
    }

    @Test
    fun extractPreviousDocumentContext_shouldUseSignedByWhenAvailable() {
        val service = AutofillService()
        val payload = """
            <strong>Responsável Técnico:</strong> Nome Antigo
            <strong>CREA:</strong> CREA-PR-1234
            <strong>Clínica:</strong> Clinica Centro
            <strong>CNPJ:</strong> 12.345.678/0001-90
        """.trimIndent()

        val context = service.extractPreviousDocumentContext(payload, signedBy = "Nome Assinado")
        assertEquals("Nome Assinado", context.responsibleName)
        assertEquals("CREA-PR-1234", context.professionalRegistry)
        assertEquals("Clinica Centro", context.clinicName)
        assertEquals("12345678000190", context.clinicCnpj)
    }

    @Test
    fun extractPreviousDocumentContext_shouldParseReporterForCat() {
        val service = AutofillService()
        val payload = """
            <strong>Responsável pela Emissão:</strong> Tecnico CAT
            <strong>CRM:</strong> CRM-123
        """.trimIndent()

        val context = service.extractPreviousDocumentContext(payload)
        assertEquals("Tecnico CAT", context.responsibleName)
        assertEquals("CRM-123", context.professionalRegistry)
        assertEquals("Tecnico CAT", context.reportedBy)
    }

    @Test
    fun extractPreviousDocumentContext_shouldSanitizeResponsibleAndExtractInlineRegistry() {
        val service = AutofillService()
        val payload = """
            Responsável Técnico: Eng. Carla | CREA: 90876
        """.trimIndent()

        val context = service.extractPreviousDocumentContext(payload)
        assertEquals("Eng. Carla", context.responsibleName)
        assertEquals("CREA: 90876", context.professionalRegistry)
    }
}

