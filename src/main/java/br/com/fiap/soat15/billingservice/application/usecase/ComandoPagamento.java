package br.com.fiap.soat15.billingservice.application.usecase;

import br.com.fiap.soat15.billingservice.domain.model.PagamentoConsultado;
import java.util.UUID;

/** MessageBody plano, usado pelos dois comandos da mesma fila. */
public record ComandoPagamento(Tipo type, UUID messageId, String sagaId, UUID causationId,
        UUID comandoId, long ordemId, long clienteId, UUID orcamentoId, int versao, String pagamentoId) {
    public enum Tipo { ProcessarPagamento, EstornarPagamento }
    public ComandoPagamento {
        if (type == null || messageId == null || sagaId == null || sagaId.isBlank()
                || sagaId.length() > 120 || comandoId == null || ordemId <= 0 || clienteId <= 0
                || orcamentoId == null || versao <= 0) {
            throw new IllegalArgumentException("Comando financeiro incompleto.");
        }
        if (type == Tipo.EstornarPagamento) PagamentoConsultado.validarId(pagamentoId);
        else if (pagamentoId != null) throw new IllegalArgumentException("Checkout não recebe pagamentoId.");
    }
    public String chaveOperacao() {
        return type == Tipo.EstornarPagamento ? "estorno:" + pagamentoId : "checkout:" + orcamentoId;
    }
    public String identidade() {
        // Transporte pode reenviar com novo messageId. A intenção deve continuar idêntica.
        return type + "|" + sagaId + "|" + ordemId + "|" + clienteId + "|" + orcamentoId
                + "|" + versao + "|" + pagamentoId;
    }
}
