# Contrato PagamentoConfirmado

O [JSON de exemplo](pagamento-confirmado.exemplo.json) é o corpo completo da
mensagem em `pagamento-confirmado` (Billing → OS), sem `payload` aninhado.
Os dados são ilustrativos. `pagamentoId` é texto e `valor` é número decimal.

- `sagaId` e `comandoId` vêm de ProcessarPagamento.
- `versao` é um inteiro positivo, obrigatório no sucesso. O OS deve enviar a
  versão do orçamento que aprovou em ProcessarPagamento; o Billing devolve
  esse mesmo valor para o OS repassar em AgendarExecucao. Não é a versão do schema.
- `causationId` é o messageId de ProcessarPagamento, não o ID interno do SQS.
- `messageId` é um UUID próprio do evento. Republicações reutilizam o envelope original.
- OS, cliente e orçamento vêm do orçamento salvo. Pagamento, valor e moeda
  vêm do registro verificado. Criar o link de checkout não confirma o pagamento.

O worker financeiro opt-in agora guarda a correlação de ProcessarPagamento e
publica eventos via outbox. Consulte o [contrato completo](comandos-pagamento-mesma-fila.md)
para type, CheckoutCriado, compensação, segurança e variáveis de habilitação.

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
a classificação de falhas e a publicação financeira estão ligadas apenas no worker
opt-in. A entrada/saída SQS de orçamento continua pendente.
