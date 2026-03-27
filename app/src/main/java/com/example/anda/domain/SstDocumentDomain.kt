package com.example.anda.domain

/**
 * Domínio de documentos SST
 * Representa os tipos de documentos que o ANDA deve gerar
 */

// ========== ENUMS ==========

enum class DocumentType {
    ASO,           // Atestado de Saúde Ocupacional
    PCMSO,         // Programa de Controle Médico de Saúde Ocupacional
    PGR,           // Programa de Gerenciamento de Riscos
    LTCAT,         // Laudo Técnico de Condições Ambientais do Trabalho
    PPP,           // Perfil Profissiográfico Previdenciário
    APR,           // Análise Preliminar de Risco
    CAT,           // Comunicação de Acidente do Trabalho
    AET,           // Análise Ergonômica do Trabalho
    INVENTARIO,    // Inventário de Riscos
    LAUDO_NR10,    // Laudo NR-10 (Eletricidade)
    LAUDO_NR12,    // Laudo NR-12 (Máquinas)
    LAUDO_NR20,    // Laudo NR-20 (Inflamáveis/Combustíveis)
    PCA,           // Programa de Conservacao Auditiva
    PPR,           // Programa de Protecao Respiratoria
    PT,            // Permissao de Trabalho
    PLANO_ACAO,    // Plano de Acao SST
    RESGATE,       // Plano de Resgate
    INSALUBRIDADE, // Laudo de Insalubridade
    PERICULOSIDADE,// Laudo de Periculosidade
    PGRTR,         // PGR para trabalho rural/tematico
    CERTIFICADO_EPI, // Certificado/controle de EPI
    MAPA_RISCO     // Mapa de Risco
}

enum class RiskLevel {
    MINIMAL,       // Risco mínimo
    LOW,           // Risco baixo
    MODERATE,      // Risco moderado
    HIGH,          // Risco alto
    CRITICAL       // Risco crítico
}

enum class EmployeeExaminationType {
    ADMISSION,     // Exame admissional
    PERIODIC,      // Exame periódico
    RETURN,        // Exame de retorno
    REMOVAL,       // Exame de afastamento
    EXIT           // Exame demissional
}

// ========== DATA CLASSES ==========

/**
 * Risco ocupacional identificado em uma atividade
 */
