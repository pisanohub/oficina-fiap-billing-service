# Histórico: cotação REST retirada pelo ADR-P4

**Não usar como instrução de integração atual.** O PR #6 consultava preços no Execução
por REST e gerava pela rota /internal/orcamentos/cotados. O teste local passou, mas o
[ADR-P4](https://github.com/pisanohub/oficina_fiap/blob/main/documentacao/decisoes-fase4/ADR-P4-saga-orquestrada.md)
descartou essa estratégia: o OS captura o preço na adição do item e envia o snapshot no comando.

## Correção aplicada em 04/11/2026

- Cliente/porta/configuração de cotação e rota /internal/orcamentos/cotados removidos.
- Collection billing-cotacao.postman_collection.json retirada para não orientar uso incorreto.
- Geração consolidada em GerarOrcamentoUseCase, lendo nomes/preços do comando interno.
- Nova [collection de snapshot](billing-snapshot-os.postman_collection.json) e [guia atual](snapshot-precos-os.md).
- Testes da consulta retirada substituídos por regressões que exigem ausência de HTTP ao Execução.

Não foi executado DROP ou exclusão de registros. Se a tabela legada orcamento_cotacoes
existir, não é mais usada/mapeada. Orçamentos e itens continuam consultáveis, assim como
preferências/pagamentos. Não reutilizar chave antiga para dar outro significado ao comando.

O [PR #6](https://github.com/pisanohub/oficina-fiap-billing-service/pull/6) preserva o histórico
completo do experimento. A decisão do grupo tem prioridade. Nenhum arquivo do Execução,
infraestrutura ou workflow dos colegas foi alterado por esta correção.
