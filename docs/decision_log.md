# Decision Log (Projeto SST)

## Estado atual
- Plataforma inicial: Android nativo (Kotlin).
- Estrategia IA: on-device primeiro; cloud apenas para geracao avancada no futuro.
- Integracao WhatsApp: adiada (fora do MVP atual).
- Arquitetura da fase 1: MVVM leve + ViewBinding + WorkManager + repositorio em memoria.

## Implementado nesta etapa (Start pratico)
- Tela inicial funcional (`MainActivity`) com dois fluxos testaveis:
  - Inserir dados demo
  - Sincronizar pendencias
- Consulta automatizada de empresa por CNPJ via BrasilAPI com estado de loading/erro/resultado.
- Mapeamento automatico de CNAE para grau de risco no fluxo de consulta.
- Historico offline de consultas CNPJ salvo localmente (SharedPreferences).
- Repositorio simples (`SstRepository`) com contadores e estado reativo.
- Worker periodico (`SyncPendingRecordsWorker`) com agendamento no `AndaApplication`.
- Sync automatico reativado com guarda de falha (`runCatching`) no startup.
- Inicio do modulo Scanner C.A. com tela dedicada e extracao inicial por regex.
- Scanner C.A. evoluido para CameraX + ML Kit OCR com permissao em runtime.
- Pipeline de extracao de C.A. separado (`CaNumberExtractor`) e validacao local (`CaValidationService`).
- Validacao oficial-first implementada via `OfficialCaApiClient` com URL configuravel (`CA_API_BASE_URL`) e fallback local seguro.
- Decisao arquitetural: backend-first para C.A. com fallback explicito para consulta oficial no navegador (`CA_OFFICIAL_CONSULT_URL`).
- Backend C.A. reforcado com cache TTL, timeout/retry, rate limit e rastreio por `request_id`; scanner abre consulta oficial automaticamente em fallback.
- Backend evoluido com cache persistente em disco, endpoints admin protegidos por token (`/admin/metrics`, `/admin/cache/clear`, dashboard) e logs estruturados.
- CI automatizada adicionada em `.github/workflows/ci.yml` cobrindo testes backend e testes unitarios Android.
- Hardening adicional: suporte a `CA_ADMIN_TOKEN_SHA256` com comparacao segura e warmup automatico no boot (`CA_WARMUP_ON_BOOT`, `CA_WARMUP_CAS`).
- Experiencia do app: status de saude do backend agora aparece tambem na tela principal (`MainActivity`) e no scanner.
- Seguranca admin reforcada com allowlist de IP configuravel (`CA_ADMIN_IP_ALLOWLIST`) para rotas administrativas.
- Home agora monitora saude do backend em ciclo periodico via `MainViewModel`, reduzindo incerteza operacional em campo.
- Novo endpoint publico `GET /home/metrics` com taxa de fallback em janela temporal (`CA_FALLBACK_WINDOW_MS`) para alimentar UX operacional.
- Home passou a exibir badge de severidade de fallback (NORMAL/MODERADO/ALTO/CRITICO) com cor semaforica.
- Pacote anti-crash fase 1 aplicado: `CrashShield` global (uncaught handler), `safeLaunch` para coroutines de UI e blindagem de camera/sync worker.
- Home passou a sinalizar recuperacao apos falha fatal anterior, com log local para diagnostico.
- Decisao P0 tecnica: protecao local incremental em vez de reescrita total do banco; payloads/documentos, historico CNPJ e diagnosticos agora sao protegidos localmente, preservando campos de consulta em claro.
- Release endurecida com minify/shrink ativados e backup automatico amplo desativado.
- Decisao P0 operacional/juridica: o MVP atual registra assinatura operacional/local para rastreabilidade, mas nao declara automaticamente validade juridica formal; roteiro salvo em `docs/operations/aso-assinatura-p0.md`.
- Estrutura pronta para evoluir para Room/Hilt sem retrabalho de tela.

## Motivo da simplificacao
- Prioridade: app rodando rapido para validacao real com equipe/parceiros.
- Evitar setup pesado de build em ambiente local limitado.

## Proxima etapa recomendada
1. Ligar `CA_API_BASE_URL` ao backend intermediario definitivo e validar contrato JSON de C.A.
2. Persistencia offline com Room (etapa 2).
3. Reintroduzir DI (Hilt) apos estabilizar o fluxo funcional.
4. Geracao automatica de documentos SST (PGR/ASO inicial).

---

# 📋 DECISÕES FASE 1 - MVP CORE (23/03/2026)

## Arquitetura Domain SST Implementada

### Decision: SstDocumentDomain.kt com 10 Data Classes
- **Aprovado:** Sim
- **Razão:** Modelagem completa do negócio SST
- **Classes:**
  - OccupationalRisk (Risco ocupacional)
  - PersonalProtectiveEquipment (EPI)
  - EmployeeProfile (Dados funcionário)
  - OccupationalHealthCertificate (ASO)
  - OccupationalHealthControlProgram (PCMSO)
  - RiskManagementProgram (PGR)
  - DocumentTemplate (Reutilização)
  - GeneratedDocument (Histórico)
  - + 2 mais

### Decision: Gerador de Documentos (DocumentGenerationService)
- **Aprovado:** Sim
- **Output:** HTML formatado para ASO e PCMSO
- **Recursos:**
  - Formatação profissional com CSS print
  - Suporte a assinatura digital (roadmap)
  - CNPJ/CPF formatados
  - Tradução de enums para português

### Decision: Autofill Baseado em CNAE
- **Aprovado:** Sim
- **Implementação:** Rules-based (sem IA no MVP)
- **Benefício:** Funciona offline, determinístico
- **Roadmap:** ML em Fase 4

### Decision: Lint não bloqueia build
- **Aprovado:** Sim (abortOnError = false)
- **Razão:** Acelerar desenvolvimento, corrigir warnings depois
- **Target:** 0 errors, warnings cosmetic

### Decision: Stack Final Confirmado
- Android: Kotlin 11 + MVVM + ViewBinding
- Database: Room (local-first)
- Sync: WorkManager background
- PDF: iText commercial
- IA: OpenAI GPT-4 Turbo (Fase 2)

## Métricas Atingidas

- ✅ Build: SUCCESSFUL (102 tasks, 19s)
- ✅ Domain: 10 classes, 5 enums
- ✅ Gerador: ASO + PCMSO completo
- ✅ Documentação: 5 arquivos

## Próximas Decisões (Semana 2)

1. Autofill Service vs ViewModel (decidir localização)
2. PDF library final (iText vs PDFBox vs alternativa)
3. Room schema (normalization vs flat)
4. Backend: Firebase vs PostgreSQL definitivo
5. IA pricing: cache strategy vs batch processing

