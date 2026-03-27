# Anda C.A. Backend (MVP)

Servico intermediario para consulta de C.A. no app Android.

## Requisitos
- Node.js 20+

## Executar localmente
```bash
npm install
npm test
npm run start
```

Servidor sobe em `http://localhost:8080` por padrao.

## Endpoints
- `GET /health`
- `GET /home/metrics`
- `GET /ca/:numero`
- `GET /admin/metrics` (requer `X-Admin-Token`)
- `POST /admin/cache/clear` (requer `X-Admin-Token`)
- `POST /admin/cache/warmup` (requer `X-Admin-Token`)
- `GET /admin/dashboard?token=...`

### Exemplo de resposta
```json
{
  "ca": "12345",
  "is_valid": true,
  "item_name": "Protetor auricular tipo plug",
  "risk_coverage": "Ruido ocupacional",
  "valid_until": "2028-12-31",
  "source": "fallback-local",
  "status_reason": null,
  "cached": false,
  "request_id": "req-1700000000000-1"
}
```

## Variaveis de ambiente
Copie `.env.example` para `.env` e ajuste:
- `PORT`: porta do backend.
- `CA_ADMIN_TOKEN`: token simples para rotas administrativas (modo legado).
- `CA_ADMIN_TOKEN_SHA256`: hash SHA-256 do token admin (recomendado).
- `CA_ADMIN_IP_ALLOWLIST`: lista CSV de IPs autorizados para `/admin`.
- `CA_ADMIN_BLOCK_RULES`: lista CSV de regras `METHOD:/rota` para bloqueio administrativo.
- `CA_UPSTREAM_URL`: URL base opcional de um upstream real (`GET {base}/ca/{numero}`).
- `CA_CACHE_TTL_MS`: TTL de cache para respostas confiaveis.
- `CA_NEGATIVE_CACHE_TTL_MS`: TTL menor para fallback/not found.
- `CA_CACHE_PERSIST_ENABLED`: habilita/desabilita persistencia em disco do cache.
- `CA_CACHE_FILE_PATH`: arquivo JSON para persistir cache entre reinicios.
- `CA_CACHE_FLUSH_DEBOUNCE_MS`: debounce da gravacao em disco.
- `CA_UPSTREAM_TIMEOUT_MS`: timeout de chamada upstream.
- `CA_UPSTREAM_RETRIES`: numero de retentativas para erro transitorio.
- `CA_FALLBACK_WINDOW_MS`: janela para calcular taxa de fallback em tempo real.
- `CA_RATE_LIMIT_WINDOW_MS` / `CA_RATE_LIMIT_MAX`: protecao basica contra excesso de consultas.
- `CA_WARMUP_ON_BOOT`: habilita warmup no startup.
- `CA_WARMUP_CAS`: lista CSV de C.A.s para pre-aquecer no boot.

## Diferenciais deste MVP
- Cache em memoria com TTL para reduzir latencia e carga.
- Cache persistente em disco para manter desempenho apos restart.
- Timeout + retry para upstream instavel.
- `X-Request-Id` e `request_id` no JSON para rastreio.
- Rate limit simples por IP na rota de consulta.
- `GET /health` devolve metricas basicas de cache/upstream/fallback.
- Logs JSON estruturados para operacao e troubleshooting.
- Dashboard admin leve para operacao manual.
- Endpoint de warmup para preaquecer C.A.s criticos.

## Seguranca admin recomendada
Use preferencialmente `CA_ADMIN_TOKEN_SHA256` ao inves de guardar token em texto puro.
Exemplo para gerar hash:

```bash
node -e "console.log(require('crypto').createHash('sha256').update('seu-token').digest('hex'))"
```

Para restringir a superficie de ataque, configure tambem `CA_ADMIN_IP_ALLOWLIST`.
Exemplo:

```properties
CA_ADMIN_IP_ALLOWLIST=127.0.0.1,::1
```

Tambem aceita CIDR IPv4:

```properties
CA_ADMIN_IP_ALLOWLIST=10.10.0.0/16,127.0.0.1
```

Tambem aceita IPv6/CIDR:

```properties
CA_ADMIN_IP_ALLOWLIST=2001:db8:abcd::/48,::1
```

Exemplo de bloqueio por metodo/rota:

```properties
CA_ADMIN_BLOCK_RULES=POST:/cache/clear,POST:/cache/warmup
```

## Deploy
### Docker
```bash
docker build -t anda-ca-backend .
docker run --rm -p 8080:8080 --env-file .env anda-ca-backend
```

### Render
O projeto raiz ja inclui `render.yaml` apontando para `backend/`.

## Operacao
- Runbook: `docs/operations/backend-runbook.md`
- Logs incluem eventos como `request_completed`, `ca_cache_hit`, `ca_upstream_unavailable` e `rate_limited`.

## Integracao com o app
No `gradle.properties` do app:
```properties
CA_API_BASE_URL=http://10.0.2.2:8080
CA_OFFICIAL_CONSULT_URL=https://seu-endereco-oficial/consulta?ca={ca}
```

