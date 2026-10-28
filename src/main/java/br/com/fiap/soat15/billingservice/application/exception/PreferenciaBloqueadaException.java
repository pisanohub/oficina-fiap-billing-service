package br.com.fiap.soat15.billingservice.application.exception;

public class PreferenciaBloqueadaException extends RuntimeException {
    public PreferenciaBloqueadaException() {
        super("Preferência em criação ou com resultado incerto. Consulte o estado; não recrie automaticamente.");
    }
}
