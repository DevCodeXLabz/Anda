# 📱 ANDA - Checklist P0 de teste em aparelho Android real

Objetivo: validar o pacote técnico P0 antes de qualquer uso externo do app.

## Pré-condições
- APK `debug` e `release` gerados com sucesso.
- Aparelho Android com biometria configurada.
- Câmera funcional.
- Internet disponível para consultas externas e também teste offline controlado.

## 1. Instalação e abertura
- [ ] Instalar APK `debug`.
- [ ] Abrir o app 3 vezes seguidas sem crash.
- [ ] Instalar APK `release`.
- [ ] Abrir o app 3 vezes seguidas sem crash.

## 2. Fluxo ASO completo
- [ ] Abrir `ASO`.
- [ ] Confirmar que a tela ASO informa assinatura operacional/local antes de qualquer ação.
- [ ] Preencher empresa e funcionário.
- [ ] Gerar rascunho.
- [ ] Confirmar preview e status de rascunho salvo com aviso de ausencia de assinatura juridica formal.
- [ ] Assinar com biometria/credencial.
- [ ] Confirmar que o status pos-assinatura fala em assinatura operacional local.
- [ ] Exportar PDF e auditoria TXT.
- [ ] Confirmar que PDF e auditoria exportados trazem aviso operacional/juridico.
- [ ] Abrir detalhe do documento.
- [ ] Confirmar que o detalhe do documento nao promete validade juridica automatica.
- [ ] Compartilhar PDF.
- [ ] Compartilhar ZIP.

## 3. Segurança local
- [ ] Fechar e reabrir o app; confirmar que o documento continua acessível.
- [ ] Confirmar que o histórico de consulta de CNPJ continua legível no app.
- [ ] Confirmar que diagnóstico técnico continua funcionando.
- [ ] Validar que dados sensíveis não são perdidos após reinício.

## 4. Scanner C.A.
- [ ] Abrir scanner com câmera permitida.
- [ ] Ler texto manual.
- [ ] Validar comportamento com backend configurado.
- [ ] Validar comportamento em modo seguro.

## 5. Sync e fila operacional
- [ ] Criar documento offline.
- [ ] Forçar sincronização depois.
- [ ] Abrir lista de documentos.
- [ ] Verificar pendências, assinados e sincronizados.

## 6. Diagnóstico / modo seguro
- [ ] Abrir diagnóstico técnico.
- [ ] Copiar diagnóstico completo.
- [ ] Copiar diagnóstico privado.
- [ ] Exportar diagnóstico completo.
- [ ] Exportar diagnóstico privado.
- [ ] Reativar modo seguro.
- [ ] Voltar para Home e conferir timer.

## 7. Critério de pronto
O pacote P0 técnico só é considerado pronto quando:
- [ ] `debug` e `release` abrem sem crash no aparelho real.
- [ ] Fluxo ASO completo funciona.
- [ ] O mesmo disclaimer operacional aparece coerentemente em tela, status, documento e auditoria.
- [ ] Scanner e diagnóstico funcionam.
- [ ] Dados sensíveis continuam acessíveis no app após reinício.
- [ ] Build `release` continua gerando artefato instalável.

