package br.com.fiap.soat15.billingservice.application.exception;
public class CotacaoRecusadaException extends RuntimeException {
    public CotacaoRecusadaException() { super("O Execução recusou a cotação. Confira se os itens existem e estão ativos no catálogo."); }
}
