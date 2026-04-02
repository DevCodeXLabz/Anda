FINAL MARKET-READY CHECKLIST — ANDA
=================================

Data: 2026-04-01

Objetivo
--------
Check-list consolidado para preparar o app ANDA para lançamento comercial. Prioridades: P0 (bloqueadores), P1 (profissionalização), P2 (escala).

Como usar
---------
- Cada item tem: Entregável | Critério de aceite | Dono sugerido | Status (TODO/DOING/DONE)
- Comece pelos P0; não avance para P1 se houver P0 em aberto que bloqueie legal/segurança.

P0 — Bloqueadores (Obrigatório)
--------------------------------

1) LGPD, Privacidade e Termos
   - Entregável: Política de Privacidade pública + Termos de Uso + descrição da base legal por tipo de dado + política de retenção/exclusão
   - Critério de aceite: Links/telas ativos no app; opção de consentimento registrada (timestamp + versão); fluxo de revogação funcionando.
   - Dono: Jurídico / Produto
   - Status: TODO

2) Assinatura digital / validade jurídica
   - Entregável: Decisão e implementação mínima da estratégia de assinatura (ICP-Brasil vs assinatura eletrônica com disclaimer)
   - Critério de aceite: Documentos importantes mostram status de assinatura; texto jurídico revisado e aprovado.
   - Dono: Produto / Jurídico
   - Status: TODO

3) Criptografia em repouso
   - Entregável: Criptografia de dados sensíveis local (Android Keystore + SQLCipher ou encriptação por campo)
   - Critério de aceite: Dados sensíveis não aparecem em texto puro em backup/DB; auditoria técnica aprovada.
   - Dono: Engenharia
   - Status: TODO

4) Hardening de release
   - Entregável: Build de release com minify/obfuscation (ProGuard/R8), logs controlados, Network Security Config, TLS estrito
   - Critério de aceite: Build de release criado por pipeline/documentado; verificação manual de strings sensíveis removidas.
   - Dono: Engenharia
   - Status: TODO

5) Backup/restore seguro
   - Entregável: Política e implementação de backup/restore que protege dados sensíveis
   - Critério de aceite: Teste de restore em dispositivo limpo sem vazamentos.
   - Dono: Engenharia
   - Status: TODO

6) Integração de alertas na UX principal
   - Entregável: Links/entrypoints em `CompanyHomeActivity` e `TechnicianHomeActivity` para `ComplianceAlertsActivity` e deep link funcional
   - Critério de aceite: Usuário acessa alertas em até 2 toques; deep link `anda://app/compliance_alerts` abre a activity
   - Dono: Engenharia / UX
   - Status: TODO

P1 — Forte impacto (Profissionalização)
--------------------------------------

1) Testes automatizados críticos
   - Entregável: Unit tests e instrumented tests para Worker, Repositorios, Permissões, Export
   - Critério de aceite: Pipeline roda testes; cobertura mínima nos fluxos críticos.
   - Dono: Engenharia
   - Status: TODO

2) Observabilidade e Crash Reporting
   - Entregável: Crashlytics/relatório de falhas e eventos telemetria para eventos-chave
   - Critério de aceite: Crash de teste aparece no painel; eventos são recebidos
   - Dono: Engenharia / Operações
   - Status: TODO

3) UX de erros/offline
   - Entregável: Estados loading/empty/error/retry em telas core
   - Critério de aceite: Testes manuais cobrindo offline e erros
   - Dono: Engenharia / UX
   - Status: TODO

4) Sync visibility
   - Entregável: Indicadores de sync pendente/erro no app
   - Critério de aceite: Usuário visualiza e repassa itens com erro
   - Dono: Engenharia
   - Status: TODO

P2 — Escala e diferencial
-------------------------

1) CI/CD e scans automáticos
   - Entregável: Pipeline com lint, tests, dependency scans, security checks
   - Critério de aceite: Pipeline green para PRs e releases
   - Dono: Engenharia
   - Status: TODO

2) Métricas de produto e dashboards
   - Entregável: Dashboards com ativação e uso por módulo
   - Critério de aceite: Painel acessível ao time de produto
   - Dono: Produto / Engenharia
   - Status: TODO

3) Suporte e KBase
   - Entregável: Fluxo de suporte in-app, templates e SLA
   - Critério de aceite: Teste de abertura/fechamento de ticket
   - Dono: Operações
   - Status: TODO

Execução recomendada (30 dias inicial)
-------------------------------------
- Semana 1-2: Fechar P0 jurídico e segurança (itens 1-5 P0)
- Semana 3: Fechar integração de alertas na UX + revisão com especialista SST
- Semana 4: QA automatizado básico + observabilidade
- Semanas 5-8: Implantar P2 e preparação comercial/piloto

Anexos e referências
---------------------
- Ver: docs/PRODUCTION_CHECKLIST.md
- Ver: docs/ROADMAP.md

---
Atualize o campo "Status" neste arquivo conforme as tarefas forem executadas. Se quiser, posso converter cada item em uma issue (GitHub/GitLab/Jira) com checklist e responsáveis — quer que eu gere issues automaticamente?

