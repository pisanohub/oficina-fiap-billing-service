# Billing Service

Microsserviço de cobrança da Fase 4 (Tech Challenge — Arquitetura de Software, FIAP).

## Primeira etapa: orçamento local

Já é possível gerar, calcular e consultar orçamento no banco do Billing, com validação
de itens e proteção contra repetição de comando. Os preços ficam registrados como snapshot.
O status inicial é `AGUARDANDO_APROVACAO`; ainda não há cobrança nem aprovação implementadas.

## Segunda etapa: Checkout Pro / Preferences API

É possível criar uma preferência a partir do orçamento salvo, persistir o ID/link e
reutilizar o resultado sem novo envio. Geração de checkout **não confirma pagamento**.
Os POST/GET `/internal/orcamentos/{id}/preferencia` só existem no perfil `local`.

Passo a passo completo: [Integração Mercado Pago](docs/mercado-pago-checkout-pro.md).

Na configuração do IntelliJ, mantenha Java 21, `--spring.profiles.active=local` e adicione
as variáveis de ambiente `MERCADOPAGO_ENABLED=true` e `MERCADOPAGO_ACCESS_TOKEN` com
o token do ambiente **Teste**. O token não deve ser colocado no Postman nem no código.
A habilitação é falsa por padrão; o orçamento local segue funcionando sem credencial.

Reimporte a collection e execute **4 - Criar preferência** e **5 - Consultar estado**.
O POST não recebe preço no corpo. Um resultado incerto bloqueia novas tentativas até
reconciliação; não há retry automático nem confirmação/webhook de pagamento nesta etapa.

Estrutura em camadas: `domain/model`, `application/usecase`, `application/port/out`,
`adapter/out/persistence`, `adapter/in/web` e `config`.
Detalhes e contratos pendentes: [Primeira etapa](docs/primeira-etapa-orcamentos.md).

## Responsabilidade

Geração e envio de orçamentos para aprovação, registro e verificação de pagamentos
integrados ao Mercado Pago, e aviso ao OS Service após o pagamento ser processado.

Faz parte do fluxo da Saga orquestrada pelo OS Service (abertura da OS → geração de
orçamento → aprovação → pagamento → execução). Ver `documentacao/divisao-tarefas-fase4.md`
e `documentacao/event-storming-saga-v1.md` no repositório principal (`oficina_fiap`)
para o contexto arquitetural completo e o diagrama de sequência da Saga.

## Tecnologias

- Java 21
- Spring Boot 4.0.5
  - Spring Web
  - Spring WebFlux (`WebClient`, para a futura integração com o Mercado Pago)
  - Spring Data JPA
  - Spring Validation
  - Spring Actuator
- PostgreSQL (driver `org.postgresql:postgresql`)
- Lombok
- springdoc-openapi (Swagger UI)
- AWS SDK v2 — Amazon SQS (mensageria da Saga)
- Resilience4j (Circuit Breaker nas chamadas síncronas)
- JaCoCo (cobertura de testes)
- Maven Wrapper

## Como rodar localmente

Pré-requisitos: Java 21. Os testes usam H2 e não precisam de banco externo nem AWS.
Para testar manualmente, use Docker Desktop com motor Linux ou PostgreSQL local.

```bash
./mvnw clean verify      # Linux/macOS
mvnw.cmd clean verify    # Windows
```

### Banco local e aplicação (PowerShell)

Com Docker Desktop aberto, execute na pasta do repositório:

```powershell
docker compose up -d --wait billing-db
```

Em outro terminal, na mesma pasta:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

Alternativa após `clean verify`:

```powershell
java -jar .\target\billing-service-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

No IntelliJ, abra o `pom.xml`, selecione JDK 21 e adicione
`--spring.profiles.active=local` em **Program arguments** de `BillingServiceApplication`.

O perfil local usa `127.0.0.1:8082` e PostgreSQL em `127.0.0.1:5543`, database
`billing_service`, usuário/senha `billing_local`. São credenciais fictícias de desenvolvimento.
Variáveis `SPRING_DATASOURCE_*` já existentes sobrescrevem esses valores: confira-as
se o aplicativo tentar acessar outro banco.

Para Linux/macOS:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Variáveis de ambiente (padrões abaixo para execução SEM perfil local):

| Variável | Padrão |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/billing_service` |
| `SPRING_DATASOURCE_USERNAME` | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | `postgres` |
| `MERCADOPAGO_ACCESS_TOKEN` | *(vazio)* |

