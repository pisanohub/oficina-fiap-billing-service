package br.com.fiap.soat15.billingservice.application.exception;
public class CotacaoNaoConfiguradaException extends RuntimeException {
    public CotacaoNaoConfiguradaException() { super("Integração de cotação com o Execução não está configurada."); }
}
