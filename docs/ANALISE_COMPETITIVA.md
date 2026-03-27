# 📊 ANÁLISE COMPETITIVA — ANDA SST
_Atualizado: Março 2026_

---

## 🏆 Comparativo com Concorrentes

| Feature | ANDA | SOC | eSocial | GHO | Convenia |
|---------|------|-----|---------|-----|---------|
| **Offline first** | ✅ Total | ❌ Online | ❌ Online | ❌ Online | ❌ Online |
| **ASO (NR-7)** | ✅ | ✅ | ✅ | ✅ | ❌ |
| **PGR/GRO (NR-01)** | ✅ | ✅ | ✅ | ✅ | ❌ |
| **CAT** | ✅ | ✅ | ✅ | ❌ | ❌ |
| **APR** | ✅ | ❌ | ❌ | ❌ | ❌ |
| **OS (NR-01)** | ✅ | ❌ | ❌ | ❌ | ❌ |
| **PCMSO (NR-7)** | ✅ | ✅ | ❌ | ✅ | ❌ |
| **PPP (INSS)** | ✅ | ✅ | ❌ | ❌ | ❌ |
| **Assinatura digital válida** | ✅ Lei 14.063/2020 | ❌ Apenas registro | ❌ | ❌ | ❌ |
| **AndroidKeyStore RSA** | ✅ | ❌ | ❌ | ❌ | ❌ |
| **Scanner C.A. (câmera)** | ✅ | ❌ | ❌ | ❌ | ❌ |
| **CNPJ autofill (BrasilAPI)** | ✅ | ❌ | ❌ | ❌ | ✅ |
| **LGPD compliance** | ✅ Tela + consentimento | ⚠️ | ⚠️ | ❌ | ⚠️ |
| **PDF com bloco de assinatura** | ✅ | ❌ | ❌ | ❌ | ❌ |
| **Exportação ZIP** | ✅ | ❌ | ❌ | ❌ | ❌ |
| **Grau de risco automático** | ✅ (CNAE→grau) | ✅ | ✅ | ✅ | ❌ |
| **Modo campo (sem internet)** | ✅ | ❌ | ❌ | ❌ | ❌ |
| **Android nativo** | ✅ | Web only | Web only | Web only | Web/App |

---

## 🚀 Diferenciais Únicos do ANDA

### 1. Assinatura Eletrônica Avançada (Lei 14.063/2020)
- **O único app SST móvel** com assinatura RSA-SHA256 no AndroidKeyStore
- Chave privada protegida por biometria, **não exportável**
- Gera bloco de assinatura criptográfico no PDF
- Fingerprint da chave pública para auditoria
- **Concorrentes não têm isso**

### 2. Offline-First Total
- Room + AES-256 localmente
- Fila de sync com retry automático
- Funciona em campo sem sinal (canteiros de obra, indústrias remotas)

### 3. CA Scanner Integrado
- Reconhece CA por câmera (ML Kit OCR)
- Único app SST com scanner de C.A. de EPI nativo

### 4. Cobertura Completa de Documentos
- ASO, PGR, PCMSO, CAT, APR, OS, PPP
- **7 documentos SST** em um app
- Todos com geração offline + assinatura digital

### 5. LGPD Ready
- Tela de consentimento no primeiro acesso
- Texto completo das bases legais (Art. 7º e 11)
- Direitos ARCO implementados na política

---

## 📈 Roadmap para Dominar o Mercado

### Fase 2 (próximas semanas)
- [ ] **GRO Digital** — Gerenciamento de Riscos Ocupacionais integrado com PGR
- [ ] **Compartilhamento WhatsApp/Email** direto do PDF
- [ ] **QR Code de verificação** no documento (link para hash público)
- [ ] **Múltiplos assinantes** em um documento (trabalhador + responsável)
- [ ] **Vencimento de ASO** — alertas push 30/15/7 dias antes
- [ ] **Dashboard de riscos** por empresa/CNAE

### Fase 3 (lançamento público)
- [ ] **eSocial S-2220 export** (importação direta no eSocial)
- [ ] **Múltiplas empresas** por usuário (carteira de clientes)
- [ ] **Relatório gerencial** com gráficos de cobertura SST
- [ ] **Biometria facial** como opção de assinatura adicional
- [ ] **Notificação push** para vencimento de documentos

### Fase 4 (premium)
- [ ] **API pública** para integração com ERP
- [ ] **ICP-Brasil** para documentos que exigem nível Qualificado
- [ ] **Assinatura em tablet** com caneta (pad de assinatura manuscrita)

---

## ✅ Estado Atual do App (Março 2026)

### Completo e Funcional
- ✅ LGPD: tela de consentimento no primeiro acesso (Lei 13.709/2018)
- ✅ Assinatura Eletrônica Avançada em TODOS os documentos (Lei 14.063/2020)
- ✅ AndroidKeyStore RSA-2048 + biometria
- ✅ PDF com bloco de assinatura criptográfico
- ✅ 7 tipos de documentos SST (ASO, PGR, PCMSO, CAT, APR, OS, PPP)
- ✅ Offline-first com Room + AES-256
- ✅ Scanner de C.A. por câmera
- ✅ CNPJ autofill via BrasilAPI
- ✅ Detecção automática de riscos por CNAE
- ✅ Exportação ZIP de todos os PDFs
- ✅ Sync com backend (com retry e fallback)
- ✅ Crash protection com modo seguro
- ✅ Auditoria de operações por documento

### Base Legal da Assinatura Digital
- **Lei 14.063/2020, Art. 4º, III** — Assinatura Eletrônica Avançada
- **LGPD Art. 46** — Medidas de segurança adequadas (AndroidKeyStore)
- **NR-7** — ASO e PCMSO podem ter assinatura eletrônica (Portaria MTE 1.419/2018)
- **NR-01** — OS e APR requerem ciência do trabalhador (assinatura eletrônica válida)
- **Lei 8.213/91, Art. 22** — CAT: documento eletrônico aceito pelo INSS

---

_ANDA — Segurança e Medicina do Trabalho. Feito no Brasil, para o Brasil._

