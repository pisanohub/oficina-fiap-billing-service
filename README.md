# Billing Service

Microsserviço de orçamento/pagamento da Oficina FIAP — Tech Challenge Fase 4.

## Decisão atual: ADR-P4 — preço vem no comando do OS

O **OS captura o preço ao adicionar o item à OS** e envia esse snapshot em `GerarOrcamento`.
O Billing valida, calcula e salva com esses valores, **sem consultar o Execução**.
Alteração posterior do catálogo não muda o snapshot recebido.

Referência: [ADR-P4 do grupo](https://github.com/pisanohub/oficina_fiap/blob/main/documentacao/decisoes-fase4/ADR-P4-saga-orquestrada.md).
O caminho REST de cotação do PR #6 foi retirado. Seu teste permanece como histórico,
não como orientação arquitetural atual. Pagamento e webhook são reaproveitados.

## Guias para o grupo

- [Fluxo de negócio e integração](docs/fluxo-de-negocio.md): responsabilidades e pendências.
- [Snapshot de preços do OS — ADR-P4](docs/snapshot-precos-os.md): entrada, teste, repetição e compatibilidade.
- [Collection de snapshot](docs/billing-snapshot-os.postman_collection.json).
- [Checkout Pro](docs/mercado-pago-checkout-pro.md), [confirmação de pagamento](docs/confirmacao-pagamento.md) e [webhook](docs/webhook-mercado-pago.md).
- [Collection original de orçamento/checkout/pagamento](docs/billing-local.postman_collection.json): continua compatível.
- [Histórico da cotação REST retirada](docs/cotacao-execucao.md): não usar como instrução ativa.

## O que está implementado

- Orçamento com nomes/preços recebidos no comando interno; total exato em BRL e persistência PostgreSQL.
- Snapshot imutável; comando compatível repetido devolve o mesmo orçamento; conteúdo diferente com a mesma chave dá 409.
- Geração/consulta local sem Execução, DynamoDB, SQS ou AWS.
- Preferência de Checkout Pro, consulta de pagamento e persistência financeira validada.
- Webhook HMAC, inbox persistente, worker, recuperação/retries limitados e idempotência.

**Ainda não há entrada/saída SQS do Billing, aprovação/revisão completa, solicitação de
reembolso ou Saga fim a fim validada.** O comando Java interno não define unilateralmente
o JSON compartilhado: envelope, IDs, correlação e versões dependem de acordo com Souza.

Receber preço por um canal interno confiável do OS é diferente de aceitar preços de uma
API pública. O adapter definitivo deve autenticar/autorizar a origem; a entrada local
serve somente para simular o comando, sem JWT.

## Rodar localmente

Pré-requisitos: Java 21 e Docker Desktop com motor Linux. Na pasta do Billing:

```powershell
docker compose up -d --wait billing-db
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

No IntelliJ: Java 21 e **Active profiles: `local`**. Alternativamente, pode usar
`--spring.profiles.active=local` em Program arguments, sem duplicar a configuração.

Billing: `127.0.0.1:8082`. PostgreSQL: `127.0.0.1:5543`, database `billing_service`,
usuário/senha de desenvolvimento `billing_local`. Variáveis `SPRING_DATASOURCE_*`
sobrescrevem esses padrões. Health: `http://127.0.0.1:8082/actuator/health`;
Swagger local: `http://127.0.0.1:8082/swagger-ui.html`.

**Não habilitar `local` no Kubernetes nem expor 8082 em túnel.** As rotas `/internal`
não autenticam o OS e não são o contrato público/de mensageria de produção.

## Gerar com snapshot — exemplo local, não envelope SQS

```http
POST http://127.0.0.1:8082/internal/orcamentos
Content-Type: application/json
```

```json
{
  "comandoId":"44444444-4444-4444-8444-444444444444",
  "ordemId":123,
  "clienteId":1,
  "itens":[
    {"referencia":"filtro-oleo","tipo":"PECA","descricao":"Filtro de óleo","quantidade":2,"valorUnitario":45.50},
    {"referencia":"troca-oleo","tipo":"SERVICO","descricao":"Troca de óleo","quantidade":1,"valorUnitario":95.00}
  ]
}
```

O Postman simula o snapshot do OS: esperado **186.00 BRL**, UUID `id`, itens e status
`AGUARDANDO_APROVACAO`. Consulte com `GET /internal/orcamentos/{id}`. O preço vem do payload;
o OS definitivo deve usar o preço capturado na adição do item, não um preço arbitrário do cliente.

**Migração do teste anterior:** usar `/internal/orcamentos`, não `/internal/orcamentos/cotados`.
Adapter, porta, configuração e rota de cotação foram removidos. A collection antiga foi retirada.
`EXECUCAO_BASE_URL` / `EXECUCAO_COTACAO_*` não são mais utilizados; remova-os do IntelliJ.
Não precisa manter Execução/DynamoDB ligados para gerar orçamento. Dados anteriores não foram apagados.

## Mercado Pago e webhook

Para preferência/consulta financeira: `MERCADOPAGO_ENABLED=true` e `MERCADOPAGO_ACCESS_TOKEN`
do ambiente correspondente. Para webhook: `MERCADOPAGO_WEBHOOK_ENABLED=true` e
`MERCADOPAGO_WEBHOOK_SECRET`, além da integração financeira configurada.
Integrações vêm desligadas por padrão; não precisa ligá-las para gerar orçamento.

Preferência `CRIADA` não é aprovação financeira. O Billing valida operação, referência
externa, BRL e total antes de registrar o pagamento. Consultar não cobra/reembolsa.
Pagamento aprovado não altera a OS ou aprova o orçamento automaticamente neste incremento.

Para demonstrar webhook por HTTPS, usar somente `webhook-demo` na porta 8083, sem `local`.
**200 do webhook significa aviso salvo, não pagamento aprovado.** Não divulgar tokens,
segredo do webhook, credenciais reais ou prints completos de headers.

## Arquitetura, testes e pipeline

Camadas: domínio, casos de uso, portas, adapters e configuração. A geração depende apenas
do repositório próprio e do relógio. WebClient continua nas integrações financeiras,
não na geração de orçamento. Java 21, Spring Boot, JPA/PostgreSQL, Maven, JUnit e JaCoCo.
AWS SDK SQS e Resilience4j são dependências disponíveis, não prova de consumidores/circuit breaker implementados.

```powershell
.\mvnw.cmd clean verify
```

Testes usam H2 e HTTP simulado. JaCoCo exige 80% de linhas; relatório em
`target/site/jacoco/index.html`. Resultado ADR-P4 no guia de snapshot. Os 181 testes
históricos incluíam cotação removida, não representam a suíte atual.

CI/Sonar e deploy dos colegas não foram alterados nesta correção. **Merge em main pode
disparar deploy**: coordenar com Pedro. Workflow existente não comprova integração fim a fim.

## Pendências com a equipe

- Souza: payload/envelope, IDs/correlação, autorização do canal, aprovação/revisão e validade de versões antigas.
- OS/Execução: confirmar captura de preços no OS e atualizar referências antigas de cotação direta nos seus documentos.
- Pedro: filas/permissões, banco, variáveis/secrets, migrações e validação do ambiente.
- Billing: entrada/saída SQS após contrato, reembolso/reconciliação e testes integrados da Saga.

Para encerrar o teste local, pare o aplicativo e use `docker compose stop billing-db`.
Não use `down -v` para preservar dados.
