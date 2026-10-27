package br.com.fiap.soat15.billingservice.application.usecase;

import br.com.fiap.soat15.billingservice.domain.model.ItemOrcamento;
import java.util.List;
import java.util.UUID;

/** Contrato interno provisório; o envelope SQS depende de acordo com o OS Service. */
public record GerarOrcamentoCommand(UUID comandoId, long ordemId, long clienteId, List<ItemOrcamento> itens) {
}
