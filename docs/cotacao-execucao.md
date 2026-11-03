# Cotação no Execução e orçamento com preços congelados

## Objetivo e fronteira

Implementar **somente o lado do Billing** do contrato HTTP provisório do Execução:
`POST /api/v1/cotacoes`. Nenhum arquivo do OS/Execução, fila, Terraform ou pipeline foi alterado.

Antes, o POST local de orçamento recebia preço e descrição no corpo. Ele continua existindo
para compatibilidade com os testes anteriores, mas **não é uma entrada confiável de produção**.
Agora existe uma segunda entrada local que recebe apenas referências, tipos e quantidades;
nome e preço vêm exclusivamente da resposta do Execução.

Fluxo:

1. O solicitante informa comandoId, OS, cliente, versão do diagnóstico e lista de itens.
2. Se o comando já foi salvo como cotado, o Billing compara os dados do pedido e devolve o
   snapshot original **sem chamar o Execução novamente**, mesmo se o catálogo mudou ou estiver fora do ar.
3. Para um comando novo, chama a cotação e confere BRL, data, lista completa, IDs, tipos,
   quantidades e valores. Ausência, duplicação, troca ou inclusão de item invalida a resposta.
4. Copia nome e preço para `ItemOrcamento`; calcula subtotais e total com BigDecimal, sem arredondamento silencioso.
5. Salva orçamento e metadados em uma transação. Não cria preferência nem faz cobrança.

## Contrato consumido

Pedido ao Execução (não recebe dados pessoais, IDs do cliente ou preços):

```http
POST /api/v1/cotacoes
Content-Type: application/json
```

```json
{
  "itens": [
    {"tipo":"PECA","itemId":"filtro-oleo","quantidade":2},
    {"tipo":"SERVICO","itemId":"troca-oleo","quantidade":1}
  ]
}
```

Resposta de exemplo, dependente dos valores cadastrados:

```json
{
  "moeda":"BRL",
  "geradaEm":"2026-11-03T12:00:00Z",
  "itens":[
    {"tipo":"PECA","itemId":"filtro-oleo","nome":"Filtro de óleo","quantidade":2,"precoUnitario":45.50},
    {"tipo":"SERVICO","itemId":"troca-oleo","nome":"Troca de óleo","quantidade":1,"precoUnitario":95.00}
  ]
}
```

Neste exemplo o total é **186.00 BRL**. Não confundir com o orçamento anterior de 199.80:
aquele continua preservado e os novos valores vêm do catálogo que estiver em execução.
Cotação **não reserva estoque**; a reserva é responsabilidade do agendamento pelo OS/Execução.

## Configuração

| Variável | Valor |
|---|---|
| `EXECUCAO_COTACAO_ENABLED` | `true` para habilitar chamadas novas; padrão `false` |
| `EXECUCAO_BASE_URL` | URL raiz do serviço, sem `/api`, sem usuário/senha, query ou fragmento |
| `EXECUCAO_COTACAO_TIMEOUT` | Opcional; padrão `5s`, positivo e no máximo `30s` |

Exemplo local: `http://127.0.0.1:8080` **se** o Execução estiver acessível nessa porta.
No cluster, conforme documento do colega: `http://execucao-service.oficina-ms:8080`.
O nome interno do cluster não resolve automaticamente no seu Windows: para teste local,
combine a execução local ou um encaminhamento autorizado com o responsável pelo serviço.

Não acrescentar `/api/v1/cotacoes` na variável; o adapter monta esse caminho.
Não há autenticação entre os serviços no contrato atual do colega. Precisamos combinar isso
com a equipe antes do deploy definitivo. Não enviar token Mercado Pago para o Execução.
Não há retry HTTP automático e a resposta é limitada a 64 KiB. Apenas a rota de cotação é chamada.

## Testar no IntelliJ e Postman

1. Deixe o Docker Desktop aberto. No Billing:

```powershell
cd C:\CODEX\oficina-fiap-billing-service
docker compose up -d --wait billing-db
```

