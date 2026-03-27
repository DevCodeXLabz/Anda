# Semana 2 - Lote Tecnico Executado

## Escopo entregue
- Persistencia local com Room:
  - `data/local/AppDatabase.kt`
  - `data/local/entity/CompanyEntity.kt`
  - `data/local/entity/DocumentEntity.kt`
  - `data/local/entity/SyncQueueEntity.kt`
  - `data/local/dao/CompanyDao.kt`
  - `data/local/dao/DocumentDao.kt`
  - `data/local/dao/SyncQueueDao.kt`
- Fila de sincronizacao offline com backoff:
  - `data/sync/SyncQueueRepository.kt`
  - `data/sync/SyncScheduler.kt`
  - `data/sync/SyncPendingRecordsWorker.kt`
- Assinatura simples segura (sem certificado digital):
  - `feature/security/DocumentSignatureHelper.kt`
- Autofill basico por CNAE para ASO:
  - `data/services/AutofillService.kt`
- Teste unitario basico:
  - `app/src/test/java/com/example/anda/data/services/AutofillServiceTest.kt`

## Incremento adicional executado (lote pratico)
- Tela ASO funcional para uso real:
  - `app/src/main/java/com/example/anda/feature/aso/AsoActivity.kt`
  - `app/src/main/res/layout/activity_aso.xml`
  - `app/src/main/AndroidManifest.xml` (registro da activity)
- Assinatura local integrada ao fluxo de documento:
  - `feature/security/DocumentSignatureHelper.kt`
  - Persistencia de assinatura em `DocumentEntity` via `DocumentDao.markSigned(...)`
- Exportacao de PDF local sem custo adicional:
  - `app/src/main/java/com/example/anda/data/services/PdfExportService.kt`
- Home com atalho direto para ASO:
  - `app/src/main/res/layout/activity_main.xml`
  - `app/src/main/java/com/example/anda/MainActivity.kt`

## Lote complementar autorizado
- Tela de documentos locais com filtro e retry manual:
  - `app/src/main/java/com/example/anda/feature/documents/DocumentsActivity.kt`
  - `app/src/main/res/layout/activity_documents.xml`
  - Filtros por tipo e CNPJ + painel de pendencias de sync
- Repositorio/DAO com listagem filtrada:
  - `data/local/dao/DocumentDao.kt` (`listDocuments(...)`)
  - `data/repository/DocumentLocalRepository.kt` (`listDocuments`, `pendingSyncCount`)
- Validacoes de formulario na tela ASO:
  - CNPJ com 14 digitos
  - CPF com 11 digitos
  - CNAE/nome empresa/nome funcionario/medico obrigatorios

## Lote avancado (operacao e auditoria)
- Historico de tentativas de sincronizacao por documento:
  - `data/local/entity/SyncAttemptLogEntity.kt`
  - `data/local/dao/SyncAttemptLogDao.kt`
  - `data/local/AppDatabase.kt` (versao 2)
  - `data/sync/SyncQueueRepository.kt` (grava eventos PENDING/PROCESSING/DONE/FAILED)
- Tela de detalhe de documento com historico e acao de forcar sync:
  - `feature/documents/DocumentDetailActivity.kt`
  - `res/layout/activity_document_detail.xml`
  - Registro no `AndroidManifest.xml`
- Lista de documentos com clique em item para abrir detalhe:
  - `feature/documents/DocumentsActivity.kt`
  - `res/layout/activity_documents.xml` (ListView)
- Exportacao PDF local melhorada:
  - `data/services/PdfExportService.kt` com paginacao, quebra de linha e rodape de pagina

## Lote extra (usabilidade operacional)
- Abertura de PDF exportado na tela de detalhe:
  - `feature/documents/DocumentDetailActivity.kt`
  - `res/layout/activity_document_detail.xml`
  - `AndroidManifest.xml` + `res/xml/file_paths.xml` (FileProvider)
- Reprocessamento manual de falhas de sync:
  - `data/local/dao/SyncQueueDao.kt` (`retryAllFailedNow`)
  - `data/sync/SyncQueueRepository.kt`
  - `data/repository/DocumentLocalRepository.kt`
  - `feature/documents/DocumentsActivity.kt` (botao "Reprocessar apenas falhas")