No perfil local, Swagger: `http://127.0.0.1:8082/swagger-ui.html`;
health: `http://127.0.0.1:8082/actuator/health`.

### Testar pelo Postman

Importe [a collection](docs/billing-local.postman_collection.json) e execute na ordem.
Ela salva o ID retornado para a consulta seguinte. Para testar manualmente:

- **POST** `http://127.0.0.1:8082/internal/orcamentos`
- **Authorization:** No Auth
- **Body → raw → JSON:**

```json
{
  "comandoId": "11111111-1111-4111-8111-111111111111",
  "ordemId": 1,
  "clienteId": 1,
  "itens": [
    {"referencia":"oleo","tipo":"PECA","descricao":"Óleo","quantidade":2,"valorUnitario":49.90},
    {"referencia":"mao-obra","tipo":"SERVICO","descricao":"Troca de óleo","quantidade":1,"valorUnitario":100.00}
  ]
}
```

Esperado: HTTP 200, UUID do orçamento, status `AGUARDANDO_APROVACAO`, moeda `BRL` e
total `199.80`. Consulte com **GET** `/internal/orcamentos/{id}`.
Repetir a criação mantém o mesmo ID. Mesma chave com outro conteúdo retorna 409;
entrada inválida retorna 400; UUID não encontrado retorna 404.
Use outro `comandoId` para uma nova operação.

**A entrada HTTP é temporária e exclusiva do desenvolvimento**: aceita preços do teste
e não possui JWT. Sem perfil `local`, as rotas não existem. Não habilite o perfil local
no Kubernetes. A entrada definitiva depende dos contratos da Saga e de preços confiáveis
obtidos do catálogo da Execução ou de um comando interno confiável.

### Testes, cobertura e encerramento

Execute `.\mvnw.cmd clean verify`. O JaCoCo bloqueia o build abaixo de 80% de linhas
do serviço, sem excluir domínio, casos de uso e adapters. Relatório HTML:
`target/site/jacoco/index.html`; XML: `target/site/jacoco/jacoco.xml`.
Os testes de integração usam H2 em modo PostgreSQL, incluindo concorrência de comandos.
H2 não comprova integralmente PostgreSQL; o Compose permite o teste manual nesse banco.

Evidência desta etapa (27/10/2026): **39 testes aprovados; 98,48% de linhas cobertas**.
Detalhes e limitações: [resultado de validação](docs/primeira-etapa-orcamentos.md#resultado-registrado-em-27102026).

Para encerrar, use `Ctrl+C` no aplicativo e `docker compose stop billing-db`.
Os dados ficam no volume local para a próxima execução.

## Status

**Primeira etapa de orçamento implementada sobre o esqueleto do Pedro.**
A geração/consulta local já funciona. Integrações externas continuam pendentes:

- Aprovação/reprovação, revisões e cancelamento; domínio de pagamento e estorno.
- Confirmação real do pagamento, webhook, reconciliação e reembolso no Mercado Pago.
  A criação da preferência já tem adapter HTTP; a validação na conta de teste é manual.
- Publicação/consumo das filas SQS da Saga (comandos `GerarOrcamento` /
  `ProcessarPagamento`, eventos `OrcamentoGerado` / `PagamentoConfirmado` /
  `PagamentoRecusado` / `PagamentoEstornado`).
- A contramedida de **Pessimistic View / Reread Value** na etapa de orçamento/pagamento
  (ver nota da P7 em `documentacao/event-storming-saga-v1.md` do repositório principal),
  para evitar condição de corrida entre a aprovação do cliente e um reprocessamento
  concorrente do mesmo orçamento.
- Sonar/qualidade no CI, migrações versionadas do schema e observabilidade distribuída,
  a integrar com o padrão da equipe.
- Deploy automatizado no pipeline (`.github/workflows/ci.yml` hoje só builda e testa).

Os arquivos de planejamento citados acima não foram encontrados na `main` do projeto
principal durante esta implementação. Foram consideradas as decisões de 15/10 e os
PDFs enviados pelo Gabriel; confirmar caminho/branch com Pedro. Nenhum recurso AWS
é criado por esta etapa local.