2. Combine com o colega a disponibilidade do **Execução** e dos itens de teste no catálogo.
   Use a collection dele para conferir `Cotar preços (Billing)` antes de integrar. Não é
   necessário rodar comandos de agendamento/cancelamento para testar esta etapa.
3. No IntelliJ, use Java 21 e **Active profiles: `local`**. Adicione as variáveis da tabela.
   Mantenha webhook desligado se não estiver testando webhook. Mercado Pago não é necessário.
   Não use `webhook-demo` para essa rota e **não abra túnel para a porta 8082**.
4. Importe `docs/billing-cotacao.postman_collection.json` no Postman. Confira `baseUrl` resolvendo
   para `http://127.0.0.1:8082` (variável de ambiente ativa pode sobrescrever a da collection).
5. Configure `ordemId`, `clienteId`, `versaoDiagnostico` e os IDs/quantidades iguais ao diagnóstico.
   Em teste isolado, use dados fictícios conhecidos e um comando UUID novo. Use os mesmos IDs
   do catálogo, não o ID do pagamento nem UUID do orçamento.
6. Execute **1 - Gerar orçamento com cotação**. O POST não exige token ou preço:

```http
POST http://127.0.0.1:8082/internal/orcamentos/cotados
```

```json
{
  "comandoId":"22222222-2222-4222-8222-222222222222",
  "ordemId":123,
  "clienteId":1,
  "versaoDiagnostico":2,
  "itens":[
    {"tipo":"PECA","itemId":"filtro-oleo","quantidade":2},
    {"tipo":"SERVICO","itemId":"troca-oleo","quantidade":1}
  ]
}
```

7. Esperado: 200 com `id`, `comandoId`, `versaoDiagnostico`, `cotacaoGeradaEm`, itens com
   `referencia`, descrição e `valorUnitario`, total e status `AGUARDANDO_APROVACAO`.
   `itemId` do Execução fica em `referencia` no modelo atual do Billing, sem trocar seu valor.
8. Execute **2 - Consultar orçamento salvo** e repita a requisição 1 com os mesmos dados.
   O ID e os preços devem permanecer iguais. A collection guarda uma cópia para comparar
   repetições; para outro cenário, gere outro `cotacaoComandoId` e limpe `cotadoSnapshot` e `cotadoId`.
9. Opcional: o responsável pelo catálogo altera o preço de um item de teste no ambiente local.
   Repetir o comando antigo continua devolvendo o preço salvo; um **novo comandoId** consulta
   o preço novo e gera outro orçamento. Não alterar catálogo compartilhado sem combinar.
10. **3 - Conflito de quantidade** reenvia o mesmo comando alterando uma quantidade e espera 409.

Campos extras de preço/descrição, se enviados, não são utilizados para calcular: a resposta
do catálogo é a única fonte de nomes e valores. Não dependa de campos extras; eles não fazem
parte desta entrada provisória. Não use comandos já usados na entrada antiga de preços manuais.
O `itemId` deve ser preservado exatamente e respeitar o limite atual do Billing de 80 caracteres.

## Conferir no DBeaver

Mesma conexão local: 127.0.0.1:5543, banco `billing_service`, usuário/senha `billing_local`.
Consulta somente de leitura:

```sql
SELECT o.id, o.comando_id, o.ordem_id, o.cliente_id, o.total,
       c.versao_diagnostico, c.cotacao_gerada_em
FROM public.orcamentos o
JOIN public.orcamento_cotacoes c ON c.orcamento_id = o.id
ORDER BY o.criado_em DESC;
```

Os preços e quantidades ficam em `orcamento_itens`. Orçamentos antigos não aparecem nesse
JOIN, pois não possuem metadados de origem de cotação. Eles continuam consultáveis normalmente.

## Erros

| HTTP Billing | Significado |
|---|---|
| 400 | Pedido inválido: UUID, IDs/versão não positivos, lista vazia/>20, item duplicado ou quantidade inválida |
| 409 | Mesmo comando com outro cliente/OS/versão/item/quantidade, ou comando de orçamento manual usado como cotado |
| 422 | Execução respondeu 400 à cotação; conferir itens existentes/ativos e contrato do pedido |
| 503 | Integração desligada ou URL/timeout inválidos |
| 502 | Falha de comunicação, resposta malformada, moeda/itens/quantidades/preços divergentes ou outro status inesperado |
| 504 | Timeout; não foi salvo orçamento novo por aquela tentativa |

