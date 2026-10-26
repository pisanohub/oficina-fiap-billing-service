# Billing Service

Microsserviço de cobrança da Fase 4 (Tech Challenge — Arquitetura de Software, FIAP).

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

Pré-requisitos: Java 21 e uma instância PostgreSQL acessível (ou ajuste as variáveis
de ambiente abaixo para apontar para uma).

```bash
./mvnw clean verify      # Linux/macOS
mvnw.cmd clean verify    # Windows
```

Para subir a aplicação:

```bash
./mvnw spring-boot:run
```

Variáveis de ambiente (todas com valor padrão para desenvolvimento local):

| Variável | Padrão |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/billing_service` |
| `SPRING_DATASOURCE_USERNAME` | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | `postgres` |
| `MERCADOPAGO_ACCESS_TOKEN` | *(vazio)* |

Com a aplicação no ar, a documentação Swagger fica disponível em
`http://localhost:8080/swagger-ui.html`, e o health check do Actuator em
`http://localhost:8080/actuator/health`.

## Status

**Este é um esqueleto inicial do serviço**, ainda sem lógica de negócio, entidades de
domínio ou endpoints reais. Serve como ponto de partida para a implementação, que
segue o plano descrito em `documentacao/divisao-tarefas-fase4.md` (repositório
principal). Em especial, ainda faltam:

- Entidades de domínio (`Orcamento`, `Pagamento`) e os endpoints de geração/consulta
  de orçamento e de pagamento.
- Integração real com o **Mercado Pago (sandbox)** — hoje existe apenas a dependência
  do WebClient e a propriedade `mercadopago.access-token` configurada via variável de
  ambiente, sem nenhuma chamada implementada ainda.
- Publicação/consumo das filas SQS da Saga (comandos `GerarOrcamento` /
  `ProcessarPagamento`, eventos `OrcamentoGerado` / `PagamentoConfirmado` /
  `PagamentoRecusado` / `PagamentoEstornado`).
- A contramedida de **Pessimistic View / Reread Value** na etapa de orçamento/pagamento
  (ver nota da P7 em `documentacao/event-storming-saga-v1.md` do repositório principal),
  para evitar condição de corrida entre a aprovação do cliente e um reprocessamento
  concorrente do mesmo orçamento.
- Testes unitários e cobertura mínima de 80% (JaCoCo já configurado no `pom.xml`, sem
  meta de cobertura ainda).
- Deploy automatizado no pipeline (`.github/workflows/ci.yml` hoje só builda e testa).
