# Orçamento via SQS — integração OS/Billing

## Formato e filas

Entrada: `gerar-orcamento`. Saída: `orcamento-respondido`.
O corpo é JSON plano, conforme os DTOs fornecidos pelo Souza; não há payload aninhado.
[Exemplo de entrada](gerar-orcamento.exemplo.json).
[Exemplo de sucesso](orcamento-respondido.exemplo.json).
O sucesso retorna id (UUID do orçamento), messageId, sagaId, causationId,
comandoId, ordemId, clienteId, status, moeda, total, criadoEm e itens.

type é opcional na entrada; quando informado deve ser GerarOrcamento.
versao é opcional para compatibilidade com o DTO original do OS: se enviada,
deve ser positiva e é devolvida sem alteração. Se ausente, não inventamos versão 1.
O OS continua responsável pela revisão/aprovação e precisa enviar a versão
aprovada no comando financeiro. Confirmar essa extensão com Souza.

## Sem chamada ao Execução

O Billing usa referencia, tipo (PECA/SERVICO), descricao, quantidade inteira positiva
e valorUnitario recebido no comando. Sem preço, não consulta catálogo: responde
FALHA/DADOS_ORCAMENTO_INVALIDOS. Total/precisão, limites e itens duplicados seguem
as validações de domínio já existentes. Quantidade fracionária não é truncada.

Metadados (IDs UUID, Saga, OS e cliente positivos) devem permitir correlação.
JSON ilegível, tipo errado ou IDs ausentes não recebem IDs inventados: ficam
para retry/DLQ. Não logamos o corpo. Corpos com preço inválido mas IDs válidos
geram resposta FALHA, persistida e enviada pela mesma saída.

## Idempotência e falhas

- comandoId fixa a intenção. Novo orçamento/revisão requer outro comandoId.
- Reenvio idêntico usa o orçamento, resposta e messageId de evento já salvos.
- Novo messageId de transporte não muda causationId: conservamos o primeiro.
- Preços equivalentes (45.5 e 45.50) e quantidade 2/2.0 são normalizados.
- Alterar os dados de um comando já recebido é conflito e segue para DLQ;
  nunca sobrescreve o orçamento ou a resposta original.
- Se o comando colidir com orçamento criado antes pela interface local e tiver
  conteúdo diferente, a nova resposta é FALHA/COMANDO_CONFLITANTE.
- Erro de banco/conexão não é FALHA de negócio: não apaga a mensagem de entrada.
- Crash após salvar orçamento e antes da resposta: reprocessamento reaproveita o
  orçamento existente, grava a resposta e só depois confirma o recebimento.
- A tabela mensagens_orcamento guarda inbox/outbox. Só marca enviado depois do
  SendMessage; queda após envio pode produzir duplicata com o MESMO messageId.
- SQS Standard não garante ordem. OS deve deduplicar e não aplicar revisão antiga.

## Habilitar somente após combinar ambiente

Desligado por padrão. Variáveis:

- BILLING_ORCAMENTO_SQS_ENABLED=true
- SQS_GERAR_ORCAMENTO_URL=URL real da entrada
- SQS_ORCAMENTO_RESPONDIDO_URL=URL real da saída
- AWS_REGION=us-east-1 e credenciais/role no ambiente, nunca no código.
- Datasource do Billing. Hibernate update cria a tabela adicional; coordenar
  backup/migração com Pedro. Não há DROP/limpeza de tabelas existentes.

Não depende de Mercado Pago para gerar orçamento. As filas não são criadas pelo
serviço: Pedro confirma URLs, permissões ReceiveMessage/DeleteMessage/SendMessage,
DLQ/redrive e retenção. Worker lê uma mensagem por vez, visibilidade 60 segundos.
A habilitação é independente da flag financeira do PR de pagamento/estorno.

## Validação

Testes usam H2, geração real e SQS simulada; não houve AWS ou chamada de catálogo.
Cobrem sucesso, falha, cache de resposta, retry após crash, escala monetária,
concorrência e falha no envio da resposta. Manter a collection local anterior
para testar a regra sem AWS; ela não prova a entrega pela fila.
Antes da entrega, testar conjuntamente OS → SQS → Billing → SQS → OS e salvar
evidências com IDs de correlação. O pipeline e os repositórios dos colegas não foram alterados.
