# Segunda etapa: preferência de Checkout Pro

Esta etapa conecta o orçamento salvo à **Preferences API** do Mercado Pago.
Não confirma pagamento, não aprova orçamento e não publica eventos de Saga.
É uma demonstração local até o grupo definir a aprovação e o contrato ProcessarPagamento.

## 1. O que o código faz

1. Recebe somente o ID do orçamento na URL, sem preço nem credencial no corpo.
2. Lê o orçamento persistido e exige total positivo.
3. Se já há preferência CRIADA, devolve o link salvo sem chamar o provedor outra vez.
4. Reserva a operação em `preferencias_checkout`, com chave única por orçamento.
5. Envia `POST https://api.mercadopago.com/checkout/preferences` usando WebClient.
   Os itens são snapshots do orçamento, com quantidade, preço, BRL e referência.
   Itens de cortesia com preço zero são omitidos, mantendo o mesmo total positivo.
6. Envia `external_reference` igual ao UUID do orçamento e metadados de correlação.
7. Salva o ID e os links devolvidos pelo provedor. O orçamento continua AGUARDANDO_APROVACAO.

O cliente não escolhe o valor nessa requisição. O cadastro local de orçamento ainda
aceita preços de teste; obter preços de fonte confiável na Execução permanece pendente.

## 2. Como as camadas foram estendidas

| Parte | Função |
|---|---|
| `domain/model/Preferencia*` | Dados da preferência e links HTTPS do Mercado Pago. |
| `application/usecase/CriarPreferenciaUseCase` | Buscar orçamento, reservar chamada e registrar resultado. |
| `application/port/out/PreferenciaPagamentoGateway` | Interface que separa a regra da API externa. |
| `adapter/out/mercadopago` | Headers, JSON e tratamento HTTP com token só no backend. |
| `adapter/out/persistence/Preferencia*` | Persistência da reserva e dos links. |
| `adapter/in/web/PreferenciaLocalController` | POST/GET apenas com perfil local. |
| `config/MercadoPagoProperties` | Habilitação explícita, token e timeout; toString oculta o segredo. |

Não há transação de banco aberta durante espera pela API. Não há retry automático do POST.
A tabela de preferências persiste a reserva antes da chamada para impedir duas criações concorrentes.
Uma única preferência é reutilizada por orçamento nesta etapa. Vencimento, alteração do
orçamento e múltiplas tentativas de checkout precisam de contrato específico na próxima etapa.

## 3. Configure no IntelliJ

Em **Run → Edit Configurations → BillingServiceApplication**:

- JRE: Java 21.
- Program arguments: `--spring.profiles.active=local`.
- Environment variables: `MERCADOPAGO_ENABLED=true` e `MERCADOPAGO_ACCESS_TOKEN`, com
  o token copiado do seletor **Teste** da sua aplicação Checkout Pro / API de Preferences.

Não use o prefixo do token para inferir o ambiente. Não coloque o token no Git, no JSON
do Postman ou em prints. Public Key, senha buyer/seller e código de verificação não
são necessários no backend para criar a preferência.

Sem habilitação ou sem token, a rota retorna 503 e não reserva/chama o provedor.
O código não consulta nem verifica automaticamente o ambiente da credencial: a escolha
do token de Teste é responsabilidade da configuração. Ainda não habilitar no Kubernetes.

Se o aplicativo já estiver rodando, pare e reinicie pela configuração atualizada.
O token definido no IntelliJ só vale para aquela execução; não passa para um PowerShell externo.
Não use um comando com o token literal no terminal, para não gravá-lo no histórico.

## 4. Teste no Postman

1. Deixe PostgreSQL local e Billing ativos. Reimporte `docs/billing-local.postman_collection.json`.
2. Execute **1 - Gerar** e **2 - Consultar**; o orçamento de exemplo tem total 199,80.
3. Execute **4 - Criar preferência** (POST sem Body):
   `http://127.0.0.1:8082/internal/orcamentos/{{orcamentoId}}/preferencia`.
4. Esperado: HTTP 200, `estado=CRIADA`, `preferenciaId` e `checkoutUrl`.
   `checkoutTesteUrl` é o sandbox_init_point quando retornado pela API e pode ser nulo.
5. Execute **5 - Consultar estado** e repita **4**: retorna a preferência salva.

Exemplo ilustrativo de resposta (não é uma preferência real):

