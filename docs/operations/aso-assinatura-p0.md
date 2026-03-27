# ANDA - Decisao P0 sobre assinatura do ASO

## Objetivo
Deixar claro, no MVP atual, que o `Anda` registra **assinatura operacional/local** para rastreabilidade, mas **nao declara automaticamente validade juridica formal** do documento.

## Estado atual decidido
- O app registra confirmacao local por biometria/credencial do aparelho.
- Esse registro serve para:
  - auditoria interna
  - rastreabilidade operacional
  - reducao de conflito sobre quem confirmou a emissao local
- Esse registro **nao substitui**, sozinho:
  - assinatura juridica formal
  - certificado ICP-Brasil
  - exigencia documental definida pela operacao
  - responsabilidade do medico/responsavel tecnico habilitado

## O que o app deve dizer (regra P0)
O app deve repetir essa logica em 3 pontos:
1. **Antes da assinatura**
   - informar que o fluxo registra assinatura operacional local
2. **Depois da assinatura**
   - informar que houve registro local, mas nao declaracao juridica automatica
3. **No documento/exportacao**
   - informar que o documento foi gerado para apoio operacional e rastreabilidade

## Roteiro de decisao para voce
Escolha uma dessas trilhas para a fase seguinte:

### Opcao A - MVP operacional puro
- O app continua com assinatura local apenas
- Melhor para lancar rapido e testar campo
- Exige texto juridico claro para nao prometer valor formal automatico

### Opcao B - Integracao futura com assinatura juridica de terceiro
- O app segue como motor operacional
- A formalizacao juridica ocorre por parceiro externo / fluxo especifico
- Boa opcao para evolucao sem travar o MVP

### Opcao C - Estrategia juridica propria mais robusta
- Exige definicao tecnica, juridica e comercial mais profunda
- Nao recomendada como proximo passo imediato do MVP

## Recomendacao tecnica atual
Se o projeto fosse meu, eu seguiria com a **Opcao A agora** e deixaria a **Opcao B como proxima fase planejada**.

Motivos:
- reduz risco de prometer algo juridicamente sensivel antes da hora
- permite validar o produto com clinicas e tecnicos rapidamente
- preserva o valor principal do app: velocidade, organizacao e rastreabilidade

## Criterio de pronto desta etapa
Esta etapa e considerada pronta quando:
- o fluxo ASO fala claramente em assinatura operacional/local
- o rodape do documento nao promete validade juridica automatica
- auditoria/exportacao repetem o aviso corretamente
- o time sabe qual trilha (A, B ou C) sera adotada depois

## Estado da implementacao no app
- Disclaimer operacional centralizado no codigo para reduzir divergencia entre tela, PDF, HTML e auditoria.
- Fluxo ASO reforcado com bloqueio inicial de assinatura/exportacao antes da geracao do rascunho.
- Checklist de aparelho real atualizado para exigir verificacao do mesmo aviso em varios pontos do fluxo.

