package com.example.anda.data.services

import com.example.anda.domain.CompanyProfile
import com.example.anda.domain.EmployeeProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentGenerationServiceTest {

    private val autofillService = AutofillService()
    private val documentGenerationService = DocumentGenerationService()

    @Test
    fun generateASO_shouldIncludeOperationalFooterDisclaimer() {
        val html = documentGenerationService.generateASO(sampleAso())

        assertTrue(html.contains(AsoOperationalPolicy.HTML_FOOTER_DISCLAIMER))
        assertTrue(html.contains("Documento gerado por ANDA"))
    }

    @Test
    fun buildPdfBody_shouldPrefixOperationalDisclaimer() {
        val body = AsoOperationalPolicy.buildPdfBody("conteudo base")

        assertTrue(body.startsWith(AsoOperationalPolicy.PDF_EXPORT_DISCLAIMER))
        assertTrue(body.contains("conteudo base"))
    }

    @Test
    fun generateGenericSstDocument_forAet_shouldIncludeErgonomicChecklistAndValidation() {
        val html = documentGenerationService.generateGenericSstDocument(
            DocumentGenerationService.GenericSstDocumentInput(
                documentType = "AET",
                documentTitle = "AET - Analise Ergonomica do Trabalho",
                normativeReference = "NR-17",
                companyCnpj = "12345678000190",
                companyName = "Empresa Teste",
                companyCnae = "35.11-5",
                responsibleName = "Tecnico Responsavel",
                objective = "Objetivo",
                scope = "Escopo",
                technicalDetails = "Detalhes",
                recommendations = "Acao 1"
            )
        )

        assertTrue(html.contains("4. CRITÉRIOS NORMATIVOS MÍNIMOS"))
        assertTrue(html.contains("fatores psicossociais"))
        assertTrue(html.contains("7. VALIDAÇÃO OPERACIONAL"))
        assertTrue(html.contains("Revisão anual e após mudanças de processo"))
    }

    @Test
    fun generateGenericSstDocument_forPt_shouldNormalizeTypeAndAddPtAlerts() {
        val html = documentGenerationService.generateGenericSstDocument(
            DocumentGenerationService.GenericSstDocumentInput(
                documentType = "PT",
                documentTitle = "Permissao de Trabalho",
                normativeReference = "NR-01",
                companyCnpj = "12345678000190",
                companyName = "Empresa Teste",
                companyCnae = "35.11-5",
                responsibleName = "Tecnico Responsavel",
                objective = "Objetivo",
                scope = "Escopo",
                technicalDetails = "Detalhes",
                recommendations = "Bloquear energia;Liberar area"
            )
        )

        assertTrue(html.contains("Tipo: PT"))
        assertTrue(html.contains("A PT deve ser encerrada ao final da atividade"))
        assertTrue(html.contains("Revalidação por turno ou por atividade"))
    }

    @Test
    fun generateGenericSstDocument_shouldRenderSpecializedSectionWhenFieldsAreProvided() {
        val html = documentGenerationService.generateGenericSstDocument(
            DocumentGenerationService.GenericSstDocumentInput(
                documentType = "AET",
                documentTitle = "AET - Analise Ergonomica do Trabalho",
                normativeReference = "NR-17",
                companyCnpj = "12345678000190",
                companyName = "Empresa Teste",
                companyCnae = "35.11-5",
                responsibleName = "Tecnico Responsavel",
                objective = "Objetivo",
                scope = "Escopo",
                technicalDetails = "Detalhes",
                recommendations = "Acao 1",
                specializedFields = mapOf(
                    "aet_workstation" to "Linha de envase",
                    "aet_task" to "Abastecimento e inspeção visual"
                )
            )
        )

        assertTrue(html.contains("5.1 CAMPOS ESPECÍFICOS"))
        assertTrue(html.contains("AET - Posto de trabalho"))
        assertTrue(html.contains("Linha de envase"))
    }

    @Test
    fun generateGenericSstDocument_shouldNormalizeNr12AliasInOperationalValidation() {
        val html = documentGenerationService.generateGenericSstDocument(
            DocumentGenerationService.GenericSstDocumentInput(
                documentType = "NR12",
                documentTitle = "Laudo NR-12",
                normativeReference = "NR-12",
                companyCnpj = "12345678000190",
                companyName = "Empresa Teste",
                companyCnae = "35.11-5",
                responsibleName = "Tecnico Responsavel",
                objective = "Objetivo",
                scope = "Escopo",
                technicalDetails = "Detalhes",
                recommendations = "Acao 1"
            )
        )

        assertTrue(html.contains("Tipo:</strong> LAUDO_NR12"))
        assertTrue(html.contains("Inventário de máquinas, zonas de perigo e proteções"))
    }

    @Test
    fun generateGenericSstDocument_shouldNormalizeNrAliasWithDashAndSpace() {
        val nr12DashHtml = documentGenerationService.generateGenericSstDocument(
            DocumentGenerationService.GenericSstDocumentInput(
                documentType = "NR-12",
                documentTitle = "Laudo NR-12",
                normativeReference = "NR-12",
                companyCnpj = "12345678000190",
                companyName = "Empresa Teste",
                companyCnae = "35.11-5",
                responsibleName = "Tecnico Responsavel",
                objective = "Objetivo",
                scope = "Escopo",
                technicalDetails = "Detalhes",
                recommendations = "Acao 1"
            )
        )

        val nr20SpaceHtml = documentGenerationService.generateGenericSstDocument(
            DocumentGenerationService.GenericSstDocumentInput(
                documentType = "NR 20",
                documentTitle = "Laudo NR-20",
                normativeReference = "NR-20",
                companyCnpj = "12345678000190",
                companyName = "Empresa Teste",
                companyCnae = "35.11-5",
                responsibleName = "Tecnico Responsavel",
                objective = "Objetivo",
                scope = "Escopo",
                technicalDetails = "Detalhes",
                recommendations = "Acao 1"
            )
        )

        assertTrue(nr12DashHtml.contains("Tipo:</strong> LAUDO_NR12"))
        assertTrue(nr12DashHtml.contains("Inventário de máquinas, zonas de perigo e proteções"))

        assertTrue(nr20SpaceHtml.contains("Tipo:</strong> LAUDO_NR20"))
        assertTrue(nr20SpaceHtml.contains("Classificação de instalações com inflamáveis/combustíveis"))
    }

    @Test
    fun generateGenericSstDocument_shouldNormalizeExtendedAliasesAndKeepUnknownFallback() {
        val nrLaudoAliasHtml = documentGenerationService.generateGenericSstDocument(
            DocumentGenerationService.GenericSstDocumentInput(
                documentType = "laudo nr 12",
                documentTitle = "Laudo NR-12",
                normativeReference = "NR-12",
                companyCnpj = "12345678000190",
                companyName = "Empresa Teste",
                companyCnae = "35.11-5",
                responsibleName = "Tecnico Responsavel",
                objective = "Objetivo",
                scope = "Escopo",
                technicalDetails = "Detalhes",
                recommendations = "Acao 1"
            )
        )

        val mapaPluralAliasHtml = documentGenerationService.generateGenericSstDocument(
            DocumentGenerationService.GenericSstDocumentInput(
                documentType = "mapa riscos",
                documentTitle = "Mapa de Riscos",
                normativeReference = "NR-5",
                companyCnpj = "12345678000190",
                companyName = "Empresa Teste",
                companyCnae = "35.11-5",
                responsibleName = "Tecnico Responsavel",
                objective = "Objetivo",
                scope = "Escopo",
                technicalDetails = "Detalhes",
                recommendations = "Acao 1"
            )
        )

        val unknownTypeHtml = documentGenerationService.generateGenericSstDocument(
            DocumentGenerationService.GenericSstDocumentInput(
                documentType = "custom_doc",
                documentTitle = "Custom Doc",
                normativeReference = "Interno",
                companyCnpj = "12345678000190",
                companyName = "Empresa Teste",
                companyCnae = "35.11-5",
                responsibleName = "Tecnico Responsavel",
                objective = "Objetivo",
                scope = "Escopo",
                technicalDetails = "Detalhes",
                recommendations = "Acao 1"
            )
        )

        assertTrue(nrLaudoAliasHtml.contains("Tipo:</strong> LAUDO_NR12"))
        assertTrue(nrLaudoAliasHtml.contains("Inventário de máquinas, zonas de perigo e proteções"))

        assertTrue(mapaPluralAliasHtml.contains("Tipo:</strong> MAPA_RISCO"))
        assertTrue(mapaPluralAliasHtml.contains("Setorização e representação visual dos grupos de risco"))

        assertTrue(unknownTypeHtml.contains("Tipo:</strong> CUSTOM_DOC"))
    }

    @Test
    fun generateGenericSstDocument_shouldSplitRecommendationsFromSemicolonAndNewLine() {
        val html = documentGenerationService.generateGenericSstDocument(
            DocumentGenerationService.GenericSstDocumentInput(
                documentType = "PT",
                documentTitle = "Permissao de Trabalho",
                normativeReference = "NR-01",
                companyCnpj = "12345678000190",
                companyName = "Empresa Teste",
                companyCnae = "35.11-5",
                responsibleName = "Tecnico Responsavel",
                objective = "Objetivo",
                scope = "Escopo",
                technicalDetails = "Detalhes",
                recommendations = "Bloquear energia;Isolar area\nConfirmar liberacao"
            )
        )

        assertTrue(html.contains(">1<"))
        assertTrue(html.contains(">2<"))
        assertTrue(html.contains(">3<"))
        assertTrue(html.contains("Bloquear energia"))
        assertTrue(html.contains("Isolar area"))
        assertTrue(html.contains("Confirmar liberacao"))
    }

    @Test
    fun generateGenericSstDocument_shouldEscapeHtmlInUserFields() {
        val html = documentGenerationService.generateGenericSstDocument(
            DocumentGenerationService.GenericSstDocumentInput(
                documentType = "PT",
                documentTitle = "Titulo <script>alert(1)</script>",
                normativeReference = "NR-01",
                companyCnpj = "12345678000190",
                companyName = "Empresa <b>Teste</b>",
                companyCnae = "35.11-5",
                responsibleName = "Resp & Gestor",
                objective = "Linha 1\nLinha 2",
                scope = "Escopo <i>interno</i>",
                technicalDetails = "Detalhes & validação",
                recommendations = "Ação <1>;Validar & registrar",
                documentId = "doc-<unsafe>"
            )
        )

        assertFalse(html.contains("<script>alert(1)</script>"))
        assertTrue(html.contains("Titulo &lt;script&gt;alert(1)&lt;/script&gt;"))
        assertTrue(html.contains("Empresa &lt;b&gt;Teste&lt;/b&gt;"))
        assertTrue(html.contains("Resp &amp; Gestor"))
        assertTrue(html.contains("Linha 1<br/>Linha 2"))
        assertTrue(html.contains("Ação &lt;1&gt;"))
        assertTrue(html.contains("doc-&lt;unsafe&gt;"))
    }

    @Test
    fun generateGenericSstDocument_shouldIgnoreEmptyRecommendationTokens() {
        val html = documentGenerationService.generateGenericSstDocument(
            DocumentGenerationService.GenericSstDocumentInput(
                documentType = "PT",
                documentTitle = "Permissao de Trabalho",
                normativeReference = "NR-01",
                companyCnpj = "12345678000190",
                companyName = "Empresa Teste",
                companyCnae = "35.11-5",
                responsibleName = "Tecnico Responsavel",
                objective = "Objetivo",
                scope = "Escopo",
                technicalDetails = "Detalhes",
                recommendations = " ;\n  ;Bloquear energia\r\n;  Isolar area;;"
            )
        )

        assertTrue(html.contains("Bloquear energia"))
        assertTrue(html.contains("Isolar area"))
        assertFalse(html.contains(">3<"))
    }

    @Test
    fun generateGenericSstDocument_shouldKeepPtSpecializedFieldOrderAndIgnoreBlankValues() {
        val html = documentGenerationService.generateGenericSstDocument(
            DocumentGenerationService.GenericSstDocumentInput(
                documentType = "PT",
                documentTitle = "Permissao de Trabalho",
                normativeReference = "NR-01",
                companyCnpj = "12345678000190",
                companyName = "Empresa Teste",
                companyCnae = "35.11-5",
                responsibleName = "Tecnico Responsavel",
                objective = "Objetivo",
                scope = "Escopo",
                technicalDetails = "Detalhes",
                recommendations = "Acao 1",
                specializedFields = mapOf(
                    "pt_executor" to "Equipe de manutencao",
                    "pt_authorizer" to "Supervisor de area",
                    "pt_issuer" to "Tecnico de seguranca",
                    "aet_task" to "",
                    "ltcat_agent" to "   "
                )
            )
        )

        val issuerIndex = html.indexOf("PT - Emitente")
        val executorIndex = html.indexOf("PT - Executante")
        val authorizerIndex = html.indexOf("PT - Autorizador")

        assertTrue(issuerIndex >= 0)
        assertTrue(executorIndex > issuerIndex)
        assertTrue(authorizerIndex > executorIndex)
        assertTrue(!html.contains("aet_task"))
        assertTrue(!html.contains("ltcat_agent"))
    }

    @Test
    fun generateGenericSstDocument_shouldRenderNrSpecializedFieldsInCanonicalOrder() {
        val html = documentGenerationService.generateGenericSstDocument(
            DocumentGenerationService.GenericSstDocumentInput(
                documentType = "LAUDO_NR10",
                documentTitle = "Laudo NR-10",
                normativeReference = "NR-10",
                companyCnpj = "12345678000190",
                companyName = "Empresa Teste",
                companyCnae = "35.11-5",
                responsibleName = "Tecnico Responsavel",
                objective = "Objetivo",
                scope = "Escopo",
                technicalDetails = "Detalhes",
                recommendations = "Acao 1",
                specializedFields = mapOf(
                    "nr_deadline" to "30 dias",
                    "nr_critical" to "Ausencia de bloqueio formal",
                    "nr_asset" to "Painel eletrico setor A"
                )
            )
        )

        val assetIndex = html.indexOf("NR - Equipamento/Área avaliada")
        val criticalIndex = html.indexOf("NR - Não conformidade crítica")
        val deadlineIndex = html.indexOf("NR - Prazo de adequação")

        assertTrue(assetIndex >= 0)
        assertTrue(criticalIndex > assetIndex)
        assertTrue(deadlineIndex > criticalIndex)
        assertTrue(html.contains("Painel eletrico setor A"))
        assertTrue(html.contains("Ausencia de bloqueio formal"))
        assertTrue(html.contains("30 dias"))
    }

    @Test
    fun generateGenericSstDocument_shouldRenderPcaFieldsInCorrectOrder() {
        val html = documentGenerationService.generateGenericSstDocument(
            DocumentGenerationService.GenericSstDocumentInput(
                documentType = "PCA",
                documentTitle = "PCA - Programa de Conservacao Auditiva",
                normativeReference = "Saude ocupacional",
                companyCnpj = "12345678000190",
                companyName = "Empresa Teste",
                companyCnae = "35.11-5",
                responsibleName = "Tecnico Responsavel",
                objective = "Objetivo",
                scope = "Escopo",
                technicalDetails = "Detalhes",
                recommendations = "Acao 1",
                specializedFields = mapOf(
                    "pca_measure" to "Implementar sistema de monitoramento auditivo",
                    "pca_risk" to "Exposicao a ruido acima de 80dB"
                )
            )
        )

        val riskIndex = html.indexOf("PCA - Risco identificado")
        val measureIndex = html.indexOf("PCA - Medida de controle")
        assertTrue(riskIndex >= 0)
        assertTrue(measureIndex > riskIndex)
        assertTrue(html.contains("Exposicao a ruido acima de 80dB"))
        assertTrue(html.contains("Implementar sistema de monitoramento auditivo"))
    }

    @Test
    fun generateGenericSstDocument_shouldRenderPprFieldsInCorrectOrder() {
        val html = documentGenerationService.generateGenericSstDocument(
            DocumentGenerationService.GenericSstDocumentInput(
                documentType = "PPR",
                documentTitle = "PPR - Programa de Protecao Respiratoria",
                normativeReference = "Protecao respiratoria",
                companyCnpj = "12345678000190",
                companyName = "Empresa Teste",
                companyCnae = "35.11-5",
                responsibleName = "Tecnico Responsavel",
                objective = "Objetivo",
                scope = "Escopo",
                technicalDetails = "Detalhes",
                recommendations = "Acao 1",
                specializedFields = mapOf(
                    "ppr_fit" to "QNFT - anual obrigatorio",
                    "ppr_epi" to "Respirador N95 descartavel"
                )
            )
        )

        val epiIndex = html.indexOf("PPR - EPI obrigatório")
        val fitIndex = html.indexOf("PPR - Teste de vedação")
        assertTrue(epiIndex >= 0)
        assertTrue(fitIndex > epiIndex)
        assertTrue(html.contains("Respirador N95 descartavel"))
        assertTrue(html.contains("QNFT - anual obrigatorio"))
    }

    @Test
    fun generateGenericSstDocument_shouldRenderInsalubridadeFieldsInCanonicalOrder() {
        val html = documentGenerationService.generateGenericSstDocument(
            DocumentGenerationService.GenericSstDocumentInput(
                documentType = "INSALUBRIDADE",
                documentTitle = "Laudo de Insalubridade",
                normativeReference = "NR-15",
                companyCnpj = "12345678000190",
                companyName = "Empresa Teste",
                companyCnae = "35.11-5",
                responsibleName = "Tecnico Responsavel",
                objective = "Objetivo",
                scope = "Escopo",
                technicalDetails = "Detalhes",
                recommendations = "Acao 1",
                specializedFields = mapOf(
                    "insalubridade_grau" to "Maximo",
                    "insalubridade_agent" to "Calor intenso (> 35°C)"
                )
            )
        )

        val agentIndex = html.indexOf("Insalubridade - Agente nocivo")
        val grauIndex = html.indexOf("Insalubridade - Grau")
        assertTrue(agentIndex >= 0)
        assertTrue(grauIndex > agentIndex)
        assertTrue(html.contains("Calor intenso (&gt; 35°C)"))
        assertTrue(html.contains("Maximo"))
    }

    @Test
    fun generateGenericSstDocument_shouldRenderPericulosidadeFieldsInCanonicalOrder() {
        val html = documentGenerationService.generateGenericSstDocument(
            DocumentGenerationService.GenericSstDocumentInput(
                documentType = "PERICULOSIDADE",
                documentTitle = "Laudo de Periculosidade",
                normativeReference = "NR-16",
                companyCnpj = "12345678000190",
                companyName = "Empresa Teste",
                companyCnae = "35.11-5",
                responsibleName = "Tecnico Responsavel",
                objective = "Objetivo",
                scope = "Escopo",
                technicalDetails = "Detalhes",
                recommendations = "Acao 1",
                specializedFields = mapOf(
                    "periculosidade_exp" to "Permanente durante turno de trabalho",
                    "periculosidade_agent" to "Materiais inflamaveis (gasolina)"
                )
            )
        )

        val agentIndex = html.indexOf("Periculosidade - Agente perigoso")
        val expIndex = html.indexOf("Periculosidade - Exposição")
        assertTrue(agentIndex >= 0)
        assertTrue(expIndex > agentIndex)
        assertTrue(html.contains("Materiais inflamaveis (gasolina)"))
        assertTrue(html.contains("Permanente durante turno de trabalho"))
    }

    @Test
    fun generateGenericSstDocument_shouldRenderPlanoAcaoFieldsInCanonicalOrder() {
        val html = documentGenerationService.generateGenericSstDocument(
            DocumentGenerationService.GenericSstDocumentInput(
                documentType = "PLANO_ACAO",
                documentTitle = "Plano de Acao de SST",
                normativeReference = "Gestao de riscos",
                companyCnpj = "12345678000190",
                companyName = "Empresa Teste",
                companyCnae = "35.11-5",
                responsibleName = "Tecnico Responsavel",
                objective = "Objetivo",
                scope = "Escopo",
                technicalDetails = "Detalhes",
                recommendations = "Acao 1",
                specializedFields = mapOf(
                    "plano_acao_acao" to "Implementar sistema de ventilacao mecanica",
                    "plano_acao_risco" to "Exposicao a vapores organicos"
                )
            )
        )

        val riscoIndex = html.indexOf("Plano de Ação - Risco prioritário")
        val acaoIndex = html.indexOf("Plano de Ação - Ação corretiva")
        assertTrue(riscoIndex >= 0)
        assertTrue(acaoIndex > riscoIndex)
        assertTrue(html.contains("Exposicao a vapores organicos"))
        assertTrue(html.contains("Implementar sistema de ventilacao mecanica"))
    }

    @Test
    fun generateGenericSstDocument_forMapaRisco_shouldIncludeDedicatedChecklistAndReviewWindow() {
        val html = documentGenerationService.generateGenericSstDocument(
            DocumentGenerationService.GenericSstDocumentInput(
                documentType = "MAPA_RISCO",
                documentTitle = "Mapa de Riscos",
                normativeReference = "NR-5",
                companyCnpj = "12345678000190",
                companyName = "Empresa Teste",
                companyCnae = "35.11-5",
                responsibleName = "Tecnico Responsavel",
                objective = "Objetivo",
                scope = "Escopo",
                technicalDetails = "Detalhes",
                recommendations = "Acao 1"
            )
        )

        assertTrue(html.contains("Setorização e representação visual dos grupos de risco"))
        assertTrue(html.contains("Mapa deve permanecer acessível e visível para os trabalhadores do setor."))
        assertTrue(html.contains("Revisão semestral ou após alteração de layout/processo"))
    }

    @Test
    fun generateGenericSstDocument_forResgate_shouldIncludeDedicatedChecklistAndAlerts() {
        val html = documentGenerationService.generateGenericSstDocument(
            DocumentGenerationService.GenericSstDocumentInput(
                documentType = "RESGATE",
                documentTitle = "Plano de Resgate",
                normativeReference = "NR-35",
                companyCnpj = "12345678000190",
                companyName = "Empresa Teste",
                companyCnae = "35.11-5",
                responsibleName = "Tecnico Responsavel",
                objective = "Objetivo",
                scope = "Escopo",
                technicalDetails = "Detalhes",
                recommendations = "Acao 1"
            )
        )

        assertTrue(html.contains("Cenários de emergência e gatilhos de acionamento definidos"))
        assertTrue(html.contains("Sem simulado e recursos mínimos, o plano deve ser tratado como não conforme."))
        assertTrue(html.contains("Revisão trimestral com simulado periódico"))
    }

    @Test
    fun generateGenericSstDocument_shouldRenderResgateFieldsInCanonicalOrder() {
        val html = documentGenerationService.generateGenericSstDocument(
            DocumentGenerationService.GenericSstDocumentInput(
                documentType = "RESGATE",
                documentTitle = "Plano de Resgate",
                normativeReference = "NR-35",
                companyCnpj = "12345678000190",
                companyName = "Empresa Teste",
                companyCnae = "35.11-5",
                responsibleName = "Tecnico Responsavel",
                objective = "Objetivo",
                scope = "Escopo",
                technicalDetails = "Detalhes",
                recommendations = "Acao 1",
                specializedFields = mapOf(
                    "resgate_equipe" to "Bombeiro civil + 2 socorristas",
                    "resgate_cenario" to "Queda em altura no telhado industrial"
                )
            )
        )

        val cenarioIndex = html.indexOf("Resgate - Cenário de emergência")
        val equipeIndex = html.indexOf("Resgate - Equipe de resgate")
        assertTrue(cenarioIndex >= 0)
        assertTrue(equipeIndex > cenarioIndex)
        assertTrue(html.contains("Queda em altura no telhado industrial"))
        assertTrue(html.contains("Bombeiro civil + 2 socorristas"))
    }

    @Test
    fun generateGenericSstDocument_shouldRenderInventarioFieldsInCanonicalOrder() {
        val html = documentGenerationService.generateGenericSstDocument(
            DocumentGenerationService.GenericSstDocumentInput(
                documentType = "INVENTARIO",
                documentTitle = "Inventario de Riscos",
                normativeReference = "NR-01 GRO/PGR",
                companyCnpj = "12345678000190",
                companyName = "Empresa Teste",
                companyCnae = "35.11-5",
                responsibleName = "Tecnico Responsavel",
                objective = "Objetivo",
                scope = "Escopo",
                technicalDetails = "Detalhes",
                recommendations = "Acao 1",
                specializedFields = mapOf(
                    "inventario_risco" to "Exposicao a ruido acima de 85 dB",
                    "inventario_setor" to "Producao - Linha de montagem"
                )
            )
        )

        val setorIndex = html.indexOf("Inventário - Setor avaliado")
        val riscoIndex = html.indexOf("Inventário - Risco principal")
        assertTrue(setorIndex >= 0)
        assertTrue(riscoIndex > setorIndex)
        assertTrue(html.contains("Producao - Linha de montagem"))
        assertTrue(html.contains("Exposicao a ruido acima de 85 dB"))
    }

    @Test
    fun generateGenericSstDocument_shouldRenderPgrtrFieldsInCanonicalOrder() {
        val html = documentGenerationService.generateGenericSstDocument(
            DocumentGenerationService.GenericSstDocumentInput(
                documentType = "PGRTR",
                documentTitle = "PGRTR",
                normativeReference = "Gerenciamento de riscos especifico",
                companyCnpj = "12345678000190",
                companyName = "Empresa Teste",
                companyCnae = "35.11-5",
                responsibleName = "Tecnico Responsavel",
                objective = "Objetivo",
                scope = "Escopo",
                technicalDetails = "Detalhes",
                recommendations = "Acao 1",
                specializedFields = mapOf(
                    "pgrtr_risco" to "Exposicao a agrotoxicos na colheita",
                    "pgrtr_atividade" to "Colheita mecanizada de cana-de-acucar"
                )
            )
        )

        val atividadeIndex = html.indexOf("PGRTR - Atividade principal")
        val riscoIndex = html.indexOf("PGRTR - Risco específico")
        assertTrue(atividadeIndex >= 0)
        assertTrue(riscoIndex > atividadeIndex)
        assertTrue(html.contains("Colheita mecanizada de cana-de-acucar"))
        assertTrue(html.contains("Exposicao a agrotoxicos na colheita"))
    }

    @Test
    fun generateGenericSstDocument_shouldRenderMapaRiscoFieldsInCanonicalOrder() {
        val html = documentGenerationService.generateGenericSstDocument(
            DocumentGenerationService.GenericSstDocumentInput(
                documentType = "MAPA_RISCO",
                documentTitle = "Mapa de Riscos",
                normativeReference = "NR-5",
                companyCnpj = "12345678000190",
                companyName = "Empresa Teste",
                companyCnae = "35.11-5",
                responsibleName = "Tecnico Responsavel",
                objective = "Objetivo",
                scope = "Escopo",
                technicalDetails = "Detalhes",
                recommendations = "Acao 1",
                specializedFields = mapOf(
                    "mapa_risco_grave" to "Amputacao por prensa hidraulica",
                    "mapa_setor" to "Estamparia - Setor 3"
                )
            )
        )

        val setorIndex = html.indexOf("Mapa de Risco - Setor mapeado")
        val riscoGraveIndex = html.indexOf("Mapa de Risco - Risco grave identificado")
        assertTrue(setorIndex >= 0)
        assertTrue(riscoGraveIndex > setorIndex)
        assertTrue(html.contains("Estamparia - Setor 3"))
        assertTrue(html.contains("Amputacao por prensa hidraulica"))
    }

    @Test
    fun generateGenericSstDocument_allNr12AliasesShouldProduceSameCanonicalTipoHeader() {
        val aliases = listOf("NR12", "NR-12", "NR 12", "nr_12", "laudo nr 12", "laudo-nr_12", " Laudo NR-12 ", "LAUDO_NR12")
        val htmls = aliases.map { alias ->
            alias to documentGenerationService.generateGenericSstDocument(
                DocumentGenerationService.GenericSstDocumentInput(
                    documentType = alias,
                    documentTitle = "Laudo NR-12",
                    normativeReference = "NR-12",
                    companyCnpj = "12345678000190",
                    companyName = "Empresa Teste",
                    companyCnae = "35.11-5",
                    responsibleName = "Tecnico Responsavel",
                    objective = "Objetivo",
                    scope = "Escopo",
                    technicalDetails = "Detalhes",
                    recommendations = "Acao 1"
                )
            )
        }

        htmls.forEach { (alias, html) ->
            assertTrue("Alias '$alias' did not produce canonical LAUDO_NR12 header",
                html.contains("Tipo:</strong> LAUDO_NR12"))
            assertTrue("Alias '$alias' did not produce NR-12 compliance checklist",
                html.contains("Inventário de máquinas, zonas de perigo e proteções"))
        }
    }

    @Test
    fun generateGenericSstDocument_allNr20AliasesShouldProduceSameCanonicalTipoHeader() {
        val aliases = listOf("NR20", "NR-20", "NR 20", "nr_20", "LAUDO_NR20")
        aliases.forEach { alias ->
            val html = documentGenerationService.generateGenericSstDocument(
                DocumentGenerationService.GenericSstDocumentInput(
                    documentType = alias,
                    documentTitle = "Laudo NR-20",
                    normativeReference = "NR-20",
                    companyCnpj = "12345678000190",
                    companyName = "Empresa Teste",
                    companyCnae = "35.11-5",
                    responsibleName = "Tecnico Responsavel",
                    objective = "Objetivo",
                    scope = "Escopo",
                    technicalDetails = "Detalhes",
                    recommendations = "Acao 1"
                )
            )
            assertTrue("Alias '$alias' did not produce canonical LAUDO_NR20 header",
                html.contains("Tipo:</strong> LAUDO_NR20"))
            assertTrue("Alias '$alias' did not produce NR-20 compliance checklist",
                html.contains("Classificação de instalações com inflamáveis/combustíveis"))
        }
    }

    private fun sampleAso() = autofillService.createAsoDraft(        company = CompanyProfile(
            cnpj = "12345678000190",
            legalName = "Empresa Teste",
            cnae = "35.11-5"
        ),
        employee = EmployeeProfile(
            name = "Funcionario Teste",
            cpf = "12345678900",
            rg = "123",
            birthDate = 0L,
            gender = "M"
        ),
        doctorName = "Dra Ana",
        doctorCrm = "CRM123",
        clinicName = "Clinica SST",
        clinicCnpj = "00999888000177"
    )
}

