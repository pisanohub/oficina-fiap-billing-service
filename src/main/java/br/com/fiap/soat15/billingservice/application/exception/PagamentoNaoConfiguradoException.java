package br.com.fiap.soat15.billingservice.application.exception;

public class PagamentoNaoConfiguradoException extends RuntimeException {
    public PagamentoNaoConfiguradoException() {
        super("Configure MERCADOPAGO_ENABLED=true e MERCADOPAGO_ACCESS_TOKEN do ambiente Teste na execução local.");
    }
}
