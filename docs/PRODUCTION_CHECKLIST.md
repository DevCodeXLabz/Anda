# ✅ Checklist de Produção / Go-Live - ANDA

Objetivo: transformar o `Anda` em um app pronto para uso real com clínicas, técnicos e empresas de SST, com prioridade correta e sem improviso.

## Como ler este documento
- **Tipo**
  - `codigo`: depende principalmente de implementação técnica
  - `decisao externa`: depende de definição sua, jurídico, responsável técnico ou operação
- **Prioridade**
  - `P0`: bloqueia uso real / lançamento
  - `P1`: muito importante para operação segura e profissional
  - `P2`: evolução de qualidade, escala ou diferenciação

---

# 1) P0 - BLOQUEADORES DE GO-LIVE

## 1.1 Engenharia / Código

### 1. Criptografar dados sensíveis locais
- **Objetivo:** proteger CPF, CNPJ, dados ocupacionais, documentos e trilhas de auditoria no aparelho.
- **Tipo:** `codigo`
- **Prioridade:** `P0`
- **Responsável sugerido:** engenharia
- **Critério de pronto:** banco local e arquivos críticos protegidos com estratégia de criptografia definida e testada.

### 2. Definir estratégia real de assinatura válida
- **Objetivo:** separar assinatura operacional interna da assinatura com valor jurídico.
- **Tipo:** `codigo`
- **Prioridade:** `P0`
- **Responsável sugerido:** engenharia + responsável do negócio
- **Critério de pronto:** o app deixa claro no fluxo e no documento qual assinatura é apenas local e qual tem validade jurídica formal.

**Status atual do MVP:** assinatura operacional/local já diferenciada no app e nos exportáveis. Falta definir a estratégia jurídica final da operação.

### 3. Revisar geração dos documentos SST com especialista
- **Objetivo:** garantir que ASO e demais documentos não saiam com estrutura ou texto inadequados.
- **Tipo:** `codigo`
- **Prioridade:** `P0`
- **Responsável sugerido:** engenharia + médico do trabalho / técnico SST / consultor NR
- **Critério de pronto:** templates revisados e aprovados por especialista responsável.

### 4. Criar proteção de backup/restore e perda de dados
- **Objetivo:** impedir perda de documentos e garantir recuperação mínima.
- **Tipo:** `codigo`
- **Prioridade:** `P0`
- **Responsável sugerido:** engenharia
- **Critério de pronto:** existe rotina clara de backup/exportação e recuperação validada em teste real.

### 5. Endurecer build de release
- **Objetivo:** sair do modo desenvolvimento e preparar versão publicável.
- **Tipo:** `codigo`
- **Prioridade:** `P0`
- **Responsável sugerido:** engenharia
- **Critério de pronto:** build release configurada, revisão de `allowBackup`, `lint`, shrink/proguard e assinatura de release.

## 1.2 Produto / Operação

### 6. Definir MVP comercial exato do lançamento
- **Objetivo:** evitar lançar “app que faz tudo mal”.
- **Tipo:** `decisao externa`
- **Prioridade:** `P0`
- **Responsável sugerido:** dono do produto
- **Critério de pronto:** lista fechada do que entra no primeiro lançamento real (ex.: ASO + scanner CA + documentos locais + diagnóstico).

### 7. Escolher público inicial real
- **Objetivo:** orientar UX, preço, fluxo e linguagem.
- **Tipo:** `decisao externa`
- **Prioridade:** `P0`
- **Responsável sugerido:** negócio/produto
- **Critério de pronto:** decisão explícita do foco inicial: técnico autônomo, clínica SST ou empresa prestadora.

### 8. Criar fluxo operacional de suporte
- **Objetivo:** saber o que fazer quando algo falhar em campo.
- **Tipo:** `decisao externa`
- **Prioridade:** `P0`
- **Responsável sugerido:** produto/operação
- **Critério de pronto:** roteiro simples de suporte com diagnóstico, exportação de relatório e ação de recuperação.

## 1.3 Jurídico / Compliance

### 9. Política de privacidade e termos de uso
- **Objetivo:** atender uso real com dados pessoais e ocupacionais.
- **Tipo:** `decisao externa`
- **Prioridade:** `P0`
- **Responsável sugerido:** jurídico + dono do produto
- **Critério de pronto:** política e termos escritos, revisados e disponíveis para uso real.

