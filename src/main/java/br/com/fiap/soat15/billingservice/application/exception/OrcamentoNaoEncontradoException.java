package br.com.fiap.soat15.billingservice.application.exception;

public class OrcamentoNaoEncontradoException extends RuntimeException {
    public OrcamentoNaoEncontradoException() {
        super("Orçamento não encontrado.");
    }
}
