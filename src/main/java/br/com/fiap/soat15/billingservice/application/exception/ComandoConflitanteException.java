package br.com.fiap.soat15.billingservice.application.exception;

public class ComandoConflitanteException extends RuntimeException {
    public ComandoConflitanteException() {
        super("comandoId já utilizado com outro conteúdo.");
    }
}
