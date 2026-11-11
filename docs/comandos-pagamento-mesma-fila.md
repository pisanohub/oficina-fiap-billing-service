# Pagamento e compensação na mesma fila

## Decisão e limites

Foi adotada a segunda opção proposta por Souza: fila de entrada única
`processar-pagamento`, JSON plano e discriminador obrigatório `type`.
Tipos aceitos: `ProcessarPagamento` e `EstornarPagamento`. Não há fila
`estornar-pagamento`, nem fallback silencioso quando type está ausente.
Os exemplos foram propostos a partir dos DTOs do OS; validar o consumidor
do OS com os novos tipos/status antes de habilitar a integração.

Isso implementa o caminho financeiro, não o consumidor de gerar-orcamento.
A versão já solicitada por Souza também foi preservada: inteiro positivo,
mesmo orçamento e mesma versão entre pagamento e compensação.

## Entrada — OS → Billing

- [ProcessarPagamento](processar-pagamento.exemplo.json): cria/reutiliza preferência
  usando o orçamento salvo; nunca recebe valor, cartão ou senha.
- [EstornarPagamento](estornar-pagamento.exemplo.json): exige pagamentoId registrado,
  correspondente ao orçamento, à Saga e à versão do checkout original.
- comandoId: UUID próprio de cada ação. Reenvio do mesmo comando mantém esse ID.
- messageId: ID da mensagem de comando; a primeira mensagem recebida é preservada.
  Reenvios com outro messageId não mudam o causationId já guardado.
- Mensagens inválidas e comandos conflitantes não são confirmados; seguem retry/DLQ.
- Mesmo checkout ou mesmo pagamento a estornar não pode ser reservado por
  outro comando para contornar idempotência. Corrigir resultado definitivo
  ou incierto exige análise, não apagar reservas nem inventar outro ID.

## Saída — Billing → OS, mesma fila pagamento-confirmado

Todos os resultados são planos e possuem type, messageId, sagaId, causationId,
comandoId, ordemId, clienteId, orcamentoId e versao. O OS deve distinguir:

| type | status | Significado |
|---|---|---|
| CheckoutCriado | CHECKOUT_CRIADO | Link pronto; NÃO avançar como pagamento aprovado. Inclui checkoutUrl e, quando disponível, checkoutTesteUrl. |
| PagamentoConfirmado | APROVADO | Exatamente um pagamento aprovado, validado e registrado pelo fluxo de consulta/webhook. Inclui pagamentoId, valor e moeda. |
| PagamentoEstornado | ESTORNADO | GET autenticado confirmou estorno integral. Inclui pagamentoId, valor estornado e moeda. |
| FalhaPagamento | FALHA | Erro definitivo reconhecido. Inclui erro.codigo e erro.mensagem segura. |

O consumidor do OS precisa ignorar CheckoutCriado para fins de avanço da Saga,
guardar/expor o link para o cliente e tratar ESTORNADO como compensação, não
aprovação. Estes tipos/status precisam ser aceitos no switch do OS antes do deploy.
Não são publicados dados de cartão nem mensagens de exceção do provedor.

messageId é derivado de comandoId + tipo do evento. Retries/republicações usam
o mesmo evento persistido. causationId aponta ao messageId original do comando,
não ao identificador interno de transporte da AWS.
A SQS Standard não garante ordem: aprovação e estorno podem chegar fora de ordem.
O OS deve tratar duplicatas e não reabrir uma Saga compensada por evento antigo.

## Estorno e segurança

O adapter usa POST /v1/payments/{id}/refunds, corpo {}, X-Idempotency-Key
igual ao comandoId. Sem amount, o provedor solicita estorno integral.
Referência: [Mercado Pago — Create refund](https://www.mercadopago.com.br/developers/en/reference/online-payments/checkout-pro-preferences/create-refund/post).

Antes do POST, valida o pagamento registrado e consulta a operação novamente:
- já integralmente estornado: conclui sem novo POST;
- parcialmente estornado: FALHA/ESTORNO_PARCIAL_NAO_SUPORTADO para análise;
- aprovado: solicita o estorno e consulta novamente;
- pendente/não confirmado/timeout: resultado INCERTO, não FALHA nem ESTORNADO.

Após INCERTO, o reenvio só faz GET para reconciliar. Nunca repete POST cegamente.
Um crash em PROCESSANDO também fica bloqueado para análise; não há expiração de
reserva que permita cobrar/estornar novamente. Outbox e estado do comando são
concluídos em uma transação curta. A chamada ao provedor fica fora da transação.

Se uma consulta posterior confirmar estorno, o mesmo comando INCERTO pode ser
reentregue para concluir. Se continuar aprovado, não usar outro comando para
forçar novo POST: conferir a operação com o responsável e reconciliar primeiro.
Autorização da aprovação, validade de versões e decisão de compensar são do OS.
Billing confere OS/cliente do orçamento, versão/Saga do checkout persistido,
referência externa, moeda e total do pagamento.

## Banco e entrega

Duas tabelas adicionais: comandos_financeiros e eventos_financeiros.
Não há DROP/limpeza nem modificação de dados anteriores nesta alteração.
O padrão atual do projeto (Hibernate update) cria as tabelas ao subir; coordenar
a migração/backup com Pedro antes de rodar em banco compartilhado.

Comando só sai da fila depois de reservar/processar e salvar o resultado.
O evento só é marcado enviado após SendMessage bem-sucedido. Se o processo cair
entre envio e marcação, pode haver duplicata com o MESMO messageId.
Pendências na outbox não dependem da mensagem original continuar na fila.
Confirmação APROVADO vem dos registros atualizados por consulta/webhook existentes.
Com mais de um pagamento aprovado no orçamento, não escolhe um arbitrariamente.

## Habilitação — desligado por padrão

Somente após combinar ambiente com Pedro e contrato com Souza:

- BILLING_SQS_ENABLED=true
- SQS_PROCESSAR_PAGAMENTO_URL=URL da fila processar-pagamento
- SQS_PAGAMENTO_CONFIRMADO_URL=URL da fila pagamento-confirmado
- AWS_REGION=us-east-1 e credenciais temporárias no ambiente/role (sem chaves no código)
- MERCADOPAGO_ENABLED=true e MERCADOPAGO_ACCESS_TOKEN da aplicação de TESTE
- Datasource PostgreSQL do Billing configurado.
- Webhook configurado/habilitado para o pagamento ser verificado automaticamente;
  alternativamente, a verificação local existente registra o resultado em teste.

Permissões: ReceiveMessage/DeleteMessage na entrada e SendMessage na saída.
A infra deve configurar DLQ/redrive e retenção. Worker pede visibilidade de 120s,
lê uma mensagem por vez e não registra corpo/token em logs. Sem essas variáveis,
não há worker financeiro nem chamadas SQS automáticas. Não alteramos infra ou deploy.

## Teste offline

Maven clean verify usa H2, mocks SQS e servidor HTTP em 127.0.0.1.
Não usar token real nem provocar um estorno real para validar o código.
O teste integrado AWS + Mercado Pago sandbox + OS ainda precisa ser feito
em conjunto, após o consumidor do OS aceitar o contrato.
