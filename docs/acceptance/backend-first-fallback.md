# Criterios de aceite - Backend-first C.A.

## MVP
- App consulta `GET {CA_API_BASE_URL}/ca/{numero}` e exibe resultado estruturado.
- Em indisponibilidade do backend, app mantem fallback local sem travar fluxo.
- Em resposta `fallback`, app pode abrir automaticamente a consulta oficial (uma vez por C.A.) se `CA_OFFICIAL_CONSULT_URL` estiver configurada.
- Botao **Abrir consulta oficial** abre URL configurada em `CA_OFFICIAL_CONSULT_URL` sob demanda.
- Resposta backend segue contrato `docs/api/ca-contract.v1.json`.
- Backend aplica cache TTL, timeout/retry e rate limit simples.

## Validacao manual
1. Configurar `CA_API_BASE_URL` para backend local e abrir scanner.
2. Testar `C.A. 12345` e confirmar fonte `oficial`/`upstream` ou `fallback-local`.
3. Derrubar backend e testar novo C.A.; app deve responder com fallback local.
4. Forcar um caso de fallback e confirmar abertura automatica da consulta oficial.
5. Acionar botao de consulta oficial e confirmar abertura manual do navegador.

