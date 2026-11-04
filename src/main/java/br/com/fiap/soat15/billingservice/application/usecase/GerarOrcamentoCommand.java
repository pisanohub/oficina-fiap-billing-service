package br.com.fiap.soat15.billingservice.application.usecase;

import br.com.fiap.soat15.billingservice.domain.model.ItemOrcamento;
import java.util.List;
import java.util.UUID;

/**
 * ADR-P4: itens contêm o snapshot de nomes/preços capturado pelo OS ao adicionar o item.
 * O Billing calcula com esses valores, sem consultar o Execução. Este comando interno
 * não implementa autenticação/transporte: o envelope SQS ainda depende de acordo com o OS.
 */
public record GerarOrcamentoCommand(UUID comandoId, long ordemId, long clienteId, List<ItemOrcamento> itens) {
}
