package com.example.anda.data.services

import com.example.anda.data.analytics.EnterpriseAnalyticsService
import com.example.anda.domain.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Serviço de geração de documentos SST
 * Responsável por criar ASO, PCMSO, PGR e outros documentos
 */
class DocumentGenerationService(
    private val analyticsService: EnterpriseAnalyticsService? = null
) {

    data class GenericSstDocumentInput(
        val documentType: String,
        val documentTitle: String,
        val normativeReference: String,
        val companyCnpj: String,
        val companyName: String,
        val companyCnae: String,
        val responsibleName: String,
        val objective: String,
        val scope: String,
        val technicalDetails: String,
        val recommendations: String,
        val specializedFields: Map<String, String> = emptyMap(),
        val issueDate: Long = System.currentTimeMillis(),
        val documentId: String = "doc-${System.currentTimeMillis()}"
    )

    companion object {
        private val localePtBr = Locale.forLanguageTag("pt-BR")
        private val dateFormat = SimpleDateFormat("dd/MM/yyyy", localePtBr)
        private val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", localePtBr)
    }

    /**
     * Gera um ASO (Atestado de Saúde Ocupacional) em HTML
     */
    fun generateASO(
        aso: OccupationalHealthCertificate,
        includeHeader: Boolean = true
    ): String {
        val startTime = System.currentTimeMillis()
        val sb = StringBuilder()

        if (includeHeader) {
            sb.append(generateHTMLHeader(aso.clinicName))
        }

        sb.append("""
            <div style="page-break-before: always;">
                <h1 style="text-align: center; text-transform: uppercase;">Atestado de Saúde Ocupacional (ASO)</h1>
                <p style="text-align: center; font-weight: bold;">Modelo PCMSO (NR-7)</p>
                
                <hr style="border: 1px solid #333;">
                
                <!-- DADOS DA CLÍNICA -->
                <section style="margin-top: 20px;">
                    <h3>1. DADOS DO LOCAL DE EMISSÃO</h3>
                    <table style="width: 100%; border-collapse: collapse;">
                        <tr>
                            <td style="padding: 8px; border: 1px solid #ddd; width: 50%;"><strong>Clínica:</strong> ${aso.clinicName}</td>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>CNPJ:</strong> ${formatCNPJ(aso.clinicCNPJ)}</td>
                        </tr>
                        <tr>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>Médico:</strong> ${aso.occupationalDoctor}</td>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>CRM:</strong> ${aso.occupationalDoctorCRM}</td>
                        </tr>
                        <tr>
                            <td colspan="2" style="padding: 8px; border: 1px solid #ddd;"><strong>Data da Emissão:</strong> ${dateFormat.format(Date(aso.examinationDate))}</td>
                        </tr>
                    </table>
                </section>
                
                <!-- DADOS DA EMPRESA -->
                <section style="margin-top: 20px;">
                    <h3>2. DADOS DA EMPRESA</h3>
                    <table style="width: 100%; border-collapse: collapse;">
                        <tr>
                            <td style="padding: 8px; border: 1px solid #ddd; width: 50%;"><strong>Razão Social:</strong> ${aso.companyProfile.legalName}</td>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>CNPJ:</strong> ${formatCNPJ(aso.companyProfile.cnpj)}</td>
                        </tr>
                        <tr>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>Nome Fantasia:</strong> ${aso.companyProfile.tradeName.ifEmpty { "-" }}</td>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>CNAE:</strong> ${aso.companyProfile.cnae}</td>
                        </tr>
                        <tr>
                            <td colspan="2" style="padding: 8px; border: 1px solid #ddd;"><strong>Endereço:</strong> ${aso.companyProfile.postalCode} - ${aso.companyProfile.city}/${aso.companyProfile.state}</td>
                        </tr>
                    </table>
                </section>
                
                <!-- DADOS DO FUNCIONÁRIO -->
                <section style="margin-top: 20px;">
                    <h3>3. DADOS DO FUNCIONÁRIO</h3>
                    <table style="width: 100%; border-collapse: collapse;">
                        <tr>
                            <td style="padding: 8px; border: 1px solid #ddd; width: 50%;"><strong>Nome:</strong> ${aso.employee.name}</td>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>CPF:</strong> ${formatCPF(aso.employee.cpf)}</td>
                        </tr>
                        <tr>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>RG:</strong> ${aso.employee.rg}</td>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>Data Nascimento:</strong> ${dateFormat.format(Date(aso.employee.birthDate))}</td>
                        </tr>
                        <tr>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>Gênero:</strong> ${aso.employee.gender}</td>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>Telefone:</strong> ${aso.employee.phoneNumber.orEmpty()}</td>
                        </tr>
                    </table>
                </section>
                
                <!-- TIPO DE EXAME -->
                <section style="margin-top: 20px;">
                    <h3>4. TIPO DE EXAME</h3>
                    <table style="width: 100%; border-collapse: collapse;">
                        <tr>
                            <td style="padding: 12px; border: 1px solid #ddd;">
                                ☐ Admissional | ☐ Periódico | ☐ Retorno | ☐ Afastamento | ☐ Demissional
                            </td>
                        </tr>
                        <tr>
                            <td style="padding: 12px; border: 1px solid #ddd; text-align: center; font-weight: bold;">
                                Tipo Selecionado: ${translateExaminationType(aso.examinationType)}
                            </td>
                        </tr>
                    </table>
                </section>
                
                <!-- RISCOS E EXPOSIÇÕES -->
                <section style="margin-top: 20px;">
                    <h3>5. RISCOS OCUPACIONAIS</h3>
                    <table style="width: 100%; border-collapse: collapse;">
                        <thead>
                            <tr style="background-color: #f0f0f0;">
                                <th style="padding: 8px; border: 1px solid #ddd; text-align: left;">Risco</th>
                                <th style="padding: 8px; border: 1px solid #ddd; text-align: left;">Nível</th>
                                <th style="padding: 8px; border: 1px solid #ddd; text-align: left;">Referência Normativa</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${aso.employee.riskExposures.joinToString("\n") { risk ->
                                """
                                <tr>
                                    <td style="padding: 8px; border: 1px solid #ddd;">${risk.name}</td>
                                    <td style="padding: 8px; border: 1px solid #ddd;">${translateRiskLevel(risk.level)}</td>
                                    <td style="padding: 8px; border: 1px solid #ddd;">${risk.normativeReference}</td>
                                </tr>
                                """.trimIndent()
                            }}
                        </tbody>
                    </table>
                </section>
                
                <!-- RESULTADO -->
                <section style="margin-top: 20px;">
                    <h3>6. RESULTADO DO EXAME</h3>
                    ${run {
                        val result = aso.examinationResults.uppercase()
                        val (bgColor, borderColor, textColor) = when {
                            result.startsWith("APT") && !result.contains("INAPTO") ->
                                Triple("#E8F5E9", "#2E7D32", "#1B5E20")
                            result.contains("INAPTO TEMP") ->
                                Triple("#FFF8E1", "#F57F17", "#E65100")
                            result.contains("INAPTO") ->
                                Triple("#FFEBEE", "#C62828", "#B71C1C")
                            else -> Triple("#F5F5F5", "#333333", "#212121")
                        }
                        """<div style="text-align: center; font-size: 20px; font-weight: bold; padding: 20px;
                            border: 3px solid $borderColor; background-color: $bgColor;
                            color: $textColor; margin: 20px 0; border-radius: 8px;">
                            $result
                        </div>"""
                    }}
                    <table style="width: 100%; border-collapse: collapse; margin-top: 8px;">
                        <tr>
                            <td style="padding: 8px; border: 1px solid #ddd; width: 30%;"><strong>Restrições:</strong></td>
                            <td style="padding: 8px; border: 1px solid #ddd;">${aso.restrictions.orEmpty().ifEmpty { "Nenhuma restrição" }}</td>
                        </tr>
                        ${if (aso.complementaryExams.isNotEmpty()) {
                            val items = aso.complementaryExams.joinToString("") { "<li>$it</li>" }
                            """<tr>
                                <td style="padding: 8px; border: 1px solid #ddd;"><strong>Exames Complementares:</strong></td>
                                <td style="padding: 8px; border: 1px solid #ddd;"><ul style="margin: 0; padding-left: 20px;">$items</ul></td>
                            </tr>"""
                        } else ""}
                    </table>
                </section>

                ${if (!aso.observations.isNullOrBlank()) {
                    """<!-- OBSERVAÇÕES -->
                <section style="margin-top: 20px;">
                    <h3>7. OBSERVAÇÕES</h3>
                    <div style="padding: 12px; border: 1px solid #ddd; background-color: #FFFDE7; white-space: pre-wrap;">${aso.observations}</div>
                </section>"""
                } else ""}

                <!-- ASSINATURA -->
                <section style="margin-top: 40px;">
                    <table style="width: 100%; margin-top: 40px;">
                        <tr>
                            <td style="text-align: center; border-top: 1px solid #333; padding-top: 10px;">
                                ${aso.occupationalDoctor}<br/>
                                CRM: ${aso.occupationalDoctorCRM}
                            </td>
                            <td style="text-align: center; border-top: 1px solid #333; padding-top: 10px;">
                                ${dateFormat.format(Date(aso.examinationDate))}
                            </td>
                        </tr>
                    </table>
                </section>
                
                <!-- RODAPÉ -->
                <section style="margin-top: 40px; padding: 20px; background-color: #f9f9f9; border-top: 2px solid #ddd; font-size: 11px; color: #666;">
                    <p><strong>Documento gerado por ANDA</strong> - Plataforma de Segurança e Medicina do Trabalho</p>
                    <p>ID: ${aso.id} | Gerado em: ${dateTimeFormat.format(Date(aso.timestamp))}</p>
                    <p style="margin-top: 10px; font-style: italic;">
                        ${AsoOperationalPolicy.HTML_FOOTER_DISCLAIMER}
                    </p>
                </section>
            </div>
        """.trimIndent())

        if (includeHeader) {
            sb.append("</body>\n</html>")
        }

        val generationTimeMs = System.currentTimeMillis() - startTime
        analyticsService?.let {
            android.util.Log.d("DocumentGeneration", "ASO generated in ${generationTimeMs}ms")
        }

        return sb.toString()
    }

    /**
     * Gera um PCMSO (Programa de Controle Médico de Saúde Ocupacional)
     */
    fun generatePCMSO(
        pcmso: OccupationalHealthControlProgram,
        includeHeader: Boolean = true
    ): String {
        val sb = StringBuilder()

        if (includeHeader) {
            sb.append(generateHTMLHeader(pcmso.clinicName))
        }

        sb.append("""
            <div style="page-break-before: always;">
                <h1 style="text-align: center; text-transform: uppercase;">Programa de Controle Médico de Saúde Ocupacional (PCMSO)</h1>
                <p style="text-align: center; font-weight: bold;">NR-7 (Ministério do Trabalho)</p>
                ${if (!pcmso.responsibilities.isNullOrBlank()) "<p style=\"text-align: center; font-size: 14px; color: #555;\">${pcmso.responsibilities}</p>" else ""}
                <hr style="border: 1px solid #333;">
                
                <!-- DADOS DA CLÍNICA -->
                <section style="margin-top: 20px;">
                    <h3>1. RESPONSÁVEL TÉCNICO</h3>
                    <table style="width: 100%; border-collapse: collapse;">
                        <tr>
                            <td style="padding: 8px; border: 1px solid #ddd; width: 50%;"><strong>Clínica:</strong> ${pcmso.clinicName}</td>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>CNPJ:</strong> ${formatCNPJ(pcmso.clinicCNPJ)}</td>
                        </tr>
                        <tr>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>Médico Coordenador:</strong> ${pcmso.occupationalDoctor}</td>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>CRM:</strong> ${pcmso.occupationalDoctorCRM}</td>
                        </tr>
                    </table>
                </section>
                
                <!-- DADOS DA EMPRESA -->
                <section style="margin-top: 20px;">
                    <h3>2. DADOS DA EMPRESA</h3>
                    <table style="width: 100%; border-collapse: collapse;">
                        <tr>
                            <td style="padding: 8px; border: 1px solid #ddd; width: 50%;"><strong>Razão Social:</strong> ${pcmso.companyProfile.legalName}</td>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>CNPJ:</strong> ${formatCNPJ(pcmso.companyProfile.cnpj)}</td>
                        </tr>
                        <tr>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>CNAE:</strong> ${pcmso.companyProfile.cnae}</td>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>Ano de Referência:</strong> ${pcmso.year}</td>
                        </tr>
                    </table>
                </section>
                
                <!-- AVALIAÇÃO DE RISCOS -->
                <section style="margin-top: 20px;">
                    <h3>3. AVALIAÇÃO DE RISCOS OCUPACIONAIS</h3>
                    <div style="padding: 12px; border: 1px solid #ddd; background-color: #f5f5f5;">
                        ${pcmso.riskAssessment}
                    </div>
                </section>
                
                <!-- OBJETIVOS -->
                <section style="margin-top: 20px;">
                    <h3>4. OBJETIVOS DO PROGRAMA</h3>
                    <ul>
                        ${pcmso.objectives.joinToString("\n") { "<li>$it</li>" }}
                    </ul>
                </section>
                
                <!-- CRONOGRAMA -->
                <section style="margin-top: 20px;">
                    <h3>5. CRONOGRAMA DE EXAMES</h3>
                    <table style="width: 100%; border-collapse: collapse;">
                        <thead>
                            <tr style="background-color: #f0f0f0;">
                                <th style="padding: 8px; border: 1px solid #ddd;">Tipo de Exame</th>
                                <th style="padding: 8px; border: 1px solid #ddd;">Frequência</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${pcmso.examinationSchedule.entries.joinToString("\n") { (type, frequency) ->
                                """
                                <tr>
                                    <td style="padding: 8px; border: 1px solid #ddd;">${translateExaminationType(type)}</td>
                                    <td style="padding: 8px; border: 1px solid #ddd;">$frequency</td>
                                </tr>
                                """.trimIndent()
                            }}
                        </tbody>
                    </table>
                </section>
                
                <!-- EXAMES COMPLEMENTARES -->
                <section style="margin-top: 20px;">
                    <h3>6. EXAMES COMPLEMENTARES</h3>
                    <ul>
                        ${pcmso.complementaryExams.joinToString("\n") { "<li>$it</li>" }}
                    </ul>
                </section>
                
                <!-- RESPONSABILIDADES -->
                <section style="margin-top: 20px;">
                    <h3>7. RESPONSABILIDADES</h3>
                    <div style="padding: 12px; border: 1px solid #ddd; background-color: #fffacd;">
                        <p><strong>Empresa:</strong> Cumprir as determinações do PCMSO e fornecer afastamentos para realização de exames.</p>
                        <p><strong>Médico Coordenador:</strong> Responsável pela condução do programa e emissão de parecer técnico.</p>
                        <p><strong>Colaboradores:</strong> Participar dos exames conforme cronograma estabelecido.</p>
                    </div>
                </section>
                ${if (!pcmso.observations.isNullOrBlank()) """
                <!-- OBSERVAÇÕES -->
                <section style="margin-top: 20px;">
                    <h3>8. OBSERVAÇÕES</h3>
                    <div style="padding: 12px; border: 1px solid #ddd; background-color: #f9f9f9;">
                        ${pcmso.observations}
                    </div>
                </section>
                """ else ""}
                <!-- ASSINATURA -->
                <section style="margin-top: 40px;">
                    <table style="width: 100%; margin-top: 40px;">
                        <tr>
                            <td style="text-align: center; border-top: 1px solid #333; padding-top: 10px;">
                                ${pcmso.occupationalDoctor}<br/>
                                CRM: ${pcmso.occupationalDoctorCRM}
                            </td>
                            <td style="text-align: center; border-top: 1px solid #333; padding-top: 10px;">
                                ${dateFormat.format(Date(System.currentTimeMillis()))}
                            </td>
                        </tr>
                    </table>
                </section>
            </div>
        """.trimIndent())

        if (includeHeader) {
            sb.append("</body>\n</html>")
        }

        return sb.toString()
    }

    /**
     * Gera um PGR (Programa de Gerenciamento de Riscos - NR-01)
     */
    fun generatePGR(
        pgr: RiskManagementProgram,
        includeHeader: Boolean = true
    ): String {
        val sb = StringBuilder()
        if (includeHeader) sb.append(generateHTMLHeader(pgr.companyProfile.legalName))

        sb.append("""
            <div style="page-break-before: always;">
                <h1 style="text-align: center; text-transform: uppercase;">Programa de Gerenciamento de Riscos (PGR)</h1>
                <p style="text-align: center; font-weight: bold;">NR-01 (Portaria MTE 672/2021)</p>
                <hr style="border: 1px solid #333;">

                <section style="margin-top: 20px;">
                    <h3>1. DADOS DA EMPRESA</h3>
                    <table style="width: 100%; border-collapse: collapse;">
                        <tr>
                            <td style="padding: 8px; border: 1px solid #ddd; width: 50%;"><strong>Razão Social:</strong> ${pgr.companyProfile.legalName}</td>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>CNPJ:</strong> ${formatCNPJ(pgr.companyProfile.cnpj)}</td>
                        </tr>
                        <tr>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>CNAE:</strong> ${pgr.companyProfile.cnae}</td>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>Ano:</strong> ${pgr.year}</td>
                        </tr>
                        <tr>
                            <td colspan="2" style="padding: 8px; border: 1px solid #ddd;"><strong>Responsável Técnico:</strong> ${pgr.technician}${pgr.technicanCREA?.let { " | CREA: $it" } ?: ""}</td>
                        </tr>
                    </table>
                </section>

                <section style="margin-top: 20px;">
                    <h3>2. INVENTÁRIO DE RISCOS OCUPACIONAIS</h3>
                    <table style="width: 100%; border-collapse: collapse;">
                        <thead>
                            <tr style="background-color: #f0f0f0;">
                                <th style="padding: 8px; border: 1px solid #ddd;">Risco</th>
                                <th style="padding: 8px; border: 1px solid #ddd;">Nível</th>
                                <th style="padding: 8px; border: 1px solid #ddd;">NR Referência</th>
                                <th style="padding: 8px; border: 1px solid #ddd;">Medidas de Controle</th>
                                <th style="padding: 8px; border: 1px solid #ddd;">EPIs</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${pgr.identifiedRisks.joinToString("\n") { risk ->
                                val medidas = risk.controlMeasures.joinToString("; ")
                                val epis = risk.epiRequired.joinToString("; ").ifEmpty { "-" }
                                """<tr>
                                    <td style="padding: 8px; border: 1px solid #ddd;">${risk.name}</td>
                                    <td style="padding: 8px; border: 1px solid #ddd;">${translateRiskLevel(risk.level)}</td>
                                    <td style="padding: 8px; border: 1px solid #ddd;">${risk.normativeReference}</td>
                                    <td style="padding: 8px; border: 1px solid #ddd;">$medidas</td>
                                    <td style="padding: 8px; border: 1px solid #ddd;">$epis</td>
                                </tr>""".trimIndent()
                            }}
                        </tbody>
                    </table>
                </section>

                <section style="margin-top: 20px;">
                    <h3>3. PLANO DE AÇÃO</h3>
                    <table style="width: 100%; border-collapse: collapse;">
                        <thead>
                            <tr style="background-color: #f0f0f0;">
                                <th style="padding: 8px; border: 1px solid #ddd;">Risco</th>
                                <th style="padding: 8px; border: 1px solid #ddd;">Ações Previstas</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${pgr.controlMeasures.entries.joinToString("\n") { (risco, acoes) ->
                                """<tr>
                                    <td style="padding: 8px; border: 1px solid #ddd;">$risco</td>
                                    <td style="padding: 8px; border: 1px solid #ddd;">${acoes.joinToString("; ")}</td>
                                </tr>""".trimIndent()
                            }.ifEmpty { """<tr><td colspan="2" style="padding: 8px; border: 1px solid #ddd;">Sem plano de acao especifico cadastrado.</td></tr>""" }}
                        </tbody>
                    </table>
                </section>

                ${pgr.timeline?.let { """
                <section style="margin-top: 20px;">
                    <h3>4. CRONOGRAMA DE IMPLEMENTAÇÃO</h3>
                    <div style="padding: 12px; border: 1px solid #ddd; background-color: #f5f5f5;">$it</div>
                </section>""".trimIndent() } ?: ""}

                ${pgr.observations?.let { """
                <section style="margin-top: 20px;">
                    <h3>5. OBSERVAÇÕES</h3>
                    <div style="padding: 12px; border: 1px solid #ddd;">$it</div>
                </section>""".trimIndent() } ?: ""}

                <section style="margin-top: 40px;">
                    <table style="width: 100%; margin-top: 40px;">
                        <tr>
                            <td style="text-align: center; border-top: 1px solid #333; padding-top: 10px;">
                                ${pgr.technician}<br/>${pgr.technicanCREA?.let { "CREA: $it" } ?: ""}
                            </td>
                            <td style="text-align: center; border-top: 1px solid #333; padding-top: 10px;">
                                ${dateFormat.format(Date(pgr.timestamp))}
                            </td>
                        </tr>
                    </table>
                </section>

                <section style="margin-top: 40px; padding: 20px; background-color: #f9f9f9; border-top: 2px solid #ddd; font-size: 11px; color: #666;">
                    <p><strong>Documento gerado por ANDA</strong> - Plataforma de Segurança e Medicina do Trabalho</p>
                    <p>ID: ${pgr.id} | Gerado em: ${dateTimeFormat.format(Date(pgr.timestamp))}</p>
                </section>
            </div>
        """.trimIndent())

        if (includeHeader) sb.append("</body>\n</html>")
        return sb.toString()
    }

    /**
     * Gera uma OS (Ordem de Serviço) — exigida pela NR-01 para todos os trabalhadores
     */
    fun generateOS(
        companyCnpj: String,
        companyName: String,
        employeeName: String,
        employeeCpf: String,
        employeeRole: String,
        sector: String,
        activities: String,
        risks: List<OccupationalRisk>,
        responsibleName: String,
        includeHeader: Boolean = true
    ): String {
        val now = System.currentTimeMillis()
        val id = "os-$now"
        val sb = StringBuilder()
        if (includeHeader) sb.append(generateHTMLHeader(companyName))

        sb.append("""
            <div>
                <h1 style="text-align: center; text-transform: uppercase;">Ordem de Serviço (OS)</h1>
                <p style="text-align: center; font-weight: bold;">NR-01 — Obrigatória para todos os trabalhadores</p>
                <hr style="border: 1px solid #333;">

                <section style="margin-top: 20px;">
                    <h3>1. DADOS DA EMPRESA</h3>
                    <table style="width: 100%; border-collapse: collapse;">
                        <tr>
                            <td style="padding: 8px; border: 1px solid #ddd; width: 55%;"><strong>Empresa:</strong> $companyName</td>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>CNPJ:</strong> ${formatCNPJ(companyCnpj)}</td>
                        </tr>
                    </table>
                </section>

                <section style="margin-top: 20px;">
                    <h3>2. DADOS DO TRABALHADOR</h3>
                    <table style="width: 100%; border-collapse: collapse;">
                        <tr>
                            <td style="padding: 8px; border: 1px solid #ddd; width: 55%;"><strong>Nome:</strong> $employeeName</td>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>CPF:</strong> ${formatCPF(employeeCpf)}</td>
                        </tr>
                        <tr>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>Função:</strong> $employeeRole</td>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>Setor:</strong> $sector</td>
                        </tr>
                    </table>
                </section>

                <section style="margin-top: 20px;">
                    <h3>3. ATIVIDADES EXERCIDAS</h3>
                    <div style="padding: 10px; border: 1px solid #ddd; min-height: 60px;">$activities</div>
                </section>

                <section style="margin-top: 20px;">
                    <h3>4. RISCOS IDENTIFICADOS E MEDIDAS PREVENTIVAS</h3>
                    <table style="width: 100%; border-collapse: collapse;">
                        <thead>
                            <tr style="background-color: #f0f0f0;">
                                <th style="padding: 8px; border: 1px solid #ddd;">Risco</th>
                                <th style="padding: 8px; border: 1px solid #ddd;">Nível</th>
                                <th style="padding: 8px; border: 1px solid #ddd;">EPIs Obrigatórios</th>
                                <th style="padding: 8px; border: 1px solid #ddd;">Medidas</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${risks.joinToString("\n") { r ->
                                """<tr>
                                    <td style="padding: 8px; border: 1px solid #ddd;">${r.name}</td>
                                    <td style="padding: 8px; border: 1px solid #ddd;">${translateRiskLevel(r.level)}</td>
                                    <td style="padding: 8px; border: 1px solid #ddd;">${r.epiRequired.joinToString("; ").ifEmpty { "-" }}</td>
                                    <td style="padding: 8px; border: 1px solid #ddd;">${r.controlMeasures.joinToString("; ")}</td>
                                </tr>""".trimIndent()
                            }.ifEmpty { """<tr><td colspan="4" style="padding: 8px; border: 1px solid #ddd;">Nenhum risco específico identificado.</td></tr>""" }}
                        </tbody>
                    </table>
                </section>

                <section style="margin-top: 30px; padding: 12px; border: 1px solid #ddd; background-color: #fffacd;">
                    <p><strong>Declaração:</strong> Declaro que recebi orientações sobre os riscos ocupacionais da minha função e as medidas preventivas, conforme NR-01.</p>
                </section>

                <section style="margin-top: 40px;">
                    <table style="width: 100%; margin-top: 20px;">
                        <tr>
                            <td style="text-align: center; border-top: 1px solid #333; padding-top: 10px; width: 50%;">
                                Trabalhador: $employeeName<br/>Data: ${dateFormat.format(java.util.Date(now))}
                            </td>
                            <td style="text-align: center; border-top: 1px solid #333; padding-top: 10px;">
                                Responsável: $responsibleName<br/>Data: ${dateFormat.format(java.util.Date(now))}
                            </td>
                        </tr>
                    </table>
                </section>

                <section style="margin-top: 20px; font-size: 11px; color: #666; border-top: 1px solid #ddd; padding-top: 8px;">
                    <p><strong>ANDA</strong> — ID: $id | ${dateTimeFormat.format(java.util.Date(now))}</p>
                </section>
            </div>
        """.trimIndent())

        if (includeHeader) sb.append("</body>\n</html>")
        return sb.toString()
    }

    /**
     * Gera um PPP (Perfil Profissiográfico Previdenciário)
     * Exigido pelo INSS para aposentadoria especial e benefícios previdenciários
     */
    fun generatePPP(
        companyCnpj: String,
        companyName: String,
        companyCnae: String,
        employeeName: String,
        employeeCpf: String,
        employeeRole: String,
        sector: String,
        admissionDate: String,
        dismissalDate: String,
        activities: String,
        risks: List<OccupationalRisk>,
        medicalExams: String,
        responsibleName: String,
        responsibleCpf: String,
        includeHeader: Boolean = true
    ): String {
        val now = System.currentTimeMillis()
        val id = "ppp-$now"
        val sb = StringBuilder()
        if (includeHeader) sb.append(generateHTMLHeader(companyName))

        sb.append("""
            <div>
                <h1 style="text-align: center; text-transform: uppercase;">Perfil Profissiográfico Previdenciário (PPP)</h1>
                <p style="text-align: center; font-weight: bold;">Art. 58 Lei 8.213/91 | IN INSS/PRES 77/2015</p>
                <hr style="border: 1px solid #333;">

                <section style="margin-top: 20px;">
                    <h3>1. DADOS DA EMPRESA</h3>
                    <table style="width: 100%; border-collapse: collapse;">
                        <tr>
                            <td style="padding: 8px; border: 1px solid #ddd; width: 55%;"><strong>Razão Social:</strong> $companyName</td>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>CNPJ:</strong> ${formatCNPJ(companyCnpj)}</td>
                        </tr>
                        <tr>
                            <td colspan="2" style="padding: 8px; border: 1px solid #ddd;"><strong>CNAE:</strong> $companyCnae</td>
                        </tr>
                    </table>
                </section>

                <section style="margin-top: 20px;">
                    <h3>2. DADOS DO TRABALHADOR</h3>
                    <table style="width: 100%; border-collapse: collapse;">
                        <tr>
                            <td style="padding: 8px; border: 1px solid #ddd; width: 55%;"><strong>Nome:</strong> $employeeName</td>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>CPF:</strong> ${formatCPF(employeeCpf)}</td>
                        </tr>
                        <tr>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>Função:</strong> $employeeRole</td>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>Setor:</strong> $sector</td>
                        </tr>
                        <tr>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>Admissão:</strong> $admissionDate</td>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>Desligamento:</strong> ${dismissalDate.ifBlank { "Ativo" }}</td>
                        </tr>
                    </table>
                </section>

                <section style="margin-top: 20px;">
                    <h3>3. ATIVIDADES E EXPOSIÇÕES</h3>
                    <div style="padding: 10px; border: 1px solid #ddd; min-height: 60px;">$activities</div>
                </section>

                <section style="margin-top: 20px;">
                    <h3>4. AGENTES NOCIVOS IDENTIFICADOS</h3>
                    <table style="width: 100%; border-collapse: collapse;">
                        <thead>
                            <tr style="background-color: #f0f0f0;">
                                <th style="padding: 8px; border: 1px solid #ddd;">Agente</th>
                                <th style="padding: 8px; border: 1px solid #ddd;">Nível / Intensidade</th>
                                <th style="padding: 8px; border: 1px solid #ddd;">NR Referência</th>
                                <th style="padding: 8px; border: 1px solid #ddd;">EPI Utilizado</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${risks.joinToString("\n") { r ->
                                """<tr>
                                    <td style="padding: 8px; border: 1px solid #ddd;">${r.name}</td>
                                    <td style="padding: 8px; border: 1px solid #ddd;">${r.measurementValue ?: translateRiskLevel(r.level)}</td>
                                    <td style="padding: 8px; border: 1px solid #ddd;">${r.normativeReference}</td>
                                    <td style="padding: 8px; border: 1px solid #ddd;">${r.epiRequired.joinToString("; ").ifEmpty { "-" }}</td>
                                </tr>""".trimIndent()
                            }.ifEmpty { """<tr><td colspan="4" style="padding: 8px; border: 1px solid #ddd;">Nenhum agente nocivo identificado.</td></tr>""" }}
                        </tbody>
                    </table>
                </section>

                <section style="margin-top: 20px;">
                    <h3>5. EXAMES MÉDICOS REALIZADOS</h3>
                    <div style="padding: 10px; border: 1px solid #ddd; min-height: 50px;">${medicalExams.ifBlank { "Conforme ASO(s) arquivado(s)." }}</div>
                </section>

                <section style="margin-top: 40px;">
                    <table style="width: 100%; margin-top: 20px;">
                        <tr>
                            <td style="text-align: center; border-top: 1px solid #333; padding-top: 10px; width: 50%;">
                                Responsável Técnico: $responsibleName<br/>CPF: ${formatCPF(responsibleCpf)}
                            </td>
                            <td style="text-align: center; border-top: 1px solid #333; padding-top: 10px;">
                                Data de Emissão: ${dateFormat.format(java.util.Date(now))}
                            </td>
                        </tr>
                    </table>
                </section>

                <section style="margin-top: 20px; font-size: 11px; color: #666; border-top: 1px solid #ddd; padding-top: 8px;">
                    <p><strong>ANDA</strong> — ID: $id | ${dateTimeFormat.format(java.util.Date(now))}</p>
                </section>
            </div>
        """.trimIndent())

        if (includeHeader) sb.append("</body>\n</html>")
        return sb.toString()
    }

    /**
     * Gera uma CAT (Comunicação de Acidente do Trabalho) em HTML
     * Prazo legal: até 24h após o acidente (art. 22 Lei 8.213/91)
     */
    fun generateCAT(
        companyCnpj: String,
        companyName: String,
        companyCnae: String,
        employeeName: String,
        employeeCpf: String,
        accidentDateTime: Long,
        accidentLocation: String,
        accidentDescription: String,
        bodyPartAffected: String,
        injuryType: String,
        medicalAttention: String,
        reportedBy: String,
        includeHeader: Boolean = true
    ): String {
        val id = "cat-${accidentDateTime}"
        val sb = StringBuilder()
        if (includeHeader) sb.append(generateHTMLHeader(companyName))

        sb.append("""
            <div style="page-break-before: always;">
                <h1 style="text-align: center; text-transform: uppercase;">Comunicação de Acidente do Trabalho (CAT)</h1>
                <p style="text-align: center; font-weight: bold; color: #c00;">Prazo legal: até 24h após o acidente (Lei 8.213/91, art. 22)</p>
                <hr style="border: 2px solid #c00;">

                <section style="margin-top: 20px;">
                    <h3>1. DADOS DA EMPRESA</h3>
                    <table style="width: 100%; border-collapse: collapse;">
                        <tr>
                            <td style="padding: 8px; border: 1px solid #ddd; width: 55%;"><strong>Razão Social:</strong> $companyName</td>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>CNPJ:</strong> ${formatCNPJ(companyCnpj)}</td>
                        </tr>
                        <tr>
                            <td colspan="2" style="padding: 8px; border: 1px solid #ddd;"><strong>CNAE:</strong> $companyCnae</td>
                        </tr>
                    </table>
                </section>

                <section style="margin-top: 20px;">
                    <h3>2. DADOS DO ACIDENTADO</h3>
                    <table style="width: 100%; border-collapse: collapse;">
                        <tr>
                            <td style="padding: 8px; border: 1px solid #ddd; width: 55%;"><strong>Nome:</strong> $employeeName</td>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>CPF:</strong> ${formatCPF(employeeCpf)}</td>
                        </tr>
                    </table>
                </section>

                <section style="margin-top: 20px;">
                    <h3>3. DADOS DO ACIDENTE</h3>
                    <table style="width: 100%; border-collapse: collapse;">
                        <tr>
                            <td style="padding: 8px; border: 1px solid #ddd; width: 55%;"><strong>Data e Hora:</strong> ${dateTimeFormat.format(java.util.Date(accidentDateTime))}</td>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>Local:</strong> $accidentLocation</td>
                        </tr>
                        <tr>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>Tipo de Lesão:</strong> $injuryType</td>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>Parte do Corpo:</strong> $bodyPartAffected</td>
                        </tr>
                        <tr>
                            <td colspan="2" style="padding: 8px; border: 1px solid #ddd;">
                                <strong>Descrição do Acidente:</strong><br/>$accidentDescription
                            </td>
                        </tr>
                        <tr>
                            <td colspan="2" style="padding: 8px; border: 1px solid #ddd;">
                                <strong>Atendimento Médico:</strong> $medicalAttention
                            </td>
                        </tr>
                    </table>
                </section>

                <section style="margin-top: 30px; padding: 12px; border: 2px solid #c00; background-color: #fff5f5;">
                    <p style="color: #c00; font-weight: bold;">⚠ ATENÇÃO: Esta CAT deve ser registrada no portal da Previdência Social (www.gov.br/inss) e entregue ao INSS, médico e ao empregado dentro do prazo legal.</p>
                </section>

                <section style="margin-top: 40px;">
                    <table style="width: 100%; margin-top: 30px;">
                        <tr>
                            <td style="text-align: center; border-top: 1px solid #333; padding-top: 10px; width: 50%;">
                                Responsável pela Emissão: $reportedBy<br/>Data: ${dateFormat.format(java.util.Date(System.currentTimeMillis()))}
                            </td>
                            <td style="text-align: center; border-top: 1px solid #333; padding-top: 10px;">
                                Assinatura do Empregador / Técnico
                            </td>
                        </tr>
                    </table>
                </section>

                <section style="margin-top: 20px; padding: 12px; background-color: #f9f9f9; border-top: 1px solid #ddd; font-size: 11px; color: #666;">
                    <p><strong>Documento gerado por ANDA</strong> - ID: $id | ${dateTimeFormat.format(java.util.Date(System.currentTimeMillis()))}</p>
                </section>
            </div>
        """.trimIndent())

        if (includeHeader) sb.append("</body>\n</html>")
        return sb.toString()
    }

    /**
     * Gera um APR (Análise Preliminar de Risco)
     * Documentos simples e rápidos para atividades eventuais/emergenciais
     */
    fun generateAPR(
        taskName: String,
        companyProfile: com.example.anda.domain.CompanyProfile,
        technician: String,
        risks: List<OccupationalRisk>,
        authorizations: List<String> = emptyList(),
        includeHeader: Boolean = true
    ): String {
        val now = System.currentTimeMillis()
        val sb = StringBuilder()
        if (includeHeader) sb.append(generateHTMLHeader(companyProfile.legalName))

        sb.append("""
            <div style="page-break-before: always;">
                <h1 style="text-align: center; text-transform: uppercase;">Análise Preliminar de Risco (APR)</h1>
                <hr style="border: 1px solid #333;">

                <section style="margin-top: 20px;">
                    <h3>1. IDENTIFICAÇÃO</h3>
                    <table style="width: 100%; border-collapse: collapse;">
                        <tr>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>Empresa:</strong> ${companyProfile.legalName}</td>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>CNPJ:</strong> ${formatCNPJ(companyProfile.cnpj)}</td>
                        </tr>
                        <tr>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>Atividade / Tarefa:</strong> $taskName</td>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>Responsável Técnico:</strong> $technician</td>
                        </tr>
                        <tr>
                            <td colspan="2" style="padding: 8px; border: 1px solid #ddd;"><strong>Data:</strong> ${dateFormat.format(Date(now))}</td>
                        </tr>
                    </table>
                </section>

                <section style="margin-top: 20px;">
                    <h3>2. IDENTIFICAÇÃO DE RISCOS E MEDIDAS</h3>
                    <table style="width: 100%; border-collapse: collapse;">
                        <thead>
                            <tr style="background-color: #f0f0f0;">
                                <th style="padding: 8px; border: 1px solid #ddd; width: 5%;">#</th>
                                <th style="padding: 8px; border: 1px solid #ddd;">Risco Identificado</th>
                                <th style="padding: 8px; border: 1px solid #ddd;">Nivel</th>
                                <th style="padding: 8px; border: 1px solid #ddd;">Medidas de Controle / EPIs</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${risks.mapIndexed { i, risk ->
                                val controles = (risk.controlMeasures + risk.epiRequired).joinToString("; ").ifEmpty { "-" }
                                """<tr>
                                    <td style="padding: 8px; border: 1px solid #ddd; text-align: center;">${i+1}</td>
                                    <td style="padding: 8px; border: 1px solid #ddd;">${risk.name}: ${risk.description}</td>
                                    <td style="padding: 8px; border: 1px solid #ddd;">${translateRiskLevel(risk.level)}</td>
                                    <td style="padding: 8px; border: 1px solid #ddd;">$controles</td>
                                </tr>""".trimIndent()
                            }.joinToString("\n").ifEmpty { """<tr><td colspan="4" style="padding: 8px; border: 1px solid #ddd;">Nenhum risco identificado.</td></tr>""" }}
                        </tbody>
                    </table>
                </section>

                <section style="margin-top: 20px;">
                    <h3>3. DECLARAÇÃO DE CIÊNCIA</h3>
                    <p style="margin-top: 8px;">Os trabalhadores abaixo declaram ter sido informados sobre os riscos e medidas de controle desta APR:</p>
                    <table style="width: 100%; border-collapse: collapse; margin-top: 8px;">
                        <thead>
                            <tr style="background-color: #f0f0f0;">
                                <th style="padding: 8px; border: 1px solid #ddd;">Nome</th>
                                <th style="padding: 8px; border: 1px solid #ddd;">Assinatura</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${authorizations.mapIndexed { i, name ->
                                """<tr>
                                    <td style="padding: 8px; border: 1px solid #ddd;">$name</td>
                                    <td style="padding: 24px; border: 1px solid #ddd;"></td>
                                </tr>""".trimIndent()
                            }.joinToString("\n").ifEmpty {
                                (1..3).joinToString("\n") { """<tr><td style="padding: 8px; border: 1px solid #ddd;"></td><td style="padding: 24px; border: 1px solid #ddd;"></td></tr>""" }
                            }}
                        </tbody>
                    </table>
                </section>

                <section style="margin-top: 40px;">
                    <table style="width: 100%; margin-top: 30px;">
                        <tr>
                            <td style="text-align: center; border-top: 1px solid #333; padding-top: 10px; width: 50%;">
                                Responsável Técnico: $technician<br/>Data: ${dateFormat.format(Date(now))}
                            </td>
                            <td style="text-align: center; border-top: 1px solid #333; padding-top: 10px;">
                                Encarregado / Supervisor<br/>Assinatura:
                            </td>
                        </tr>
                    </table>
                </section>
            </div>
        """.trimIndent())

        if (includeHeader) sb.append("</body>\n</html>")
        return sb.toString()
    }

    fun generateGenericSstDocument(
        input: GenericSstDocumentInput,
        includeHeader: Boolean = true
    ): String {
        val normalizedType = normalizeGenericType(input.documentType)
        val complianceChecklist = buildComplianceChecklist(normalizedType)
        val operationalAlerts = buildOperationalAlerts(normalizedType)
        val reviewWindow = recommendedReviewWindow(normalizedType)
        val specializedRows = buildSpecializedRows(normalizedType, input.specializedFields)
        val parsedActions = parseRecommendations(input.recommendations)

        val safeDocumentTitle = escapeHtml(input.documentTitle)
        val safeNormativeReference = escapeHtml(input.normativeReference.ifBlank { "Documento SST" })
        val safeCompanyName = escapeHtml(input.companyName)
        val safeCompanyCnae = escapeHtml(input.companyCnae.ifBlank { "Não informado" })
        val safeObjective = renderMultilineText(input.objective.ifBlank { "Não informado" })
        val safeScope = renderMultilineText(input.scope.ifBlank { "Não informado" })
        val safeTechnicalDetails = renderMultilineText(input.technicalDetails.ifBlank { "Não informado" })
        val safeResponsibleName = escapeHtml(input.responsibleName.ifBlank { "Responsável técnico" })
        val safeFallbackRecommendation = renderMultilineText(input.recommendations.ifBlank { "Não informado" })

        val sb = StringBuilder()
        if (includeHeader) sb.append(generateHTMLHeader(input.companyName.ifBlank { "ANDA" }))

        sb.append(
            """
            <div style="page-break-before: always;">
                <h1 style="text-align: center; text-transform: uppercase;">$safeDocumentTitle</h1>
                <p style="text-align: center; font-weight: bold;">$safeNormativeReference</p>
                <hr style="border: 1px solid #333;">

                <section style="margin-top: 20px;">
                    <h3>1. IDENTIFICAÇÃO DA EMPRESA</h3>
                    <table style="width: 100%; border-collapse: collapse;">
                        <tr>
                            <td style="padding: 8px; border: 1px solid #ddd; width: 50%;"><strong>Razão Social:</strong> $safeCompanyName</td>
                            <td style="padding: 8px; border: 1px solid #ddd;"><strong>CNPJ:</strong> ${formatCNPJ(input.companyCnpj)}</td>
                        </tr>
                        <tr>
                            <td colspan="2" style="padding: 8px; border: 1px solid #ddd;"><strong>CNAE:</strong> $safeCompanyCnae</td>
                        </tr>
                    </table>
                </section>

                <section style="margin-top: 20px;">
                    <h3>2. OBJETIVO</h3>
                    <div style="padding: 12px; border: 1px solid #ddd; background-color: #f5f5f5;">
                        $safeObjective
                    </div>
                </section>

                <section style="margin-top: 20px;">
                    <h3>3. ESCOPO</h3>
                    <div style="padding: 12px; border: 1px solid #ddd;">
                        $safeScope
                    </div>
                </section>

                <section style="margin-top: 20px;">
                    <h3>4. CRITÉRIOS NORMATIVOS MÍNIMOS</h3>
                    <table style="width: 100%; border-collapse: collapse;">
                        <thead>
                            <tr style="background-color: #f0f0f0;">
                                <th style="padding: 8px; border: 1px solid #ddd; width: 50px;">Status</th>
                                <th style="padding: 8px; border: 1px solid #ddd;">Item de verificação</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${complianceChecklist.joinToString("\n") { item ->
                                """
                                <tr>
                                    <td style="padding: 8px; border: 1px solid #ddd; text-align: center;">☐</td>
                                    <td style="padding: 8px; border: 1px solid #ddd;">$item</td>
                                </tr>
                                """.trimIndent()
                            }}
                        </tbody>
                    </table>
                </section>

                <section style="margin-top: 20px;">
                    <h3>5. DESENVOLVIMENTO TÉCNICO</h3>
                    <div style="padding: 12px; border: 1px solid #ddd; min-height: 120px;">
                        $safeTechnicalDetails
                    </div>
                </section>

                ${if (specializedRows.isNotEmpty()) {
                    """
                    <section style="margin-top: 20px;">
                        <h3>5.1 CAMPOS ESPECÍFICOS</h3>
                        <table style="width: 100%; border-collapse: collapse;">
                            <thead>
                                <tr style="background-color: #f0f0f0;">
                                    <th style="padding: 8px; border: 1px solid #ddd; width: 35%;">Campo</th>
                                    <th style="padding: 8px; border: 1px solid #ddd;">Valor</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${specializedRows.joinToString("\n") { (label, value) ->
                                    """
                                    <tr>
                                        <td style="padding: 8px; border: 1px solid #ddd;"><strong>${escapeHtml(label)}</strong></td>
                                        <td style="padding: 8px; border: 1px solid #ddd;">${renderMultilineText(value)}</td>
                                    </tr>
                                    """.trimIndent()
                                }}
                            </tbody>
                        </table>
                    </section>
                    """.trimIndent()
                } else ""}

                <section style="margin-top: 20px;">
                    <h3>6. RECOMENDAÇÕES E PLANO DE AÇÃO</h3>
                    <table style="width: 100%; border-collapse: collapse;">
                        <thead>
                            <tr style="background-color: #f0f0f0;">
                                <th style="padding: 8px; border: 1px solid #ddd; width: 40px;">#</th>
                                <th style="padding: 8px; border: 1px solid #ddd;">Ação</th>
                                <th style="padding: 8px; border: 1px solid #ddd; width: 160px;">Responsável</th>
                                <th style="padding: 8px; border: 1px solid #ddd; width: 110px;">Prazo</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${parsedActions.mapIndexed { index, action ->
                                """
                                <tr>
                                    <td style="padding: 8px; border: 1px solid #ddd; text-align: center;">${index + 1}</td>
                                    <td style="padding: 8px; border: 1px solid #ddd;">${renderMultilineText(action)}</td>
                                    <td style="padding: 8px; border: 1px solid #ddd;">$safeResponsibleName</td>
                                    <td style="padding: 8px; border: 1px solid #ddd;">Definir</td>
                                </tr>
                                """.trimIndent()
                            }.joinToString("\n").ifEmpty {
                                """
                                <tr>
                                    <td style="padding: 8px; border: 1px solid #ddd; text-align: center;">1</td>
                                    <td style="padding: 8px; border: 1px solid #ddd;">$safeFallbackRecommendation</td>
                                    <td style="padding: 8px; border: 1px solid #ddd;">$safeResponsibleName</td>
                                    <td style="padding: 8px; border: 1px solid #ddd;">Definir</td>
                                </tr>
                                """.trimIndent()
                            }}
                        </tbody>
                    </table>
                </section>

                <section style="margin-top: 20px;">
                    <h3>7. VALIDAÇÃO OPERACIONAL</h3>
                    <p><strong>Tipo:</strong> $normalizedType</p>
                    <p><strong>Janela sugerida de reavaliação:</strong> $reviewWindow</p>
                    <ul>
                        ${operationalAlerts.joinToString("\n") { "<li>$it</li>" }}
                    </ul>
                </section>

                <section style="margin-top: 40px;">
                    <table style="width: 100%; margin-top: 30px;">
                        <tr>
                            <td style="text-align: center; border-top: 1px solid #333; padding-top: 10px; width: 60%;">
                                $safeResponsibleName
                            </td>
                            <td style="text-align: center; border-top: 1px solid #333; padding-top: 10px;">
                                ${formatDate(input.issueDate)}
                            </td>
                        </tr>
                    </table>
                </section>

                <section style="margin-top: 30px; padding: 16px; background-color: #f9f9f9; border-top: 2px solid #ddd; font-size: 11px; color: #666;">
                    <p><strong>Documento gerado por ANDA</strong> - Plataforma de Segurança e Medicina do Trabalho</p>
                    <p>ID: ${escapeHtml(input.documentId)} | Tipo: $normalizedType | Emitido em: ${formatDateTime(input.issueDate)}</p>
                </section>
            </div>
            """.trimIndent()
        )

        if (includeHeader) sb.append("</body>\n</html>")
        return sb.toString()
    }

    private fun normalizeGenericType(rawType: String): String {
        return DocumentTypeNormalizer.normalize(rawType)
    }

    private fun buildComplianceChecklist(documentType: String): List<String> {
        val common = listOf(
            "Identificação da empresa, CNPJ e CNAE validados",
            "Responsável técnico indicado no documento",
            "Escopo alinhado com atividade real e evidências registradas"
        )

        val specific = when (documentType) {
            "AET" -> listOf(
                "Análise da organização do trabalho e fatores psicossociais",
                "Levantamento de postura, repetitividade e esforço físico",
                "Recomendações ergonômicas com prioridade por risco"
            )
            "LTCAT" -> listOf(
                "Caracterização de agentes nocivos por ambiente e função",
                "Registro de metodologia de avaliação quantitativa/qualitativa",
                "Conclusão técnica para enquadramento previdenciário"
            )
            "LAUDO_NR10" -> listOf(
                "Verificação de proteção coletiva e bloqueio/etiquetagem",
                "Comprovação de capacitação e autorização para eletricidade",
                "Inspeção de prontuário de instalações elétricas"
            )
            "LAUDO_NR12" -> listOf(
                "Inventário de máquinas, zonas de perigo e proteções",
                "Validação de parada de emergência e intertravamentos",
                "Compatibilidade com manual, operação e manutenção segura"
            )
            "LAUDO_NR20" -> listOf(
                "Classificação de instalações com inflamáveis/combustíveis",
                "Controles de prevenção, detecção e resposta a emergência",
                "Treinamentos e permissões de trabalho mapeados"
            )
            "PT" -> listOf(
                "Descrição da atividade crítica e limites da autorização",
                "Bloqueios, liberações e EPI/EPC obrigatórios",
                "Assinaturas de emitente, executante e autorizador"
            )
            "INVENTARIO" -> listOf(
                "Levantamento de perigos por setor, atividade e função",
                "Critérios de severidade, probabilidade e priorização documentados",
                "Registro de evidências e vínculo com plano de ação do PGR"
            )
            "MAPA_RISCO" -> listOf(
                "Setorização e representação visual dos grupos de risco",
                "Validação em conjunto com CIPA e responsáveis operacionais",
                "Atualização após mudança de layout, processo ou ocorrência relevante"
            )
            "RESGATE" -> listOf(
                "Cenários de emergência e gatilhos de acionamento definidos",
                "Equipe, recursos e tempos de resposta testados em simulado",
                "Integração com procedimentos de altura/espaço confinado quando aplicável"
            )
            "PGRTR" -> listOf(
                "Inventário de riscos por frente de trabalho rural/temática",
                "Medidas de prevenção compatíveis com sazonalidade e operação",
                "Plano de ação com responsáveis e periodicidade de reavaliação"
            )
            else -> listOf(
                "Referência normativa principal registrada",
                "Plano de ação com responsáveis e evidências mínimas"
            )
        }
        return common + specific
    }

    private fun buildOperationalAlerts(documentType: String): List<String> {
        return when (documentType) {
            "AET" -> listOf(
                "Reavaliar quando houver mudança de posto, processo ou jornada.",
                "Priorizar medidas de engenharia antes de controles administrativos."
            )
            "LTCAT" -> listOf(
                "Atualizar em caso de alteração de exposição ou layout produtivo.",
                "Manter rastreabilidade das medições e dos equipamentos utilizados."
            )
            "LAUDO_NR10", "LAUDO_NR12", "LAUDO_NR20" -> listOf(
                "Registrar não conformidades críticas com bloqueio imediato da atividade.",
                "Vincular plano de ação a responsáveis e prazos verificáveis."
            )
            "PT" -> listOf(
                "A PT deve ser encerrada ao final da atividade ou em mudança de cenário.",
                "Atividade sem liberação formal deve permanecer bloqueada."
            )
            "INVENTARIO" -> listOf(
                "Riscos sem evidência mínima devem ser revisitados antes da aprovação final.",
                "Mudanças de processo exigem atualização do inventário e comunicação formal."
            )
            "MAPA_RISCO" -> listOf(
                "Mapa deve permanecer acessível e visível para os trabalhadores do setor.",
                "Alterações de risco exigem revisão do mapa e registro da nova versão."
            )
            "RESGATE" -> listOf(
                "Sem simulado e recursos mínimos, o plano deve ser tratado como não conforme.",
                "Alteração de equipe/rota de acesso exige revalidação imediata do plano."
            )
            "PGRTR" -> listOf(
                "Atividades sazonais devem ter controles revalidados antes de cada ciclo.",
                "Riscos críticos sem controle efetivo devem gerar bloqueio operacional."
            )
            else -> listOf(
                "Revisar documento periodicamente e após incidentes relevantes.",
                "Garantir comunicação formal das ações aos responsáveis operacionais."
            )
        }
    }

    private fun recommendedReviewWindow(documentType: String): String {
        return when (documentType) {
            "PT" -> "Revalidação por turno ou por atividade"
            "LTCAT" -> "Revisão anual ou a cada mudança de exposição"
            "AET" -> "Revisão anual e após mudanças de processo"
            "LAUDO_NR10", "LAUDO_NR12", "LAUDO_NR20" -> "Revisão semestral ou após não conformidade crítica"
            "INVENTARIO" -> "Revisão semestral e após incidentes/mudanças relevantes"
            "MAPA_RISCO" -> "Revisão semestral ou após alteração de layout/processo"
            "RESGATE" -> "Revisão trimestral com simulado periódico"
            "PGRTR" -> "Revisão por ciclo operacional/safra e no mínimo anual"
            else -> "Revisão anual"
        }
    }

    private fun buildSpecializedRows(
        documentType: String,
        specializedFields: Map<String, String>
    ): List<Pair<String, String>> {
        if (specializedFields.isEmpty()) return emptyList()

        val keyOrder = when (documentType) {
            "AET" -> listOf("aet_workstation", "aet_task")
            "LTCAT" -> listOf("ltcat_agent", "ltcat_intensity", "ltcat_habituality")
            "PT" -> listOf("pt_issuer", "pt_executor", "pt_authorizer")
            "LAUDO_NR10", "LAUDO_NR12", "LAUDO_NR20" -> listOf("nr_asset", "nr_critical", "nr_deadline")
            "PCA" -> listOf("pca_risk", "pca_measure")
            "PPR" -> listOf("ppr_epi", "ppr_fit")
            "INSALUBRIDADE" -> listOf("insalubridade_agent", "insalubridade_grau")
            "PERICULOSIDADE" -> listOf("periculosidade_agent", "periculosidade_exp")
            "PLANO_ACAO" -> listOf("plano_acao_risco", "plano_acao_acao")
            // Block 4
            "RESGATE" -> listOf("resgate_cenario", "resgate_equipe")
            "INVENTARIO" -> listOf("inventario_setor", "inventario_risco")
            "PGRTR" -> listOf("pgrtr_atividade", "pgrtr_risco")
            "MAPA_RISCO" -> listOf("mapa_setor", "mapa_risco_grave")
            else -> specializedFields.keys.toList()
        }

        val keyLabels = mapOf(
            "aet_workstation" to "AET - Posto de trabalho",
            "aet_task" to "AET - Atividade analisada",
            "ltcat_agent" to "LTCAT - Agente nocivo",
            "ltcat_intensity" to "LTCAT - Intensidade/Concentração",
            "ltcat_habituality" to "LTCAT - Habitualidade/Permanência",
            "pt_issuer" to "PT - Emitente",
            "pt_executor" to "PT - Executante",
            "pt_authorizer" to "PT - Autorizador",
            "nr_asset" to "NR - Equipamento/Área avaliada",
            "nr_critical" to "NR - Não conformidade crítica",
            "nr_deadline" to "NR - Prazo de adequação",
            "pca_risk" to "PCA - Risco identificado",
            "pca_measure" to "PCA - Medida de controle",
            "ppr_epi" to "PPR - EPI obrigatório",
            "ppr_fit" to "PPR - Teste de vedação",
            "insalubridade_agent" to "Insalubridade - Agente nocivo",
            "insalubridade_grau" to "Insalubridade - Grau",
            "periculosidade_agent" to "Periculosidade - Agente perigoso",
            "periculosidade_exp" to "Periculosidade - Exposição",
            "plano_acao_risco" to "Plano de Ação - Risco prioritário",
            "plano_acao_acao" to "Plano de Ação - Ação corretiva",
            // Block 4
            "resgate_cenario" to "Resgate - Cenário de emergência",
            "resgate_equipe" to "Resgate - Equipe de resgate",
            "inventario_setor" to "Inventário - Setor avaliado",
            "inventario_risco" to "Inventário - Risco principal",
            "pgrtr_atividade" to "PGRTR - Atividade principal",
            "pgrtr_risco" to "PGRTR - Risco específico",
            "mapa_setor" to "Mapa de Risco - Setor mapeado",
            "mapa_risco_grave" to "Mapa de Risco - Risco grave identificado"
        )

        return keyOrder.mapNotNull { key ->
            val value = specializedFields[key]?.trim().orEmpty()
            if (value.isBlank()) return@mapNotNull null
            (keyLabels[key] ?: key) to value
        }
    }

    /**
     * Gera header HTML padrão
     */
    private fun generateHTMLHeader(clinicName: String): String {
        return """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Documento SST - ANDA</title>
                <style>
                    * {
                        margin: 0;
                        padding: 0;
                        box-sizing: border-box;
                    }
                    
                    body {
                        font-family: 'Arial', sans-serif;
                        line-height: 1.6;
                        color: #333;
                        padding: 20px;
                    }
                    
                    h1 { font-size: 24px; margin: 20px 0 10px 0; }
                    h2 { font-size: 20px; margin: 16px 0 8px 0; }
                    h3 { font-size: 16px; margin: 12px 0 6px 0; }
                    
                    p { margin-bottom: 10px; }
                    
                    table {
                        width: 100%;
                        border-collapse: collapse;
                        margin: 10px 0;
                    }
                    
                    td, th {
                        padding: 8px;
                        text-align: left;
                    }
                    
                    @media print {
                        body { padding: 0; }
                        .no-print { display: none; }
                        @page { margin: 1cm; }
                    }
                </style>
            </head>
            <body>
                <header style="text-align: center; margin-bottom: 30px; border-bottom: 2px solid #333; padding-bottom: 15px;">
                    <h2>${escapeHtml(clinicName)}</h2>
                    <p style="margin-top: 5px; color: #666;">Segurança e Medicina do Trabalho</p>
                </header>
        """.trimIndent()
    }

    private fun parseRecommendations(raw: String): List<String> {
        return raw
            .split(Regex("[;\\r\\n]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun renderMultilineText(value: String): String {
        return escapeHtml(value).replace("\n", "<br/>")
    }

    private fun escapeHtml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun formatDate(epochMs: Long): String = synchronized(dateFormat) {
        dateFormat.format(Date(epochMs))
    }

    private fun formatDateTime(epochMs: Long): String = synchronized(dateTimeFormat) {
        dateTimeFormat.format(Date(epochMs))
    }

    // ========== HELPER FUNCTIONS ==========

    private fun formatCNPJ(cnpj: String): String {
        val clean = cnpj.replace(Regex("[^0-9]"), "")
        return if (clean.length == 14) {
            "${clean.substring(0, 2)}.${clean.substring(2, 5)}.${clean.substring(5, 8)}/${clean.substring(8, 12)}-${clean.substring(12)}"
        } else {
            cnpj
        }
    }

    private fun formatCPF(cpf: String): String {
        val clean = cpf.replace(Regex("[^0-9]"), "")
        return if (clean.length == 11) {
            "${clean.substring(0, 3)}.${clean.substring(3, 6)}.${clean.substring(6, 9)}-${clean.substring(9)}"
        } else {
            cpf
        }
    }

    private fun translateRiskLevel(level: RiskLevel): String = when (level) {
        RiskLevel.MINIMAL -> "Mínimo"
        RiskLevel.LOW -> "Baixo"
        RiskLevel.MODERATE -> "Moderado"
        RiskLevel.HIGH -> "Alto"
        RiskLevel.CRITICAL -> "Crítico"
    }

    private fun translateExaminationType(type: EmployeeExaminationType): String = when (type) {
        EmployeeExaminationType.ADMISSION -> "Admissional"
        EmployeeExaminationType.PERIODIC -> "Periódico"
        EmployeeExaminationType.RETURN -> "Retorno"
        EmployeeExaminationType.REMOVAL -> "Afastamento"
        EmployeeExaminationType.EXIT -> "Demissional"
    }
}