- Status de pendencias com refresh periodico em tela de documentos (5s)
- Persistencia de caminho PDF no documento local:
  - `data/local/entity/DocumentEntity.kt` (`pdfPath`)
  - `data/local/dao/DocumentDao.kt` (`markPdfExported`)
  - `feature/aso/AsoActivity.kt` salva metadado apos exportar

## Proximo salto executado
- Home com atalho para abrir o ultimo documento local:
  - `res/layout/activity_main.xml`
  - `res/values/strings.xml`
  - `MainActivity.kt`
  - `DocumentDao.findLatest()` + `DocumentLocalRepository.findLatestDocumentId()`
- Lista de documentos com severidade visual:
  - `DocumentsActivity.kt` (cores semaforicas por pendencia e contadores)
  - `activity_documents.xml` (cards de status)
- Abertura direta de PDF pela lista (pressionar e segurar item)
- Compartilhamento de PDF no detalhe:
  - `activity_document_detail.xml` (botao)
  - `DocumentDetailActivity.kt` (intent ACTION_SEND)

## Prox salto (auditoria e lote operacional)
- Exportacao de auditoria TXT junto com exportacao de PDF no ASO:
  - `data/services/AuditExportService.kt`
  - `feature/aso/AsoActivity.kt`
- Filtro por status na lista de documentos:
  - `activity_documents.xml` (`statusFilterInput`)
  - `DocumentsActivity.kt` (`ALL`, `DRAFT`, `SIGNED`, `SYNCED`)
- Acao em lote para assinados nao sincronizados:
  - `DocumentDao.listSignedUnsyncedDocumentIds(...)`
  - `DocumentLocalRepository.forceSyncSignedUnsyncedBatch(...)`
  - `DocumentsActivity.kt` (botao `syncSignedUnsyncedButton`)

## Salto extra (acabamento premium)
- Filtros rapidos por status (ALL/DRAFT/SIGNED/SYNCED) na tela de documentos
- Exportacao e compartilhamento de auditoria TXT no detalhe:
  - `DocumentDetailActivity.kt`
  - `activity_document_detail.xml`
- Resumo operacional na home:
  - `MainActivity.kt`
  - `activity_main.xml`
  - `strings.xml`
  - mostra docs locais, pendencias e ultimo documento

## Salto autorizado final (operacao forte)
- "Sincronizar tudo agora" com feedback visual:
  - `activity_documents.xml` (`syncAllNowButton`, `syncProgress`)
  - `DocumentsActivity.kt` (travamento de botoes + progresso por etapa)
- Pacote ZIP (PDF + TXT auditoria) no detalhe:
  - `ZipBundleExportService.kt`
  - `DocumentDetailActivity.kt` (`exportZipButton`, `shareZipButton`)
- Resumo da home ampliado:
  - total de docs, pendencias, falhas e assinados hoje
  - `DocumentDao`, `SyncQueueDao`, `DocumentLocalRepository`, `MainActivity`

## Retomada - entrega complementar
- Alertas de validade local:
  - `DocumentEntity.validUntil`
  - contador `Vencendo 30d` na home (`DocumentDao.countExpiringBetween`)
  - filtro rapido `VENCENDO30D` na lista de documentos
- Sync em lote total mantido e validado com barra de progresso
- Pacote ZIP (PDF + auditoria TXT) consolidado no detalhe do documento

## Sprint de confiabilidade e economia de requests
- Fila de sync deduplicada por `itemType + itemRef`:
  - `SyncQueueEntity.kt` com indice unico
- Sync correto de documentos:
  - `SyncQueueRepository.kt` agora marca `DocumentEntity.isSynced = true` em sucesso
  - `DocumentDao.markSigned(...)` volta documento para `isSynced = false` quando ha nova assinatura/alteracao
- Tipagem correta da fila:
  - `SstRepository.kt` usa `enqueueCompanySync(...)` para empresa em vez de tratar como documento
- Autofill local mais inteligente:
  - `AutofillService.kt` extrai contexto do ultimo ASO da mesma empresa
  - `AsoActivity.kt` reaproveita clinica/CNPJ clinica/CRM anteriores quando o usuario deixa em branco

## Como validar localmente
```powershell
cd "C:\Users\heinz\Desktop\Oficina\ANDAAPP\Anda"
./gradlew test
./gradlew build
```

## Observacoes
- Nao foi adicionada IA (conforme decisao).
- Nao foi criado frontend web (somente app Android).
- Assinatura digital ICP foi adiada; no MVP usamos biometria + hash + trilha de auditoria.

