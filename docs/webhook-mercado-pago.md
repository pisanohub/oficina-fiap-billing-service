# Webhook Mercado Pago — quarta etapa do Billing

## O que mudou e por quê

Antes, o teste 6 do Postman iniciava a consulta manual do pagamento. Agora um aviso
assinado do Mercado Pago pode iniciar a mesma verificação automaticamente.
Não há cobrança nova, estorno, alteração da OS nem publicação SQS neste incremento.

Fluxo implementado:

```text
Mercado Pago -> POST /webhooks/mercadopago?data.id=ID
             -> valida HMAC e horário -> grava inbox -> HTTP 200
Worker      -> reserva aviso salvo -> GET /v1/payments/ID
             -> valida orçamento / preferência / valor / BRL
             -> registra ou atualiza pagamento -> conclui aviso
```

**HTTP 200 significa recebimento durável do aviso, não pagamento aprovado.**
Somente a consulta autenticada ao provedor e as validações financeiras podem confirmar
o pagamento. Nunca usamos `status`, preço ou UUID fornecidos no corpo do webhook.

## Segurança e contrato HTTP

- Rota desabilitada por padrão. Habilitação explícita exige também integração e segredo válidos.
- `POST /webhooks/mercadopago?data.id=<ID numérico positivo>` com `Content-Type: application/json`.
- Headers únicos `x-request-id` e `x-signature=ts=...,v1=...`; query `data.id` também única.
- JSON mínimo: `{"type":"payment","data":{"id":"123456789"}}`.
  `data.id` do JSON deve coincidir com o ID assinado da query; outros tópicos não são suportados.
- Manifesto `id:<data.id>;request-id:<x-request-id>;ts:<ts>;`, HMAC-SHA256 e comparação
  de bytes em tempo constante. O contrato desta rota é específico para IDs numéricos de payments.
- Aceita timestamp em segundos ou milissegundos; tolerância padrão 5 minutos para o passado
  e 30 segundos para o futuro. Relógio do computador/servidor deve estar sincronizado.
- Assinatura ausente, inválida, repetição de parâmetro/header ou timestamp vencido: **401**.
- JSON inválido, tópico diferente ou divergência entre ID do corpo e query: **400**.
  Tipo de conteúdo incorreto: **415**. Rota desligada: **404**.
- Falha ao gravar/confirmar transação da inbox: **503** (ou erro 5xx inesperado), nunca 200.
  O provedor pode reenviar quando não houver confirmação.
- Não existe modo para ignorar a assinatura. Não colocar segredo no Postman, Git ou prints.
- Assinatura autentica os identificadores, não torna o corpo inteiro confiável.

## Inbox persistente e worker

Tabela `mercadopago_webhook_inbox` no **banco do Billing**: ID opaco da entrega, ID do
pagamento, estado, datas, número de tentativas, reserva temporária e código fixo de erro.
Não armazena corpo bruto, Access Token, segredo, assinatura completa, cartão ou dados do comprador.

- Mesma combinação assinada de ID/request-id/timestamp ocupa uma linha; replay não reinicia trabalho concluído.
- Notificações diferentes do mesmo pagamento podem ocupar várias linhas. A tabela `pagamentos`
  continua com **uma linha por operação financeira**; cada aviso novo permite reconsultar o estado atual.
- Worker a cada 2 segundos, até 20 candidatos por ciclo. Não faz requisição externa na thread de recebimento.
- Reserva pessimista curta no banco, liberada antes do HTTP; lease de 1 minuto e token de posse.
  Se a aplicação cair, outro ciclo/instância pode retomar a reserva vencida.
- A conclusão de um worker antigo não pode encerrar a reserva de outro.
- Erros temporários: reagenda 1, 2, 4, 8 e depois até 15 minutos; no máximo 8 reservas/tentativas.
  Quedas repetidas também contam. Depois, mantém **FALHA** para intervenção.
- Referência inválida/inexistente, orçamento sem preferência, valor/moeda incompatíveis ou conflito
  financeiro: **FALHA** sem confirmar pagamento. Não fica repetindo uma incompatibilidade financeira.
- O registro financeiro continua idempotente: se houver queda entre salvar pagamento e concluir inbox,
  a retomada reconsulta e não duplica. Snapshots antigos não substituem estados novos.
- Aviso **PROCESSADO** pode representar pagamento pendente, recusado ou estornado; não significa aprovado.
- FALHA não tem reprocessamento automático ilimitado: corrigir a causa e usar reconciliação manual
  pela verificação já existente, ou uma nova notificação autenticada. Uma futura ferramenta operacional
  poderá reabrir entregas com autorização e auditoria; não editar linhas diretamente em produção.

