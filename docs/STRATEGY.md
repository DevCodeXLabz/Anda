# 🚀 ESTRATÉGIA ANDA - MVP PARA DOMINAÇÃO DE MERCADO SST

**Versão:** 1.0  
**Data:** 2026-03-23  
**Status:** IMPLEMENTAÇÃO INICIADA

---

## 📋 EXECUTIVE SUMMARY

O **ANDA** é o "canivete suíço" dos técnicos de Segurança e Saúde do Trabalho (SST). Diferente dos concorrentes que oferecem "consultoras" caras e lentas, o ANDA entrega:

- ✅ **Mobile-first** - Trabalha em campo, offline
- ✅ **Autofill inteligente** - Reutiliza dados entre documentos
- ✅ **Geração automática** - Cria ASO, PCMSO, PGR em minutos
- ✅ **Integração de dados** - BrasilAPI + eSocial
- ✅ **Viciante** - UX que faz o usuário querer usar todo dia

---

## 🎯 FASES DE IMPLEMENTAÇÃO

### **FASE 1: MVP Core (Técnicos Autônomos)** - 8-10 semanas
**Objetivo:** Conquistar 1.000 técnicos freelas  
**Público:** Técnicos de SST independentes

#### Funcionalidades:
1. ✅ Consulta CNPJ + Risco automático (JÁ EXISTE)
2. ✅ Scanner de C.A. (JÁ EXISTE)
3. 🔧 **Gerador de ASO (Atestado de Saúde Ocupacional)** - PRIORITÁRIO
4. 🔧 **Gerador de PCMSO (Programa de Controle Médico)** - PRIORITÁRIO
5. 🔧 **Autofill inteligente** - Reutiliza CNPJ em todos docs
6. 🔧 **Galeria de templates** - 50+ cenários de risco
7. 🔧 **Checklist de conformidade** - NR10, NR20, NR12 por risco
8. 🔧 **Exportação PDF** - Documentos prontos

#### Métricas de Sucesso:
- 500+ downloads/semana
- 70%+ retenção após 7 dias
- NPS > 40
- 100+ documentos gerados/dia

---

### **FASE 2: Empresas Prestadoras** - 12-14 semanas
**Objetivo:** Integrar clínicas e consultoras  
**Público:** Clínicas de medicina do trabalho, consultoras de SST

#### Funcionalidades:
9. Gestão multi-empresa
10. Sistema de usuários (Técnico + Gerente + Admin)
11. Relatórios de faturamento
12. Integração com NF-e
13. Versionamento de documentos
14. Assinatura digital

---

### **FASE 3: Compliance Executivo** - 8-10 semanas
**Objetivo:** Empresas contratantes gerenciarem conformidade  
**Público:** RH, Segurança, Compliance

#### Funcionalidades:
15. Dashboard de compliance executivo
16. Alertas de vencimento
17. Integração com RH/eSocial
18. Auditoria interna
19. Relatórios para órgãos reguladores

---

### **FASE 4: IA & Inovação** - Ongoing
**Objetivo:** Diferencial competitivo inigualável

#### Funcionalidades:
20. IA para análise de risco (ML)
21. Autopreenchimento inteligente (NLP)
22. Validação automática de inconsistências
23. Webhook para eSocial
24. Análise de riscos psicossociais (NR-1 2025)
25. Modo neurodivergente/acessibilidade

---

## 💰 MODELO DE MONETIZAÇÃO

| Tier | Preço | Público | Limite |
|------|-------|---------|--------|
| **Free** | R$0 | Teste | 5 docs/mês |
| **Pro** | R$99/mês | Técnicos | Unlimited |
| **Business** | R$499/mês | Clínicas | Multi-empresa |
| **Enterprise** | Custom | Grandes empresas | API + SSO |

**Projeção 12 meses:**
- 1.000 Pro @ R$99 = R$100k/mês
- 50 Business @ R$499 = R$25k/mês
- **Total: R$1,5M/ano** (conservador)

---

## 🏗️ ARQUITETURA TÉCNICA

```
┌─────────────────────────────────┐
│    ANDROID (Kotlin)             │
│  - Jetpack Compose (UI)         │
│  - Room (BD local)              │
│  - WorkManager (Sync)           │
│  - iText (PDF generation)       │
│  - ML Kit (OCR)                 │
└────────────┬────────────────────┘
             │
┌────────────▼────────────────────┐
│  BACKEND (Node.js)              │
│  - Express                      │
│  - PostgreSQL                   │
│  - Redis (cache)                │
│  - Bull (jobs)                  │
│  - JWT Auth                     │
└────────────┬────────────────────┘
             │
┌────────────▼────────────────────┐
│  INTEGRAÇÕES                    │
│  - BrasilAPI (CNPJ)             │
│  - gov.br (Certificado)         │
│  - eSocial (Governo)            │
│  - NF-e (Nota Fiscal)           │
│  - OpenAI (IA)                  │
└─────────────────────────────────┘
```

---

## 📊 CONCORRENTES & DIFERENCIAL

| Concorrente | Força | ANDA Vence |
|-------------|-------|-----------|
| SafetyWare | UI familiar | Mobile-first + Preço (R$99 vs R$3.000) |
| Prova SST | OCR bom | Offline robusto + Templates prontos |
| SSTCheck | Dashboard | Mobile + Autofill inteligente |
| Integra SST | eSocial | Foco em PME (não só grandes) |
| EGS Consultoria | Conhecimento | Tecnologia moderna + IA |

**Diferencial ANDA:**
- 🔥 **100x mais rápido** que concorrentes (minutos vs. horas)
- 🔥 **Funciona offline** em qualquer lugar
- 🔥 **Automação agressiva** (IA + templates)
- 🔥 **Preço disruptivo** (R$99 vs. R$3.000+)
- 🔥 **Viciante** (gamificação + notificações)

---

## 🎬 PRÓXIMOS PASSOS (SEMANA 1)

- [ ] **Módulo ASO** - Gerador com template inteligente
- [ ] **Módulo PCMSO** - Template baseado em CNAE
- [ ] **Sistema de Autofill** - Reutiliza CNPJ em todos docs
- [ ] **Exportador PDF** - iText integration
- [ ] **Testes unitários** - Validação de lógica
- [ ] **Build APK** - Teste em device real

---

## 📝 DECISÕES TOMADAS

- ✅ **Público prioritário:** Técnicos autônomos (MVP)
- ✅ **Stack:** Kotlin Android + Node.js Backend
- ✅ **IA:** OpenAI (GPT-4 Turbo) para documentos + TensorFlow Lite para OCR local
- ✅ **Offline:** Room + WorkManager (sincronização automática)
- ✅ **Design:** Material Design 3 + Cores de risco (verde/amarelo/vermelho)
- ✅ **Monetização:** Freemium (Free/Pro/Business/Enterprise)

---

## 🚨 RISCOS & MITIGAÇÃO

| Risco | Impacto | Mitigação |
|-------|---------|-----------|
| Crash app | ALTO | Error handler robusto + Sentry |
| Offline falha | ALTO | Room BD + WorkManager sync |
| IA cara (OpenAI) | MÉDIO | Cache agressivo + Fallback templates |
| Concorrência | MÉDIO | Inovação 3x mais rápida |
| Regulamentação | BAIXO | Compliance desde dia 1 |

---

## 📞 CONTATO & SUPORTE

- **Product Manager:** Você
- **Tech Lead:** IA (GitHub Copilot)
- **Sprint:** 2 semanas
- **Next Review:** 2026-04-06

