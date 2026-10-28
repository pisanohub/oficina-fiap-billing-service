package br.com.fiap.soat15.billingservice.application.port.out;

import br.com.fiap.soat15.billingservice.domain.model.Orcamento;
import br.com.fiap.soat15.billingservice.domain.model.PreferenciaGerada;

public interface PreferenciaPagamentoGateway {
    void validarConfiguracao();
    PreferenciaGerada criar(Orcamento orcamento);
}
