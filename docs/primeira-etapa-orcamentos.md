# Primeira etapa: orçamento local

Fluxo: entrada local → caso de uso → domínio → banco próprio do Billing.
Não precisa de AWS, Mercado Pago nem acesso aos bancos dos colegas.

| Camada | Local | Responsabilidade |
|---|---|---|
| Domínio | `domain/model` | Validar e calcular com BigDecimal; guardar snapshot dos preços. |
| Aplicação | `application/usecase` | Gerar/consultar e reconhecer comando repetido. |
| Porta de saída | `application/port/out` | Contrato de persistência independente de JPA. |
| Adapter de banco | `adapter/out/persistence` | Tabelas orcamentos/orcamento_itens. |
| Entrada local | `adapter/in/web` | HTTP temporário, somente no perfil local. |
| Configuração | `config` | Conectar componentes e fornecer relógio UTC. |

## Regras desta etapa

- BRL; decimal exato, não negativo, até duas casas sem arredondamento silencioso.
- Cortesia com zero permitida. Limite de persistência numeric(19,2).
- Entre 1 e 100 itens, quantidade inteira positiva; referências/descrições limitadas.
- Não repetir referência + tipo. Consolidar quantidade no mesmo item.
- OS/cliente são referências positivas, sem consultas ao banco do OS.
- Só geração/consulta; status AGUARDANDO_APROVACAO. Não há aprovação/cobrança/estorno.
- Não impõe um orçamento por OS: revisão/substituição precisa de contrato específico.
- comandoId UUID identifica a operação. Mesma chave/conteúdo normalizado devolve original;
  outra carga com mesma chave dá conflito. A ordem dos itens faz parte do conteúdo.
- Restrição única no banco impede INSERT duplicado. Após rollback por corrida, o caso
  de uso relê o orçamento vencedor em nova transação.

Esses detalhes são propostas desta etapa, não novas decisões atribuídas à reunião.

## Contratos ainda pendentes

O grupo decidiu catálogo/estoque na Execução, Saga no OS, SQS e entrada Gateway → OS.
O HTTP local permite experimentar sem declarar um schema SQS definitivo.
Ele aceita preços de teste e não possui JWT; não habilitar perfil local no Kubernetes.
O bind é 127.0.0.1:8082; o teste verifica ausência das rotas sem esse perfil.

1. Souza: payloads, correlação, comandoId/operationId, aprovação e revisão de orçamento.
2. Guilherme: preços confiáveis via consulta REST ou snapshot confiável no comando.
3. Pedro: banco/usuário Billing, filas/permissões, migrações e padrão de qualidade/deploy.
4. Grupo: modalidade Mercado Pago, confirmação, pendência, reembolso e idempotência financeira.

SQS/outbox virão depois; idempotência da geração não garante efeitos financeiros nem entrega de eventos.
O ddl-auto update da base foi mantido; combinar migrações versionadas antes de usar dados reais.

## Evidência e limitações

Testes HTTP + JPA usam H2 em modo PostgreSQL e exercitam corrida de quatro comandos iguais.
H2 não comprova integralmente PostgreSQL. O Compose fornece PostgreSQL para validação manual.
JaCoCo exige 80% de linhas do serviço, sem excluir domínio, casos de uso ou adapters.
Springdoc foi ajustado de 2.8.8 para 3.0.3, da linha compatível com Boot 4.0.x;
há teste de /v3/api-docs. Referência: https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot

Os arquivos documentacao/divisao-tarefas-fase4.md e documentacao/event-storming-saga-v1.md,
citados na base, não foram encontrados em pisanohub/oficina_fiap/main nesta implementação.
Foram usadas as decisões de 15/10 e PDFs fornecidos pelo Gabriel. Confirmar caminho/branch com Pedro.

## Resultado registrado em 27/10/2026

- Java 21, `clean verify`: 39 testes, zero falhas/erros e nenhum teste ignorado.
- JaCoCo: 130 linhas cobertas de 132 (98,48%); limite mínimo de 80% aprovado.
- JAR gerado: `target/billing-service-0.0.1-SNAPSHOT.jar`.
- Relatórios completos são regenerados em `target/surefire-reports` e `target/site/jacoco`.
- Docker Desktop foi iniciado, mas o motor Linux não estava acessível na verificação.
  Não foi executado teste contra PostgreSQL real nem deploy na AWS nesta etapa.

Os números representam esta revisão e podem mudar com os próximos incrementos.