## Variáveis (IntelliJ: Environment variables)

| Nome | Valor / origem |
|---|---|
| `MERCADOPAGO_ENABLED` | `true`, como na etapa anterior |
| `MERCADOPAGO_ACCESS_TOKEN` | Token do ambiente de teste usado no backend; manter privado |
| `MERCADOPAGO_WEBHOOK_ENABLED` | `true` |
| `MERCADOPAGO_WEBHOOK_SECRET` | Assinatura secreta gerada em Webhooks da aplicação correspondente |
| `MERCADOPAGO_WEBHOOK_TOLERANCE` | Opcional; padrão `5m`, máximo `24h` |

**Webhook Secret não é Access Token nem Public Key.** Não inventar o valor: copie a chave
gerada pelo painel. Validamos tamanho entre 32 e 512 caracteres, sem espaços.
Se o painel mudar esse formato, revisar a configuração conscientemente, não desabilitar a assinatura.

O token e a assinatura precisam corresponder à aplicação/conta e ao ambiente configurados.
Não misturar produção com teste e não confiar apenas no prefixo do token para identificar o ambiente.
Se o webhook não for usado, deixe `MERCADOPAGO_WEBHOOK_ENABLED=false`; as etapas locais anteriores continuam funcionando.

## Passo a passo manual — sem AWS

### 1. Preparar o banco e preservar o teste anterior

No PowerShell:

```powershell
cd C:\CODEX\oficina-fiap-billing-service
docker compose up -d --wait billing-db
```

Use o mesmo volume que contém o orçamento e a preferência do pagamento já realizado.
Não apague o volume e **não precisa pagar novamente** para simular uma notificação desse pagamento.
Se o banco tiver sido apagado, recriar orçamento gera outro UUID e não recupera automaticamente
o vínculo financeiro antigo; não forçar a associação.

### 2. Obter uma URL HTTPS temporária

Instale/configure o ngrok seguindo sua documentação oficial, usando a própria conta.
Em outro terminal, com o agente autenticado, execute:

```powershell
ngrok http 8083
```

Ele fornecerá uma URL HTTPS pública. O túnel pode iniciar antes do aplicativo; até a aplicação
subir, não conseguirá encaminhar requisições. Não compartilhar o token de autenticação do ngrok.
**Não apontar o túnel para 8082 / perfil local**: isso exporia as rotas internas de teste sem JWT.
Nenhum túnel foi aberto automaticamente por esta implementação.

### 3. Configurar no Mercado Pago

1. Developers > Suas integrações > aplicação que corresponde à integração usada.
2. Webhooks > Configurar notificações.
3. Na configuração de teste aplicável às suas credenciais, informar:
   `https://SEU-ENDERECO-PUBLICO/webhooks/mercadopago`.
4. Marcar **Pagamentos** (`payment`, Checkout Pro), não Orders da Checkout API.
5. Salvar e copiar a **assinatura secreta** gerada para `MERCADOPAGO_WEBHOOK_SECRET`.
6. Conferir se alguma preferência já tem `notification_url` própria: essa URL tem prioridade
   sobre o painel. O adapter atual não configura esse campo, mas preferências externas podem configurar.

A configuração de teste/produção e o simulador variam conforme conta e credenciais. Use
a aplicação/conta que emitiu o token testado na etapa anterior; não reutilizar segredo de outra aplicação.

### 4. Executar no IntelliJ sem expor as rotas locais

Pare a execução anterior. Run > Edit Configurations > BillingServiceApplication:

- Java 21.
- **Active profiles:** somente `webhook-demo` (sem `local`, sem `--spring...` nesse campo).
- Environment variables: quatro variáveis obrigatórias da tabela acima, com seus valores privados.
- Execute. Este perfil usa o mesmo PostgreSQL local na porta 5543 e escuta `127.0.0.1:8083`.

Swagger/API docs ficam desligados, health sem detalhes e rotas `/internal` não existem.
É um perfil de demonstração, **não** uma configuração de deploy Kubernetes.
Um POST sem assinatura para a rota deve retornar 401. Não usar requisição sem assinatura como prova de sucesso.

### 5. Simular e verificar o processamento

1. No painel do Mercado Pago, usar a simulação de webhook do tópico `payment` e informar o
   **ID real do pagamento de teste já realizado**. A notificação precisa conter query e headers assinados.
2. Conferir **HTTP 200** no resultado da entrega. Isso prova que o aviso foi aceito e salvo.
3. Aguardar alguns segundos e consultar **somente leitura** pelo terminal:

