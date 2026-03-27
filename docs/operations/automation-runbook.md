# Automacao Maxima - ANDA

Objetivo: executar tudo que e possivel automaticamente, sem aparelho fisico.

## 1) Pipeline local unico
Na raiz do projeto:

```powershell
Set-Location "C:\Users\heinz\Desktop\Oficina\ANDAAPP\Anda"
.\scripts\auto-max.ps1
```

Esse script executa:
- `:app:testDebugUnitTest`
- `:app:lintDebug` e `:app:lintRelease`
- `:app:assembleDebug` e `:app:assembleRelease`
- `npm ci` e `npm test` no `backend`

Politica de robustez:
- Etapas Gradle (Android) possuem retry automatico e limitado para falhas transitorias conhecidas
  (ex.: daemon desapareceu, OOM da JVM, `java heap space`).
- Etapas de backend continuam em fail-fast (sem retry cego) para nao mascarar erro funcional.

Parametros opcionais (sem alterar o padrao quando omitidos):
- `-Mode <full|fast>` (padrao: `full`)
- `-GradleMaxRetries <0..5>` (padrao: `2`)
- `-GradleRetryDelaySeconds <1..120>` (padrao: `8`)
- `-PrintConfig` (imprime configuracao efetiva no inicio)
- `-ShowStepTiming` (imprime duracao por etapa)
- `-SummaryAsJson` (emite uma linha JSON no stdout ao final, em sucesso ou falha do pipeline)
- `-SummaryJsonPath <caminho>` (salva o JSON de resumo em arquivo ao final, em sucesso ou falha)
- `-FailOnSummaryWriteError` (modo estrito: falha o run se nao conseguir salvar `SummaryJsonPath` quando o pipeline estiver ok)
- `-SkipAndroidLint` (pula `lintDebug` + `lintRelease`)
- `-SkipAndroidAssemble` (pula `assembleDebug` + `assembleRelease`)

Precedencia:
- `-Mode` define os valores padrao de skip (`full` = nao pula, `fast` = pula lint + assemble).
- Se `-SkipAndroidLint` e/ou `-SkipAndroidAssemble` forem informados explicitamente, eles sobrescrevem o `-Mode` para cada flag.

Exemplo:

```powershell
Set-Location "C:\Users\heinz\Desktop\Oficina\ANDAAPP\Anda"
.\scripts\auto-max.ps1 -PrintConfig -ShowStepTiming -GradleMaxRetries 2 -GradleRetryDelaySeconds 8
```

Loop local mais rapido (nao usar para aceite final):

```powershell
Set-Location "C:\Users\heinz\Desktop\Oficina\ANDAAPP\Anda"
.\scripts\auto-max.ps1 -Mode fast -ShowStepTiming
```

Exemplo de override explicito no modo rapido (executa assemble mesmo em `fast`):

```powershell
Set-Location "C:\Users\heinz\Desktop\Oficina\ANDAAPP\Anda"
.\scripts\auto-max.ps1 -Mode fast -SkipAndroidAssemble:$false -ShowStepTiming
```

Ao final da execucao, o script imprime um resumo objetivo com:
- `Mode` e os valores efetivos de skip
- status por grupo de etapa (`executed`/`skipped`)

Isso facilita auditoria rapida do que realmente rodou em cada chamada.

Se precisar integrar com parser/CI, use o resumo em JSON:

```powershell
Set-Location "C:\Users\heinz\Desktop\Oficina\ANDAAPP\Anda"
$summary = .\scripts\auto-max.ps1 -Mode fast -SummaryAsJson
$summary | Out-File -FilePath ".\tmp-auto-max-summary.json" -Encoding utf8
```

Opcao mais robusta (nao depende de captura de stdout):

```powershell
Set-Location "C:\Users\heinz\Desktop\Oficina\ANDAAPP\Anda"
.\scripts\auto-max.ps1 -Mode fast -SummaryJsonPath ".\tmp-auto-max-summary.json"
```

Modo estrito para CI (garante arquivo de resumo):

```powershell
Set-Location "C:\Users\heinz\Desktop\Oficina\ANDAAPP\Anda"
.\scripts\auto-max.ps1 -Mode full -SummaryJsonPath ".\tmp-auto-max-summary.json" -FailOnSummaryWriteError
```

Campos principais do JSON:
- `schemaVersion` (`1.2`), `outcome`, `exitCode`
- `mode`, `effectiveSkipAndroidLint`, `effectiveSkipAndroidAssemble`
- `summaryWriteStatus`, `summaryFilePath`, `summaryWriteError`
- `failedStage` (quando houver falha)
- `startedAtUtc`, `finishedAtUtc`, `durationMs`
- `stages` (status por grupo) e `error` (quando houver)

Valores esperados de `summaryWriteStatus`:
- `not_requested`: nao foi solicitado `SummaryJsonPath`
- `written`: resumo salvo com sucesso em arquivo
- `write_failed`: tentativa de salvar falhou

## 2) Artefatos esperados
- APK debug: `app/build/outputs/apk/debug/`
- APK release: `app/build/outputs/apk/release/`
- Relatorios lint: `app/build/reports/lint-results-*.html`

## 3) O que ainda depende de aparelho real
Mesmo com automacao maxima, estes itens continuam manuais:
- camera real (scanner C.A.)
- biometria/credencial real no fluxo ASO
- validacao UX operacional em campo

Use o checklist:
- `docs/android-p0-real-device-checklist.md`

## 4) Regra de aceite tecnico
Considerar aprovado apenas quando:
1. script `auto-max.ps1` conclui sem falha
2. APK `release` instala e abre no aparelho real
3. checklist de aparelho real passa nos itens criticos (ASO, scanner, diagnostico)

Observacao: modos com `-SkipAndroidLint` e/ou `-SkipAndroidAssemble` sao apenas para iteracao local, nao substituem o aceite tecnico completo.

