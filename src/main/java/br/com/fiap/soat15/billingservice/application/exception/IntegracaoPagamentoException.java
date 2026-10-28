package br.com.fiap.soat15.billingservice.application.exception;

/** Erro sanitizado: não propagar token, corpo ou exceção HTTP do provedor ao consumidor. */
public class IntegracaoPagamentoException extends RuntimeException {
    private final boolean resultadoIncerto;
    private final boolean timeout;

    public IntegracaoPagamentoException(String mensagem, boolean resultadoIncerto, boolean timeout) {
        super(mensagem);
        this.resultadoIncerto = resultadoIncerto;
        this.timeout = timeout;
    }

    public boolean resultadoIncerto() { return resultadoIncerto; }
    public boolean timeout() { return timeout; }
}
