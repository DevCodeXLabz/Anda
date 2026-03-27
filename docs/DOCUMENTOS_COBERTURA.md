# Cobertura documental ANDA

## Status por documento

| Documento | Status atual | Fluxo |
|---|---|---|
| ASO | Implementado dedicado | Tela propria + assinatura + PDF + sync |
| PCMSO | Implementado dedicado | Tela propria + assinatura + PDF + sync |
| PGR | Implementado dedicado | Tela propria + assinatura + PDF + sync |
| APR | Implementado dedicado | Tela propria + assinatura + PDF + sync |
| CAT | Implementado dedicado | Tela propria + assinatura + PDF + sync |
| OS | Implementado dedicado | Tela propria + assinatura + PDF + sync |
| PPP | Implementado dedicado | Tela propria + assinatura + PDF + sync |
| AET | Implementado generico | Central avancada + assinatura + PDF + sync |
| LTCAT | Implementado generico | Central avancada + assinatura + PDF + sync |
| Inventario de riscos | Implementado generico | Central avancada + assinatura + PDF + sync |
| Laudo NR-10 | Implementado generico especializado | Central avancada + campos NR obrigatorios + assinatura + PDF + sync |
| Laudo NR-12 | Implementado generico especializado | Central avancada + campos NR obrigatorios + assinatura + PDF + sync |
| Laudo NR-20 | Implementado generico especializado | Central avancada + campos NR obrigatorios + assinatura + PDF + sync |
| PCA | Implementado generico especializado | Central avancada + campos obrigatorios + assinatura + PDF + sync |
| PPR | Implementado generico especializado | Central avancada + campos obrigatorios + assinatura + PDF + sync |
| PT | Implementado generico | Central avancada + assinatura + PDF + sync |
| Insalubridade | Implementado generico especializado | Central avancada + campos obrigatorios (agente/grau) + assinatura + PDF + sync |
| Periculosidade | Implementado generico especializado | Central avancada + campos obrigatorios (agente/exposicao) + assinatura + PDF + sync |
| Plano de acao | Implementado generico especializado | Central avancada + campos obrigatorios (risco/acao) + assinatura + PDF + sync |
| Resgate | Implementado generico | Central avancada + assinatura + PDF + sync |
| PGRTR | Implementado generico | Central avancada + assinatura + PDF + sync |
| Certificado EPI | Scanner + generico complementar | OCR CA + registro local |
| Mapa de risco | Implementado via generico | Registro tecnico estruturado |

## Proximo refinamento tecnico

1. Transformar cada documento generico em template dedicado conforme checklist legal detalhado.
2. Adicionar validacoes obrigatorias por norma antes da assinatura.
3. Expandir smart prefill cross-doc para demais telas dedicadas (prioridade: CAT/APR/OS/PGR/PPP/PCMSO/ASO) e registrar origem para auditoria.

## Inteligencia cross-doc (status)

- Smart prefill legal aplicado em `PPP`, `PCMSO`, `PGR`, `CAT`, `APR`, `OS` e `ASO` (somente campos vazios).
- Fonte de contexto: documento local mais recente da mesma empresa.
- Campos reutilizados: responsavel/medico, registro profissional (CRM/CREA), clinica/CNPJ clinica e emissor CAT.
- Auditoria operacional: UI informa origem do contexto reaproveitado (`tipo/id` do documento fonte).
- Cobertura de testes atualizada em `AutofillServiceTest` e `DocumentGenerationServiceTest`.