```json
{
  "orcamentoId": "11111111-1111-4111-8111-111111111111",
  "estado": "CRIADA",
  "preferenciaId": "identificador-retornado-pelo-provedor",
  "checkoutUrl": "https://www.mercadopago.com.br/checkout/start?pref_id=...",
  "checkoutTesteUrl": "https://sandbox.mercadopago.com.br/checkout/pay?pref_id=...",
  "atualizadoEm": "2026-10-27T12:00:00Z"
}
```

Para a compra manual, siga o fluxo de testes oficial da modalidade usando conta buyer
de teste e cartão de teste. Use o link de checkout retornado conforme o procedimento
de testes do painel; não altere o domínio ou construa URLs manualmente. Separe a sessão
buyer da conta seller, por exemplo com uma janela privada.

Abrir ou finalizar o checkout não atualiza o estado financeiro do Billing nesta etapa.
Não há URLs de retorno nem webhook configurados, porque o endpoint HTTPS público ainda
precisa ser combinado. A criação mínima usa os itens obrigatórios; notificações,
consulta de pagamento e reembolso serão próximos incrementos.

## 5. Erros e repetição

| Resposta | Significado / ação |
|---|---|
| 400 | UUID inválido ou orçamento sem total positivo. |
| 404 | Orçamento inexistente; no GET da preferência, ainda não há reserva. |
| 503 | Integração desabilitada/token ausente; ajustar configuração e reiniciar. |
| 502, resultadoIncerto=false | Provedor rejeitou definitivamente (por exemplo 400/401/403). Corrigir configuração; nova chamada explícita é permitida. Não é pagamento recusado. |
| 502/504, resultadoIncerto=true | Falha de rede, timeout, erro do provedor ou resposta inválida; consultar estado e reconciliar antes de repetir. |
| 409 | Já está CRIANDO ou RESULTADO_INCERTO; nenhuma nova criação é enviada. |

Estados de **preferência**, não de pagamento: CRIANDO, CRIADA, FALHA_DEFINITIVA e RESULTADO_INCERTO.
Se a aplicação cair depois do envio e antes de salvar, CRIANDO fica persistido e bloqueia
reenvio cego. Ainda não há reconciliação automática. Não apagar reservas para contornar
esse bloqueio; conferir a operação no provedor, buscando pela referência externa.

`external_reference` serve para correlação; não é uma garantia de idempotência do provedor.
Também não se assume suporte de X-Idempotency-Key no POST de preferências. O controle
implementado evita reenvio cego e conserva resultados incertos para recuperação futura.
Uma preferência não impede por si só pagamentos múltiplos; o domínio financeiro deve
tratar isso quando implementarmos confirmação e ProcessarPagamento da Saga.

## 6. Validação e próximos passos

Testes automatizados usam servidor HTTP local com token fictício e H2. Verificam payload,
headers, resposta válida/inválida, ausência de credencial, erro HTTP, timeout, repetição,
concorrência e ausência das rotas fora do perfil local. Não chamam a conta real.

Ainda faltam: aprovação e contrato do OS, confirmação via API/webhook autenticado,
reconciliação, estado de pagamento, reembolso e eventos SQS/outbox.
O teste na sua conta Mercado Pago deve ser realizado manualmente com sua configuração
do IntelliJ. Não se declara aprovação financeira nem deploy real com base nos testes simulados.

### Evidência desta revisão (27/10/2026)

- `mvnw.cmd clean verify`: 72 testes aprovados, sem falhas/erros/ignorados.
- Cobertura JaCoCo: 280 de 285 linhas, 98,25%; limite de 80% aprovado.
- Verificação real de inicialização com PostgreSQL 16 local, em instância temporária
  na porta 18082: health UP, orçamento criado e tabela preferencias_checkout disponível.
- Com Mercado Pago explicitamente desabilitado, POST de preferência retornou 503 e GET
  retornou 404, confirmando ausência de reserva. A instância temporária foi encerrada.
- Uma preferência real ainda não foi criada por esta validação. As chamadas HTTP
  automatizadas foram dirigidas a um servidor local, com token fictício.

## Referências oficiais

- Criar preferência: https://www.mercadopago.com.br/developers/pt/reference/online-payments/checkout-pro-preferences/create-preference/post
- Configurar preferência: https://www.mercadopago.com.br/developers/pt/docs/checkout-pro-preferences/create-payment-preference
- Compra de teste: https://www.mercadopago.com.br/developers/pt/docs/checkout-pro-preferences/integration-test/test-purchases