```powershell
docker compose exec billing-db psql -U billing_local -d billing_service -c "SELECT pagamento_id, estado, tentativas, ultimo_erro FROM mercadopago_webhook_inbox ORDER BY recebido_em DESC LIMIT 10;"
docker compose exec billing-db psql -U billing_local -d billing_service -c "SELECT pagamento_id, status_provedor, valor, moeda FROM pagamentos ORDER BY pagamento_id;"
```

O aviso deve chegar a `PROCESSADO`. No pagamento aprovado esperado, o status salvo é `approved`,
valor 199.80 BRL e o ID da operação deve coincidir. A confirmação também pode ser conferida pela
requisição 7 do Postman **depois de fechar o túnel e voltar ao perfil local / porta 8082**.
O orçamento não muda de `AGUARDANDO_APROVACAO` neste incremento: aprovação do orçamento é outro fluxo.

Repita a simulação: pode haver outra linha de inbox por ser nova entrega, mas não outro registro
financeiro com o mesmo ID. Para provar recuperação, uma falha de rede deve deixar o aviso pendente
e uma consulta posterior pode concluir. Não simular indisponibilidade na infraestrutura do grupo.

### 6. Diagnóstico

- **404**: webhook desligado, URL/porta errada ou código antigo.
- **401**: segredo de outra aplicação, header/query ausente, assinatura inválida ou relógio desajustado.
  Replay de captura antiga (mais de 5 min) pode ser recusado; prefira nova simulação/entrega assinada.
  Se houver entrega legítima assinada com timestamp antigo, avaliar a tolerância explicitamente
  ou reconciliar manualmente; não remover a validação para “fazer passar”.
- **400**: tópico não suportado ou JSON/ID divergente.
- **502 do túnel**: app não está ouvindo em 8083; não é resposta financeira do Billing.
- **200 + PENDENTE**: esperar a próxima tentativa e conferir token/rede/ID. O painel entregou;
  o worker ainda não conseguiu processar. Não confundir com status financeiro pendente.
- **200 + FALHA**: ler o código fixo e conferir vínculo/valor/moeda/preferência ou tentativas esgotadas.
  Um ID fictício do simulador não existe na API de pagamentos; usar o ID de teste real.

### 7. Encerrar

Feche o túnel com Ctrl+C, pare o aplicativo e, se não for usar mais, execute
`docker compose stop billing-db`. Os dados permanecem no volume.
Remova/desative a URL temporária no painel quando não estiver testando e atualize-a se mudar.
Inspeções de túnel podem capturar headers; não publicar capturas completas nem compartilhar a assinatura.

## Validação e limites desta entrega

Execute `.\mvnw.cmd clean verify` com Java 21. Os testes usam HTTP simulado, MockMvc e H2:
assinatura, relógio, payload incompatível, inbox, concorrência, reservas expiradas, retries,
idempotência financeira e ausência das rotas quando desligadas. As credenciais são fictícias.
Verificação real no simulador/Mercado Pago e PostgreSQL continua sendo etapa manual.

Resultado local registrado em **29/10/2026**: Java 21, `clean verify`, **136 testes aprovados**,
sem falhas/erros/ignorados e **98,04% de cobertura de linhas (549/560)**. O gate mínimo de 80%
passou. Não houve acesso à conta Mercado Pago, abertura de túnel, cobrança ou deploy AWS.

Não há AWS, SQS ou alteração dos serviços OS/Execução. A inbox usa `ddl-auto: update`, como o
esqueleto atual: antes do deploy definitivo, alinhar migrações versionadas, retenção/limpeza,
monitoramento de FALHA, rate limit/limite de body no ingresso, e política de reconciliação com a equipe.
No ingresso definitivo, a equipe deverá permitir que o provedor alcance especificamente esta rota
sem JWT do cliente: sua autenticação é a assinatura HMAC. Isso não libera as demais APIs.
Este PR não altera Gateway, cluster ou configuração de autenticação dos colegas.
Não há exatamente-uma-vez na rede: entrega é repetível e processamento é idempotente no banco.
O worker não é uma fila SQS da Saga; essa integração permanece um incremento separado.

## Referências oficiais

- [Mercado Pago: Webhooks e configuração](https://www.mercadopago.com.br/developers/pt/docs/your-integrations/notifications/webhooks).
- [SDK oficial: validação, tolerância e processamento](https://github.com/mercadopago/sdk-nodejs/blob/master/docs/webhooks.md).
- [ngrok: compartilhar localhost](https://github.com/ngrok/ngrok-docs/blob/main/share-localhost/tunnels.mdx).