data class OccupationalRisk(
    val id: String = "",
    val name: String,                      // Ex: "Ruído ocupacional"
    val description: String,               // Descrição detalhada
    val level: RiskLevel,                  // Nível do risco
    val normativeReference: String,        // Ex: "NR-15 Anexo 1"
    val measurementValue: String? = null,  // Ex: "85 dB"
    val controlMeasures: List<String>,     // Ex: ["EPI: Protetor auricular", "EPCs: Isolamento acústico"]
    val epiRequired: List<String> = emptyList(), // EPIs necessários
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Equipamento de Proteção Individual (EPI)
 */
data class PersonalProtectiveEquipment(
    val id: String = "",
    val name: String,                      // Ex: "Protetor auricular tipo plug"
    val caNumber: String,                  // Número do Certificado de Aprovação
    val category: String,                  // Ex: "Proteção auditiva"
    val validUntil: Long,                  // Timestamp de validade
    val manufacturer: String,              // Fabricante
    val model: String,                     // Modelo
    val serialNumber: String? = null,      // Número de série
    val inspectionDate: Long? = null,      // Data de última inspeção
    val isValid: Boolean = true,           // Se está dentro da validade
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Informações do funcionário para ASO
 */
data class EmployeeProfile(
    val id: String = "",
    val name: String,
    val cpf: String,
    val rg: String,
    val birthDate: Long,                   // Timestamp
    val gender: String,                    // M/F
    val maritalStatus: String? = null,
    val phoneNumber: String? = null,
    val email: String? = null,
    val address: String? = null,
    val riskExposures: List<OccupationalRisk> = emptyList(),
    val medicalHistory: String? = null,    // Histórico médico relevante
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Atestado de Saúde Ocupacional (ASO)
 */
data class OccupationalHealthCertificate(
    val id: String = "",
    val documentNumber: String,            // Número sequencial único
    val companyProfile: CompanyProfile,    // Dados da empresa
    val employee: EmployeeProfile,         // Dados do funcionário
    val examinationType: EmployeeExaminationType,
    val examinationDate: Long,             // Timestamp
    val occupationalDoctor: String,        // Nome do médico
    val occupationalDoctorCRM: String,     // CRM do médico
    val clinicName: String,                // Nome da clínica
    val clinicCNPJ: String,                // CNPJ da clínica
    val examinationResults: String,        // Resultado (APT/INAPTO)
    val restrictions: String? = null,      // Restrições, se houver
    val complementaryExams: List<String> = emptyList(), // Exames complementares solicitados
    val observations: String? = null,      // Observações adicionais
    val validUntil: Long,                  // Data de validade do ASO (timestamp)
    val generatedBy: String,               // Quem gerou (técnico/médico)
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Programa de Controle Médico de Saúde Ocupacional (PCMSO)
 */
data class OccupationalHealthControlProgram(
    val id: String = "",
    val companyProfile: CompanyProfile,
    val year: Int,                         // Ano de referência
    val occupationalDoctor: String,        // Médico responsável
    val occupationalDoctorCRM: String,
    val clinicName: String,
    val clinicCNPJ: String,
    val riskAssessment: String,            // Avaliação de riscos
    val objectives: List<String>,          // Objetivos do programa
    val examinationSchedule: Map<EmployeeExaminationType, String>,  // Cronograma
    val complementaryExams: List<String> = emptyList(), // Exames complementares
    val controlMeasures: List<String> = emptyList(),    // Medidas de controle
    val responsibilities: String? = null,  // Responsabilidades
    val observations: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Programa de Gerenciamento de Riscos (PGR)
 */
data class RiskManagementProgram(
    val id: String = "",
    val companyProfile: CompanyProfile,
    val year: Int,
    val technician: String,
    val technicanCREA: String? = null,    // CREA do engenheiro
    val identifiedRisks: List<OccupationalRisk> = emptyList(),
    val controlMeasures: Map<String, List<String>> = emptyMap(), // Risco -> Medidas
    val timeline: String? = null,          // Cronograma de implementação
    val budget: Double? = null,            // Orçamento estimado
    val responsibilities: String? = null,
    val observations: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Template reutilizável de documento
 */
data class DocumentTemplate(
    val id: String = "",
    val name: String,                      // Ex: "ASO Padrão"
    val documentType: DocumentType,
    val cnaeList: List<String> = emptyList(), // CNAEs aplicáveis (vazio = todas)
    val riskLevels: List<RiskLevel> = emptyList(), // Níveis de risco (vazio = todos)
    val content: String,                   // Conteúdo do template (markdown/HTML)
    val variables: List<String> = emptyList(), // {{variable}} placeholders
    val isPublic: Boolean = false,         // Se pode ser compartilhado
    val authorId: String? = null,          // ID do autor
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Documento gerado (instância de um template preenchido)
 */
data class GeneratedDocument(
    val id: String = "",
    val documentType: DocumentType,
    val templateId: String,
    val companyProfile: CompanyProfile,
    val technician: String,
    val generationDate: Long = System.currentTimeMillis(),
    val content: String,                   // Conteúdo final gerado
    val pdfPath: String? = null,           // Caminho do PDF gerado localmente
    val isSynced: Boolean = false,         // Se foi sincronizado com backend
    val syncDate: Long? = null,
    val versionNumber: Int = 1,            // Número da versão
    val isDigitallySigned: Boolean = false, // Se foi assinado digitalmente
    val signatureDate: Long? = null,
    val observations: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

// Importar CompanyProfile do network
data class CompanyProfile(
    val cnpj: String = "",
    val legalName: String = "",
    val tradeName: String = "",
    val cnae: String = "",
    val city: String = "",
    val state: String = "",
    val postalCode: String = ""
)

