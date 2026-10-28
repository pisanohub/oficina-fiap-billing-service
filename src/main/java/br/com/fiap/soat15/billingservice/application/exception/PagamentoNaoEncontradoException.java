package br.com.fiap.soat15.billingservice.application.exception;

public class PagamentoNaoEncontradoException extends RuntimeException {
    public PagamentoNaoEncontradoException() { super("Pagamento não encontrado ou indisponível para a credencial configurada."); }
}
