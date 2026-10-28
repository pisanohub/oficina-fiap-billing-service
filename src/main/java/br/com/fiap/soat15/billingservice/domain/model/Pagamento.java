package br.com.fiap.soat15.billingservice.domain.model;

import br.com.fiap.soat15.billingservice.domain.exception.PagamentoIncompativelException;
import java.time.Instant;
import java.util.UUID;

/** Registro de uma operação externa verificada contra o orçamento. */
public record Pagamento(UUID orcamentoId, PagamentoConsultado dados, Instant consultadoEm) {
    public Pagamento {
        if (orcamentoId == null || dados == null || consultadoEm == null
                || !orcamentoId.toString().equals(dados.referenciaExterna())) {
            throw new PagamentoIncompativelException("Pagamento não corresponde ao orçamento informado.");
        }
    }

    public boolean confirmado() { return dados.estado() == EstadoPagamento.APROVADO; }

    public Pagamento atualizarCom(Pagamento novo) {
        if (!orcamentoId.equals(novo.orcamentoId()) || !dados.id().equals(novo.dados().id())
                || dados.valor().compareTo(novo.dados().valor()) != 0 || !dados.moeda().equals(novo.dados().moeda())) {
            throw new PagamentoIncompativelException("Operação já vinculada a outros dados financeiros.");
        }
        int ordem = novo.dados().atualizadoNoProvedor().compareTo(dados.atualizadoNoProvedor());
        if (ordem < 0) return this;
        if (ordem == 0) {
            if (!dados.equals(novo.dados())) {
                throw new PagamentoIncompativelException("Respostas divergentes com a mesma data do provedor; requer reconciliação.");
            }
            return this;
        }
        return novo;
    }
}
