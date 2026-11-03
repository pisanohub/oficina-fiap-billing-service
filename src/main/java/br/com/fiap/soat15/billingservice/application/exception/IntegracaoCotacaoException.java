package br.com.fiap.soat15.billingservice.application.exception;
public class IntegracaoCotacaoException extends RuntimeException {
    private final boolean timeout;
    public IntegracaoCotacaoException(boolean timeout) {
        super(timeout ? "Tempo limite na consulta de cotação; nenhum orçamento foi salvo por esta tentativa."
                : "Resposta de cotação inválida ou Execução indisponível; nenhum orçamento foi salvo por esta tentativa.");
        this.timeout = timeout;
    }
    public boolean timeout() { return timeout; }
}