### 10. Definir base legal LGPD e retenção de dados
- **Objetivo:** saber por que o dado é coletado, por quanto tempo fica e como é excluído.
- **Tipo:** `decisao externa`
- **Prioridade:** `P0`
- **Responsável sugerido:** jurídico/compliance
- **Critério de pronto:** matriz simples com tipo de dado, finalidade, base legal, retenção e descarte.

### 11. Definir responsabilidade técnica documental
- **Objetivo:** não vender automação como se substituísse médico/engenheiro/responsável técnico.
- **Tipo:** `decisao externa`
- **Prioridade:** `P0`
- **Responsável sugerido:** negócio + jurídico + especialista SST
- **Critério de pronto:** texto e operação deixam claro o papel do app e do responsável técnico humano.

---

# 2) P1 - PROFISSIONALIZAÇÃO DO MVP

## 2.1 Engenharia / Código

### 12. Expandir suíte de testes críticos
- **Objetivo:** reduzir regressão em startup, scanner, sync e geração de documentos.
- **Tipo:** `codigo`
- **Prioridade:** `P1`
- **Responsável sugerido:** engenharia
- **Critério de pronto:** testes cobrindo cenários críticos reais e pipeline rodando sem falha.

### 13. Melhorar persistência e estrutura de dados
- **Objetivo:** sustentar escala de documentos, empresas e funcionários sem retrabalho.
- **Tipo:** `codigo`
- **Prioridade:** `P1`
- **Responsável sugerido:** engenharia
- **Critério de pronto:** schema local consolidado para empresa, funcionário, documento, histórico e fila de sync.

### 14. Implementar criptografia/segurança de arquivos exportados
- **Objetivo:** reduzir exposição de PDFs, TXT e pacotes ZIP.
- **Tipo:** `codigo`
- **Prioridade:** `P1`
- **Responsável sugerido:** engenharia
- **Critério de pronto:** arquivos críticos seguem política clara de proteção e compartilhamento.

### 15. Melhorar telemetria técnica de produção
- **Objetivo:** enxergar falhas reais pós-lançamento.
- **Tipo:** `codigo`
- **Prioridade:** `P1`
- **Responsável sugerido:** engenharia
- **Critério de pronto:** diagnóstico local + estratégia de observabilidade definida para produção.

### 16. Criar testes em aparelho real
- **Objetivo:** validar câmera, OCR, biometria, exportação e comportamento offline.
- **Tipo:** `codigo`
- **Prioridade:** `P1`
- **Responsável sugerido:** engenharia/testes
- **Critério de pronto:** checklist executado em pelo menos 2 aparelhos Android reais.

## 2.2 Produto / Operação

### 17. Melhorar onboarding para usuário leigo/amador
- **Objetivo:** reduzir erro operacional no primeiro uso.
- **Tipo:** `codigo`
- **Prioridade:** `P1`
- **Responsável sugerido:** produto + engenharia
- **Critério de pronto:** fluxo inicial guiado com mensagens claras e validação amigável.

### 18. Definir padrão dos documentos prioritários após ASO
- **Objetivo:** escolher o próximo bloco de maior valor comercial.
- **Tipo:** `decisao externa`
- **Prioridade:** `P1`
- **Responsável sugerido:** produto/negócio
- **Critério de pronto:** ordem fechada dos próximos documentos (ex.: APR, CAT, PGR, PT).

### 19. Criar processo piloto com parceiros reais
- **Objetivo:** testar o `Anda` em ambiente de trabalho real.
- **Tipo:** `decisao externa`
- **Prioridade:** `P1`
- **Responsável sugerido:** negócio/operação
- **Critério de pronto:** 1 a 3 parceiros usando o app com feedback estruturado.

### 20. Definir estratégia de suporte e atualização
- **Objetivo:** saber como corrigir, treinar e evoluir sem caos.
- **Tipo:** `decisao externa`
- **Prioridade:** `P1`
- **Responsável sugerido:** operação/produto
- **Critério de pronto:** fluxo de atualização, coleta de feedback e priorização semanal definidos.

## 2.3 Jurídico / Compliance

### 21. Revisar textos e disclaimers do app
- **Objetivo:** evitar promessas indevidas ou interpretações jurídicas erradas.
- **Tipo:** `decisao externa`
- **Prioridade:** `P1`
- **Responsável sugerido:** jurídico + produto
- **Critério de pronto:** textos de telas e relatórios revisados para uso profissional.

### 22. Definir política de compartilhamento e retenção de relatórios
- **Objetivo:** controlar exportação de diagnóstico, PDF e dados sensíveis.
- **Tipo:** `decisao externa`
- **Prioridade:** `P1`
- **Responsável sugerido:** jurídico/compliance
- **Critério de pronto:** regra simples de quem pode exportar, compartilhar e por quanto tempo manter arquivos.

