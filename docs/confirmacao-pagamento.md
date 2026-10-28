# Terceira etapa: verificação manual do pagamento

O Billing agora consulta `GET /v1/payments/{id}` no Mercado Pago e registra um snapshot
financeiro validado. É um GET remoto: esta operação não cobra nem reembolsa dinheiro.
O POST local serve para atualizar o registro no banco do Billing.

## O que acontece, passo a passo

1. Recebe o UUID do orçamento e o ID numérico da operação; não recebe valor nem status.
2. Confere que existe orçamento e preferência CRIADA registrada localmente.
3. Consulta o provedor usando o Access Token já configurado no backend.
4. Exige que o ID da resposta seja a operação solicitada, a external_reference seja
   o UUID do orçamento, a moeda seja BRL e transaction_amount seja exatamente o total salvo.
5. Valida data de atualização e valor já reembolsado. Campo financeiro obrigatório ausente
   ou resposta inválida não cria confirmação.
6. Grava/atualiza `pagamentos`, cuja chave primária é o identificador externo da operação.
7. Devolve estado financeiro e `confirmado`, sem dados de cartão, e-mail ou CPF.

Não usa o print de aprovação nem os parâmetros de retorno do navegador como prova financeira.
O provedor pode retornar pendente ou recusado: a resposta HTTP local é 200 se a consulta
e validação funcionaram; é necessário olhar estado/confirmado.

## Como testar no IntelliJ e Postman

1. Pare a execução antiga, mantenha Java 21 e **Active profiles: local**.
2. Mantenha MERCADOPAGO_ENABLED=true e MERCADOPAGO_ACCESS_TOKEN da aplicação no ambiente Teste.
   Não precisa gerar outra credencial nem informar senha buyer/seller no backend.
3. Inicie a aplicação com PostgreSQL local disponível. O schema local update acrescenta
   a tabela pagamentos; dados anteriores permanecem.
4. Reimporte `docs/billing-local.postman_collection.json`.
5. Nas variáveis da collection, mantenha `baseUrl=http://127.0.0.1:8082`, preencha
   `orcamentoId` com o orçamento que originou o checkout e `pagamentoId` com o número
   da operação aprovado no checkout.
6. Execute **6 - Verificar na API e registrar pagamento**, sem corpo e sem token no Postman:

```text
POST /internal/orcamentos/{orcamentoId}/pagamentos/{pagamentoId}/verificar
```

Para o pagamento aprovado de 199,80, o esperado é HTTP 200 com `estado=APROVADO`,
`confirmado=true`, `valor=199.80`, `moeda=BRL` e o identificador externo informado.
O teste da collection verifica a estrutura, não presume que todo pagamento foi aprovado.

7. Execute **7 - Listar pagamentos registrados do orçamento**. Repita a requisição 6
   e volte à 7: o mesmo paymentId deve continuar com um único registro.

```text
GET /internal/orcamentos/{orcamentoId}/pagamentos
```

O GET consulta somente o banco local; o POST consulta novamente o provedor para obter
mudanças como aprovação posterior ou reembolso. Não repita a compra para testar duplicação
da consulta: consulte a mesma operação já paga.

## Estados

| Estado no provedor | Estado local / confirmação |
|---|---|
| approved, sem reembolso | APROVADO / true |
| approved, com reembolso parcial ou detalhe partially_refunded | PARCIALMENTE_ESTORNADO / false |
| refunded ou valor integral já reembolsado em approved | ESTORNADO / false |
| pending | PENDENTE / false |
| in_process | EM_PROCESSAMENTO / false |
| authorized | AUTORIZADO / false; autorização ainda não é captura/aprovação final |
| rejected | RECUSADO / false |
| cancelled | CANCELADO / false |
| charged_back / in_mediation | CONTESTADO / EM_MEDIACAO / false |
| Código novo/desconhecido | DESCONHECIDO / false |

Detectar estorno na consulta não implementa a operação de reembolso da Saga.

## Consistência e repetição

- A mesma operação não cria duas linhas. A consulta pode ser repetida.
- Lock curto na linha do orçamento serializa gravações concorrentes; não há lock durante HTTP.
- ID, vínculo, valor original e moeda de uma operação não podem ser trocados.
- Snapshot com date_last_updated anterior é ignorado em favor do registro mais recente.
- Mesma data do provedor com dados divergentes retorna 409 e requer reconciliação.
- Resposta mais recente atualiza o estado, inclusive remoção da confirmação após estorno.
- Falha de consulta não converte pagamento em recusado e não remove um registro anterior.

Um orçamento pode ter diferentes tentativas externas. O sistema lista cada paymentId
separadamente; ainda não resolve pagamentos múltiplos, soma de parcelas, aprovação
de orçamento, escolha da tentativa válida para a Saga ou compensação de duplicidade financeira.

## Erros

| HTTP | Significado |
|---|---|
| 400 | Identificadores inválidos. |
| 404 | Orçamento ou pagamento não encontrado/acessível para a credencial. |
| 409 | Falta preferência criada, referência/valor/moeda incompatível, operação de outro orçamento ou snapshot conflitante. |
| 503 | Integração desabilitada ou token ausente. |
| 502 | Provedor recusou consulta, retornou outra operação ou resposta incompleta/inválida. |
| 504 | Timeout de consulta; é seguro consultar novamente, pois não há criação de cobrança neste endpoint. |

## Limites desta entrega

Rotas disponíveis só no perfil local e em loopback. Ainda não há JWT/autorização de
cliente nessas rotas temporárias; não habilitar esse perfil no Kubernetes.
O registro financeiro é separado do status da OS/orçamento: o orçamento continua
AGUARDANDO_APROVACAO enquanto o contrato de aprovação e avanço da Saga não for implementado.
Nenhum evento PagamentoConfirmado é publicado nesta etapa.

O token deve continuar sendo do ambiente Teste selecionado no painel. O programa não
infere ambiente pelo prefixo nem faz cobrança automática. Ficam para os próximos
incrementos webhook autenticado, reconciliação, reembolso e SQS/outbox.

## Referências

Validação em 28/10/2026: `clean verify` com 110 testes aprovados, nenhum erro/falha/ignorado,
418 de 427 linhas cobertas (97,89%) e limite mínimo de 80% atendido. Os testes usam API
HTTP local com credencial fictícia e H2 em modo PostgreSQL. O motor Docker não estava
disponível nesta execução; a tabela/lock de pagamentos ainda não foi validada contra
PostgreSQL real. Não foi feita consulta à conta real do usuário durante a implementação.

Esta branch parte de `feat/mercado-pago-preferencia`, porque a criação da preferência
ainda não constava da main na conferência inicial. Integrar a preferência primeiro ou
revisar a confirmação com a branch anterior como base para separar as entregas.

- https://www.mercadopago.com.br/developers/pt/reference/online-payments/checkout-pro-preferences/get-payment/get
- https://www.mercadopago.com.br/developers/pt/docs/checkout-api-payments/response-handling/query-results
