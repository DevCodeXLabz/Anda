# Anda - SST MVP (Start pratico)

Base inicial do app Android para Seguranca e Medicina do Trabalho, focada em executar rapido para teste real.

## O que ja existe
- `MVVM leve + ViewBinding + WorkManager`
- `SstRepository` em memoria (estado reativo)
- Consulta CNPJ real com BrasilAPI
- Mapeamento automatico CNAE -> grau de risco
- Historico offline local de consultas
- Scanner C.A. real com CameraX + OCR (ML Kit) + fallback manual
- Validacao de C.A. backend-first (endpoint configuravel) + fallback local
- Atalho para abrir consulta oficial no navegador quando necessario
- Autoabertura da consulta oficial quando o retorno vier em fallback
- Backend com painel admin (`/admin/dashboard`) e limpeza de cache sob token
- Pipeline CI automatica para backend + testes unitarios Android
- Seguranca admin com token hash (`CA_ADMIN_TOKEN_SHA256`) e allowlist de IP (`CA_ADMIN_IP_ALLOWLIST`)
- Status de backend visivel na home e no scanner, com monitoramento periodico
- Endpoint `GET /home/metrics` para status operacional leve da home
- Badge visual de severidade de fallback na home (NORMAL/MODERADO/ALTO/CRITICO)
- Tela inicial com dois testes manuais:
  - inserir dados demo
  - sincronizar pendencias
- Agendamento de sincronizacao em background pelo `AndaApplication`

## Fluxo de teste rapido
1. Abrir o app.
2. Digitar CNPJ e tocar em **Consultar CNPJ (BrasilAPI)**.
3. Conferir dados da empresa e grau de risco sugerido.
4. Conferir historico offline na tela principal.
5. Abrir **Scanner de C.A.** e testar deteccao por camera e fallback manual (ex.: `C.A. 12345`).
6. Tocar em **Sincronizar pendencias** e validar que pendencias vao para zero.

## Estrutura principal
- `app/src/main/java/com/example/anda/MainActivity.kt`
- `app/src/main/java/com/example/anda/presentation/MainViewModel.kt`
- `app/src/main/java/com/example/anda/data/repository/SstRepository.kt`
- `app/src/main/java/com/example/anda/data/sync/SyncPendingRecordsWorker.kt`
- `app/src/main/java/com/example/anda/data/sync/SyncScheduler.kt`

## Proximos modulos
1. Conectar endpoint oficial definitivo de C.A. no `CA_API_BASE_URL`
2. Persistencia offline com Room
3. Gerador de PDF de documentos SST
4. Regras anti-conflito eSocial

## Configurar validacao oficial de C.A.
Defina no `gradle.properties` (ou via CI) a URL base do seu backend e a URL oficial de consulta:

```properties
CA_API_BASE_URL=https://seu-backend-ca
CA_OFFICIAL_CONSULT_URL=https://seu-endereco-oficial/consulta?ca={ca}
```

O app consulta `GET {CA_API_BASE_URL}/ca/{numero}` e faz parse tolerante de campos comuns.
Se o backend nao responder, o app usa fallback local para nao interromper o trabalho em campo.
Na tela do scanner, o botao **Abrir consulta oficial** usa `CA_OFFICIAL_CONSULT_URL` (com placeholder `{ca}` opcional).

O backend MVP inclui cache em memoria com TTL, timeout/retry para upstream, `request_id` por chamada e rate limit simples.

