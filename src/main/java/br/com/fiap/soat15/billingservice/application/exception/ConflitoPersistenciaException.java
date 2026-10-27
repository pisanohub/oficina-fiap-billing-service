package br.com.fiap.soat15.billingservice.application.exception;

/** O adapter já encerrou a transação antes de propagar o conflito para o caso de uso. */
public class ConflitoPersistenciaException extends RuntimeException {
    public ConflitoPersistenciaException(Throwable causa) {
        super("Não foi possível persistir o orçamento por conflito de integridade.", causa);
    }
}
