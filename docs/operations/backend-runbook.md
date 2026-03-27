# Runbook - Anda C.A. Backend

## Subir localmente
```powershell
Set-Location "C:\Users\heinz\Desktop\Oficina\ANDAAPP\Anda\backend"
npm install
npm test
npm run start
```

## Health check
```powershell
Invoke-RestMethod http://localhost:8080/health
Invoke-RestMethod http://localhost:8080/home/metrics
```

## Admin rapido
```powershell
$token = "troque-este-token"
Invoke-RestMethod http://localhost:8080/admin/metrics -Headers @{"X-Admin-Token"=$token}
Invoke-RestMethod http://localhost:8080/admin/cache/clear -Method Post -Headers @{"X-Admin-Token"=$token}
Invoke-RestMethod http://localhost:8080/admin/cache/warmup -Method Post -Headers @{"X-Admin-Token"=$token;"Content-Type"="application/json"} -Body '{"cas":["12345","98765"]}'
```

Dashboard web:
`http://localhost:8080/admin/dashboard?token=troque-este-token`

## Seguranca admin (recomendado)
Use hash SHA-256 em `CA_ADMIN_TOKEN_SHA256` e deixe `CA_ADMIN_TOKEN` vazio.

```powershell
node -e "console.log(require('crypto').createHash('sha256').update('seu-token').digest('hex'))"
```

Restrinja tambem por IP:
- `CA_ADMIN_IP_ALLOWLIST=127.0.0.1,::1`
- CIDR IPv4 tambem e suportado: `CA_ADMIN_IP_ALLOWLIST=10.10.0.0/16,127.0.0.1`
- CIDR IPv6 tambem e suportado: `CA_ADMIN_IP_ALLOWLIST=2001:db8:abcd::/48,::1`

Bloqueio por metodo/rota (opcional):
- `CA_ADMIN_BLOCK_RULES=POST:/cache/clear,POST:/cache/warmup`

## Warmup no boot
- `CA_WARMUP_ON_BOOT=true`
- `CA_WARMUP_CAS=12345,98765`

Ao subir, o backend ja preenche o cache para os C.A.s informados.

## Logs
O backend escreve logs JSON em stdout/stderr com campos como:
- `timestamp`
- `level`
- `service`
- `message`
- `request_id`
- `duration_ms`
- `ca`

Eventos mais importantes:
- `server_started`
- `request_started`
- `request_completed`
- `ca_cache_hit`
- `ca_upstream_success`
- `ca_upstream_unavailable`
- `ca_fallback_local_served`
- `rate_limited`

## Troubleshooting rapido
### `/health` nao sobe
- conferir `PORT`
- verificar se outra aplicacao ja usa a porta 8080
- rodar `npm test` para validar o backend antes do start

### muitas respostas `fallback`
- conferir `CA_UPSTREAM_URL`
- reduzir timeout/retry so se o upstream estiver travando o fluxo
- consultar logs `ca_upstream_attempt_failed` e `ca_upstream_unavailable`
- verificar em `admin/metrics` se `cacheEntries` e `upstreamFailures` estao subindo

### muitas respostas `429`
- aumentar `CA_RATE_LIMIT_MAX`
- aumentar `CA_RATE_LIMIT_WINDOW_MS`
- considerar um proxy/load balancer se o uso crescer

### cache nao persiste entre reinicios
- conferir `CA_CACHE_FILE_PATH`
- conferir permissao de escrita da pasta
- checar logs `ca_cache_persist_failed`

## Deploy rapido com Docker
```powershell
Set-Location "C:\Users\heinz\Desktop\Oficina\ANDAAPP\Anda\backend"
docker build -t anda-ca-backend .
docker run --rm -p 8080:8080 --env-file .env anda-ca-backend
```

## Integracao com o app Android
No `gradle.properties`:
```properties
CA_API_BASE_URL=http://10.0.2.2:8080
CA_OFFICIAL_CONSULT_URL=https://seu-endereco-oficial/consulta?ca={ca}
```