Os erros de integração são sanitizados: não repassamos corpo bruto nem causa HTTP do outro serviço.
Em falha de persistência, a transação é revertida e não deixamos orçamento sem seus metadados.
Um comando repetido pode retornar 200 mesmo com a integração desligada: não precisa recotar o snapshot já salvo.

## Idempotência, concorrência e versão

- A chave continua sendo `comandoId` UUID. Dados iguais podem vir em outra ordem de itens.
- Não recotar um comando já concluído. Um mesmo comando com conteúdo diferente gera conflito.
- Na corrida entre consultas novas com preços diferentes, a primeira transação confirmada vence;
  a perdedora relê o orçamento salvo e devolve o mesmo ID e snapshot se o pedido lógico coincide.
- Identificamos origem com `orcamento_cotacoes`; não atribuímos retroativamente confiança aos
  preços enviados na rota manual antiga. Orçamento e metadados são gravados atomicamente.
- `versaoDiagnostico` é a revisão recebida do solicitante, guardada como contexto do snapshot.
  **Não é `schemaVersion` da mensagem nem `versao` do orçamento aprovado.**
- Não fazemos GET do diagnóstico, nem comprovamos que a versão informada ainda é atual.
  A leitura/revalidação do diagnóstico e o contrato que o OS enviará precisam ser combinados.
- Novo comando com diagnóstico alterado gera outro orçamento imutável, mas não implementa
  revisão automática, substituição/cancelamento de orçamento anterior ou bloqueio de pagamento
  de orçamento desatualizado. Essas políticas permanecem pendentes do alinhamento da Saga.

## Pontos a alinhar com Souza/Pedro

1. Envelope de `GerarOrcamento` e retorno de `OrcamentoGerado`, com `sagaId`, `messageId`,
   `operationId`, IDs, itens e versões. Este comando Java/JSON local **não é** o contrato SQS.
2. O Execução usa `osId` texto; o domínio atual do Billing usa OS/cliente numéricos positivos.
   Não fazemos conversão silenciosa de IDs alfanuméricos neste incremento.
3. Como o OS receberá os itens e a versão aprovada; os IDs/tipos/quantidades devem ser preservados.
4. Releitura do diagnóstico, versionamento do orçamento, aprovação e validade de versões antigas.
5. Endpoint acessível, autenticação de serviço e variáveis no deploy, a cargo de quem mantém a infraestrutura.

O pipeline e os manifestos do Pedro não foram alterados. Como a integração é desligada por
padrão e não há consumidor de geração da Saga aqui, **não basta mesclar este PR para integrar
o fluxo completo em Kubernetes**. É um incremento do Billing com teste local e portas prontas
para a futura entrada compartilhada. Nenhuma fila ou recurso AWS é criado.

## Testes

`.\mvnw.cmd clean verify` em Java 21. Inclui HTTP local simulado **com adapter real** e
persistência H2, validação da resposta, snapshot, erro sanitizado, concorrência com preços
diferentes, rollback de metadados e preservação das rotas de pagamento/webhook existentes.
Não prova PostgreSQL real nem o serviço do colega: a integração manual continua pendente.

Resultado registrado em **03/11/2026**: Java 21, `clean verify`, **181 testes aprovados**,
sem falhas/erros/ignorados, cobertura de linhas **98,44% (693/704)** e gate de 80% aprovado.
JSON e sintaxe dos scripts Postman também validados. Nenhuma cobrança, consulta à conta
Mercado Pago, implantação ou mudança na infraestrutura dos colegas foi realizada.

## Referência do colega

[Guia de integração do Execução, seção 8](https://github.com/pisanohub/oficina-fiap-execucao-service/blob/main/docs/contrato-provisorio-saga.md#8-http-para-os-service-e-billing).
