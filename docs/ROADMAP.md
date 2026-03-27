# ROADMAP DETALHADO IMPLEMENTAÇÃO ANDA MVP

## SEMANA 1-2: Estrutura Core

### ✅ Concluído
- [x] Estratégia de mercado (`STRATEGY.md`)
- [x] Domínio SST (`SstDocumentDomain.kt`)
- [x] Serviço de geração de documentos (`DocumentGenerationService.kt`)
  - [x] ASO (Atestado de Saúde Ocupacional)
  - [x] PCMSO (Programa de Controle Médico)

### 🔧 Em Progresso
- [ ] Serviço de autofill inteligente (`AutofillService.kt`)
- [ ] Repositório de documentos
- [ ] Database Room para persistência

### 📋 Próximo
- [ ] Exportador PDF (iText)
- [ ] UI para criar ASO
- [ ] UI para criar PCMSO
- [ ] Testes unitários

---

## FUNCIONALIDADES MVP (FASE 1)

### 1. ✅ Consulta CNPJ + Risco (JÁ EXISTE)
- Integração com BrasilAPI
- Mapeamento automático CNAE -> risco
- Histórico local offline

### 2. ✅ Scanner de C.A. (JÁ EXISTE)
- OCR com ML Kit
- Validação de CA
- Fallback local

### 3. 🔧 Gerador ASO (PRIORITÁRIO)
- Template inteligente
- Autofill do CNPJ
- Exportação PDF
- Assinatura médico

### 4. 🔧 Gerador PCMSO (PRIORITÁRIO)
- Template baseado em CNAE
- Cronograma automático
- Exames complementares sugeridos
- Exportação PDF

### 5. 🔧 Sistema de Autofill
- Reutiliza dados de CNPJ em ASO/PCMSO
- Sugere riscos baseado em CNAE
- Sincroniza alterações entre docs

### 6. 🔧 Galeria de Templates
- 50+ templates prontos para diferentes riscos
- Compartilhamento na comunidade
- Versionamento

### 7. 🔧 Checklist de Conformidade
- NR-10, NR-12, NR-20 por risco
- Validação automática
- Relatório de gaps

### 8. 🔧 Exportação PDF
- Documentos prontos para imprimir
- Assinatura digital preparada
- Metadados de rastreabilidade

---

## ARQUITETURA DADOS

```
Database (Room)
├── Companies
├── Employees
├── Documents (ASO, PCMSO, PGR, etc)
├── Templates
├── DocumentHistory
└── SyncQueue (pendentes)

Cache (offline)
├── Last CNPJs consulted
├── Generated documents
└── Templates

Backend Sync
├── POST /documents/{type} - salvar documento
├── GET /templates - baixar templates
└── POST /sync - sincronizar pendências
```

---

## PRIORIDADE DE IMPLEMENTAÇÃO

### SEMANA 1-2 (MVP Core)
1. ✅ Domínio + Database schema
2. 🔧 Autofill Service
3. 🔧 PDF Export (iText)
4. 🔧 UI ASO (Activity)
5. 🔧 Testes unitários

### SEMANA 3-4
6. 🔧 UI PCMSO (Activity)
7. 🔧 Templates (RecyclerView)
8. 🔧 Sincronização automática
9. 🔧 Teste em device real

### SEMANA 5-6
10. 🔧 Checklist de conformidade
11. 🔧 Relatórios (Bitmap -> PDF)
12. 🔧 Publicação Play Store
13. 🔧 Marketing inicial

---

## ESTRUTURA ARQUIVOS

```
app/src/main/java/com/example/anda/
├── domain/
│   └── SstDocumentDomain.kt (✅ Criado)
├── data/
│   ├── db/
│   │   ├── AppDatabase.kt (🔧 TODO)
│   │   ├── entities/
│   │   │   ├── CompanyEntity.kt
│   │   │   ├── DocumentEntity.kt
│   │   │   └── TemplateEntity.kt
│   │   └── dao/
│   │       ├── CompanyDao.kt
│   │       ├── DocumentDao.kt
│   │       └── TemplateDao.kt
│   ├── services/
│   │   ├── DocumentGenerationService.kt (✅ Criado)
│   │   ├── AutofillService.kt (🔧 TODO)
│   │   └── PdfExportService.kt (🔧 TODO)
│   └── repository/
│       └── DocumentRepository.kt (🔧 TODO)
├── feature/
│   ├── aso/
│   │   ├── AsoActivity.kt (🔧 TODO)
│   │   ├── AsoViewModel.kt (🔧 TODO)
│   │   └── layout/activity_aso.xml (🔧 TODO)
│   ├── pcmso/
│   │   ├── PcmsoActivity.kt (🔧 TODO)
│   │   ├── PcmsoViewModel.kt (🔧 TODO)
│   │   └── layout/activity_pcmso.xml (🔧 TODO)
│   ├── templates/
│   │   ├── TemplatesActivity.kt (🔧 TODO)
│   │   ├── TemplatesViewModel.kt (🔧 TODO)
│   │   └── layout/activity_templates.xml (🔧 TODO)
│   └── scanner/
│       └── CaScannerActivity.kt (✅ Existe)
└── MainActivity.kt (✅ Existe)
```

---

## MÉTRICAS DE SUCESSO

### Sprint 1 (Semana 1-2)
- [ ] Código compila sem erros
- [ ] DocumentGenerationService testa ASO/PCMSO
- [ ] PDF exporta corretamente
- [ ] App não fica mais "abre e fecha"

### Sprint 2 (Semana 3-4)
- [ ] UI ASO/PCMSO funcional
- [ ] Autofill preenchedata automáticamente
- [ ] 100+ documentos gerados localmente
- [ ] Sincronização funciona offline

### Sprint 3 (Semana 5-6)
- [ ] Primeiro build APK testável
- [ ] NPS > 7/10 em feedback
- [ ] 500+ downloads beta
- [ ] 0 crashes críticos

---

## BLOQUEADORES CONHECIDOS

1. **App "abre e fecha"** - Precisamos debugar no Android Studio
2. **Firebase vs PostgreSQL** - Decidir qual usar para backend
3. **IA OpenAI** - Custo de API (mitigar com cache)
4. **Assinatura digital** - Integração com certificado gov.br

---

## PRÓXIMOS PASSOS IMEDIATOS

1. ✅ **FEITO:** Criar estratégia
2. ✅ **FEITO:** Criar domínio SST
3. ✅ **FEITO:** Criar gerador de documentos
4. 🔧 **PRÓXIMO:** Criar autofill service
5. 🔧 **PRÓXIMO:** Criar PDF export
6. 🔧 **PRÓXIMO:** Debugar crash do app
7. 🔧 **PRÓXIMO:** Criar UI ASO

