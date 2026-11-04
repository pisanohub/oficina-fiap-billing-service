# Snapshot de preços do OS — ADR-P4

## Decisão

Segundo o [ADR-P4](https://github.com/pisanohub/oficina_fiap/blob/main/documentacao/decisoes-fase4/ADR-P4-saga-orquestrada.md),
o OS captura o preço ao adicionar o item à OS e o envia em GerarOrcamento. O Billing
calcula exclusivamente com o snapshot recebido, sem chamar o Execução e sem buscar
preço mais recente. Catálogo indisponível não pode bloquear um comando válido já recebido.

GerarOrcamentoUseCase depende apenas do repositório próprio e do relógio. Reaproveitamos
esse caminho existente e retiramos o fluxo REST de cotação. Checkout/consulta/webhook
continuam usando o orçamento salvo, sem alteração de contrato financeiro.

**O JSON HTTP local abaixo simula a entrada interna; não define o envelope SQS.**
Campos/IDs/correlação/versões e autorização da origem ainda precisam de acordo com Souza.
Não implementamos consumer/publicador SQS nem modificamos serviços dos colegas nesta correção.

## Teste local sem Execução ou AWS

1. Abra Docker Desktop e inicie apenas o banco do Billing:

```powershell
cd C:\CODEX\oficina-fiap-billing-service
docker compose up -d --wait billing-db
```

2. IntelliJ: Java 21 e Active profiles **local**. Remova EXECUCAO_BASE_URL e EXECUCAO_COTACAO_*:
   não são mais utilizadas. Não precisa manter Execução/DynamoDB ligados.
3. Para testar só orçamento, deixe integrações financeiras/webhook desligadas se não for usá-las.
4. Importe a [collection de snapshot](billing-snapshot-os.postman_collection.json).
   Confira baseUrl efetiva = http://127.0.0.1:8082; ambiente Postman pode sobrescrever variáveis.
5. Execute **1 - Gerar com snapshot do OS**:

```http
POST /internal/orcamentos
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

6. Esperado: 200, total 186.00 BRL, UUID id, preços do payload e AGUARDANDO_APROVACAO.
7. **2 - Consultar salvo** e repetir 1: mesmo ID e snapshot, sem cotação externa.
8. **3 - Mesmo comando com preço diferente**: 409, sem sobrescrever o original.
9. **4 - Snapshot sem preço**: 400, sem completar o valor por consulta ao catálogo.

Mesmo um novo comando usa o preço recebido, não o catálogo. Para outro snapshot, use
novo snapshotComandoId e limpe snapshotOrcamentoId/snapshotAnterior na collection.
Isso não cancela/invalida automaticamente o orçamento anterior.

Não precisa criar pagamento para testar geração. A operação anterior de 199.80 BRL é
do orçamento original, não deste exemplo de 186.00; não misturar vínculos nas collections.
Não expor a porta 8082 ou perfil local publicamente.

## Regras

- comandoId UUID; OS/cliente numéricos positivos; sem consulta aos bancos dos colegas.
- De 1 a 100 itens, sem referência/tipo repetidos. PECA ou SERVICO.
- Referência até 80, descrição até 255; quantidade inteira positiva, sem truncar fração.
- valorUnitario obrigatório, não negativo e exato em centavos, limite numeric(19,2).
- Zero como cortesia continua permitido; checkout exige total positivo.
- Mesma chave/conteúdo normalizado: mesmo orçamento; ordem dos itens faz parte do conteúdo atual.
- Mesma chave com preço/nome/quantidade/item/OS/cliente diferente: 409.
- Em corrida, uma gravação vence; snapshots diferentes não se misturam e o perdedor conflita.
- Snapshot não compartilha lista mutável do chamador; os itens são copiados.

Essas validações não autenticam a origem. O canal definitivo deve autorizar o OS, além de
validar schema. Não basta aceitar um campo fonte=OS ou preço enviado pelo navegador.
A entrada HTTP local é só um adapter de desenvolvimento sem JWT, ausente nos demais perfis.

## Conferir no DBeaver

Conexão local: 127.0.0.1:5543, banco billing_service, usuário/senha de desenvolvimento billing_local.
Consultas somente de leitura (troque o UUID se usou outro comando):

```sql
SELECT id, comando_id, ordem_id, cliente_id, total, criado_em
FROM public.orcamentos
WHERE comando_id = '44444444-4444-4444-8444-444444444444';

SELECT * FROM public.orcamento_itens
WHERE orcamento_id IN (
    SELECT id FROM public.orcamentos
    WHERE comando_id = '44444444-4444-4444-8444-444444444444'
);
```

## Compatibilidade e contrato pendente

Nenhum DROP ou limpeza foi executado. Orçamentos/itens, preferências, pagamentos e inbox
existentes permanecem. Se orcamento_cotacoes existir, não é mais mapeada/usada: eventual
migração/limpeza exige plano separado, autorização e backup. Registros antigos seguem consultáveis.

A rota /internal/orcamentos/cotados foi retirada (404/405 conforme roteamento). A collection
antiga foi removida. Não reinterpretamos datas/versões de cotação como metadados do OS.
O [histórico REST](cotacao-execucao.md) fica apenas para rastreabilidade.

Com Souza: confirmar campos/nomes JSON (ItemOS.precoUnitario não é automaticamente o
valorUnitario do transporte), IDs/correlação, versões, proveniência/autorização e revisão/aprovação.
O modelo atual usa IDs numéricos; não converter silenciosamente osId alfanumérico.
A captura no OS e publicação após SolicitarOrcamento são responsabilidades a validar no serviço dele.

<a id="validacao"></a>

## Validação

Executar .\mvnw.cmd clean verify em Java 21. Os testes configuram um Execução simulado
com erro/outro preço e exigem **zero chamadas** ao catálogo, mesmo com propriedades
legadas habilitadas. Incluem snapshot, replay, conflitos, corrida de preços distintos,
valores/quantidades inválidos e registros históricos. Financeiro/webhook seguem na suíte.
Testes são H2/HTTP simulado; a nova entrada deve ter seu próprio teste manual PostgreSQL.
O teste antigo da cotação REST não comprova este cenário.

**Resultado em 04/11/2026:** Java 21, clean verify, **144 testes aprovados**, sem
falhas/erros/ignorados, **98,04% de linhas (549/560)** e gate mínimo de 80% aprovado.
A contagem anterior de 181 incluía o fluxo de cotação retirado; seus testes foram
substituídos por regressões do snapshot, mantendo pagamento/webhook na suíte.
Não houve consulta à conta Mercado Pago, criação de pagamento ou mudança na AWS.