---

# 3) P2 - ESCALA E DIFERENCIAÇÃO

## 3.1 Engenharia / Código

### 23. Evoluir arquitetura para módulos maiores sem retrabalho
- **Objetivo:** preparar crescimento de documentos e fluxos.
- **Tipo:** `codigo`
- **Prioridade:** `P2`
- **Responsável sugerido:** engenharia
- **Critério de pronto:** estrutura pronta para crescer sem bagunça excessiva.

### 24. Melhorar busca, filtros e produtividade operacional
- **Objetivo:** acelerar o uso em clínicas e prestadoras com volume maior.
- **Tipo:** `codigo`
- **Prioridade:** `P2`
- **Responsável sugerido:** engenharia/produto
- **Critério de pronto:** listas e documentos com busca e filtros realmente úteis em campo.

### 25. Criar painéis operacionais mais fortes no app
- **Objetivo:** mostrar vencimentos, filas, risco e pendências em uma visão executiva.
- **Tipo:** `codigo`
- **Prioridade:** `P2`
- **Responsável sugerido:** produto + engenharia
- **Critério de pronto:** Home operacional com indicadores claros de produtividade e risco.

### 26. Melhorar acessibilidade e inclusão
- **Objetivo:** atender usuários neurodivergentes e pessoas com deficiência sem prejudicar eficiência.
- **Tipo:** `codigo`
- **Prioridade:** `P2`
- **Responsável sugerido:** produto/design/engenharia
- **Critério de pronto:** contraste, legibilidade, navegação e textos revisados com checklist de acessibilidade.

## 3.2 Produto / Operação

### 27. Definir modelo comercial e precificação
- **Objetivo:** transformar o app em negócio sustentável.
- **Tipo:** `decisao externa`
- **Prioridade:** `P2`
- **Responsável sugerido:** negócio/produto
- **Critério de pronto:** plano inicial de monetização documentado e testável.

### 28. Criar roteiro de implantação em clínica/prestadora
- **Objetivo:** facilitar venda e adoção.
- **Tipo:** `decisao externa`
- **Prioridade:** `P2`
- **Responsável sugerido:** operação/comercial
- **Critério de pronto:** existe material básico de implantação, treinamento e primeiros passos.

### 29. Definir métricas de sucesso do produto
- **Objetivo:** evoluir por dados e não por achismo.
- **Tipo:** `decisao externa`
- **Prioridade:** `P2`
- **Responsável sugerido:** produto/negócio
- **Critério de pronto:** KPIs simples definidos (retenção, documentos gerados, tempo por documento, falhas, exportações).

---

# 4) Ordem recomendada de execução

## Fase A - Antes de qualquer lançamento externo
1. Criptografia local
2. Estratégia de assinatura válida
3. Revisão técnica dos documentos SST
4. Política de privacidade + termos
5. Base legal LGPD + retenção
6. Build release endurecida

## Fase B - Preparar uso real com parceiros
7. Definir MVP comercial exato
8. Definir público inicial
9. Fluxo de suporte operacional
10. Testes críticos ampliados
11. Teste em aparelhos reais
12. Parceiro piloto real

## Fase C - Profissionalizar e escalar
13. Melhorar onboarding
14. Escolher próximos documentos prioritários
15. Melhorar persistência e produtividade
16. Telemetria de produção
17. Modelo comercial e implantação

## Execucao automatica recomendada
- Pipeline local unificado: `scripts/auto-max.ps1`
- Runbook: `docs/operations/automation-runbook.md`

---

# 5) O que eu faria agora, se o projeto fosse meu

## Próximos 5 passos imediatos
1. Fechar juridicamente o que pode e o que não pode ser prometido pelo app.
2. Definir se a assinatura atual é apenas operacional ou se haverá trilha jurídica formal.
3. Criptografar dados locais e arquivos sensíveis.
4. Revisar o template de ASO com especialista real da área.
5. Testar o app em aparelho Android real com fluxo completo: abrir -> gerar ASO -> assinar -> exportar -> diagnosticar.

---

# 6) Regra de ouro para o ANDA

Antes de adicionar muitos módulos novos, garantir 3 coisas no núcleo:
- **Confiabilidade**
- **Validade operacional/jurídica**
- **Velocidade real para o técnico em campo**

Se essas 3 estiverem fortes, o `Anda` tem base para virar produto líder.

