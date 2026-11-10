# Contrato PagamentoConfirmado

O [JSON de exemplo](pagamento-confirmado.exemplo.json) é o corpo completo da
mensagem em `pagamento-confirmado` (Billing → OS), sem `payload` aninhado.
Os dados são ilustrativos. `pagamentoId` é texto e `valor` é número decimal.

- `sagaId` e `comandoId` vêm de ProcessarPagamento.
- `causationId` é o messageId de ProcessarPagamento, não o ID interno do SQS.
- `messageId` é um UUID próprio do evento. Republicações reutilizam o envelope original.
- OS, cliente e orçamento vêm do orçamento salvo. Pagamento, valor e moeda
  vêm do registro verificado. Criar o link de checkout não confirma o pagamento.

O Billing já tem o contrato e o mapeamento Java. A publicação automática ainda
requer guardar a correlação de ProcessarPagamento e persistir o evento junto da
confirmação (outbox), para que falhas de envio não percam nem dupliquem o evento.

## Resultado terminal de falha

O [exemplo de FALHA](billing-falha.exemplo.json) usa o mesmo formato plano.
O evento vai na fila correspondente ao comando: `orcamento-respondido` para
GerarOrcamento e `pagamento-confirmado` para ProcessarPagamento. O OS deve
verificar `status` antes de ler campos de sucesso e usar `erro.codigo` para
distinguir motivos, sem depender do texto de `erro.mensagem`.

O evento mantém todos os IDs de correlação. `orcamentoId` e `pagamentoId`
são omitidos quando não conhecidos; em uma falha antes de criar o orçamento,
nenhum dos dois precisa existir. Não inclui valor, moeda ou itens.

O modelo só representa erros definitivos. Timeout, indisponibilidade,
checkout criado e pagamento pendente não são `FALHA`. Mensagens sem IDs
válidos para correlacionar não devem receber IDs inventados. O roteamento,
a classificação das exceções e a publicação SQS ainda não estão conectados.
