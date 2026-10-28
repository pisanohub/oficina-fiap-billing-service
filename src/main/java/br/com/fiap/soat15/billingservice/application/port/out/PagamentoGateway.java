package br.com.fiap.soat15.billingservice.application.port.out;

import br.com.fiap.soat15.billingservice.domain.model.PagamentoConsultado;

public interface PagamentoGateway {
    PagamentoConsultado consultar(String pagamentoId);
}
