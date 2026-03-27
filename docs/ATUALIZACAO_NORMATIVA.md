# Rotina de atualizacao normativa (NR/eSocial)

## Objetivo
Manter documentos e validacoes sempre alinhados com normas vigentes, evitando retrabalho e risco de nao conformidade.

## Ciclo operacional (semanal)

1. Verificar alteracoes em NRs aplicaveis e manual eSocial.
2. Registrar mudancas no changelog interno.
3. Marcar documentos impactados e campos afetados.
4. Atualizar templates HTML e validacoes do app.
5. Executar checklist de regressao documental.
6. Publicar versao com nota tecnica resumida.

## Fontes prioritarias

- Portal gov.br (MTE e Previdencia)
- Documentacao oficial eSocial
- Publicacoes tecnicas de associacoes SST

## Checklist minimo por mudanca

- Campos obrigatorios por documento revisados
- Textos legais atualizados no rodape
- Regras de assinatura e rastreabilidade revisadas
- Validacoes de CNPJ/CPF/datas mantidas
- Exportacao PDF testada
- Sincronizacao e historico de auditoria testados

## Automacao recomendada (fase seguinte)

- Script operacional: `scripts/normative-update.ps1`
- Fontes configuraveis: `docs/operations/normative-sources.json`
- Estado de ultimo hash: `docs/operations/normative-state.json`
- Log de alteracoes detectadas: `docs/operations/normative-changelog.md`

## Execucao rapida

```powershell
Set-Location "C:\Users\heinz\Desktop\Oficina\ANDAAPP\Anda"
powershell -ExecutionPolicy Bypass -File ".\scripts\normative-update.ps1"
```

## Resultado esperado

- Sem alteracoes: atualiza somente estado interno.
- Com alteracoes/erros: adiciona entrada no changelog para abrir revisao de impacto.

