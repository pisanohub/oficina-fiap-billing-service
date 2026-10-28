package br.com.fiap.soat15.billingservice.domain.exception;

public class PagamentoIncompativelException extends RuntimeException {
    public PagamentoIncompativelException(String mensagem) { super(mensagem); }
}
