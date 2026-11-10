package br.com.fiap.soat15.billingservice.application.event;

import br.com.fiap.soat15.billingservice.domain.model.EstadoPagamento;
import br.com.fiap.soat15.billingservice.domain.model.Orcamento;
import br.com.fiap.soat15.billingservice.domain.model.Pagamento;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Corpo JSON plano da fila pagamento-confirmado. */
public record PagamentoConfirmado(UUID messageId, String sagaId, UUID causationId,
        UUID comandoId, long ordemId, long clienteId, UUID orcamentoId, String pagamentoId,
        EstadoPagamento status, BigDecimal valor, String moeda) {

    /** O chamador fornece o ID do evento e deve reutilizá-lo em republicações. */
    public static PagamentoConfirmado de(UUID messageId, String sagaId, UUID causationId,
            UUID comandoId, Orcamento orcamento, Pagamento pagamento) {
        Objects.requireNonNull(messageId, "messageId do evento");
        Objects.requireNonNull(causationId, "messageId de ProcessarPagamento");
        Objects.requireNonNull(comandoId, "comandoId de ProcessarPagamento");
        if (messageId.equals(causationId) || sagaId == null || sagaId.isBlank()
                || !pagamento.confirmado() || !orcamento.id().equals(pagamento.orcamentoId())
                || !"BRL".equals(pagamento.dados().moeda())
                || orcamento.total().compareTo(pagamento.dados().valor()) != 0) {
            throw new IllegalArgumentException("Pagamento ou correlação incompatível com o orçamento.");
        }
        return new PagamentoConfirmado(messageId, sagaId, causationId, comandoId,
                orcamento.ordemId(), orcamento.clienteId(), orcamento.id(), pagamento.dados().id(),
                pagamento.dados().estado(), pagamento.dados().valor(), pagamento.dados().moeda());
    }
}
