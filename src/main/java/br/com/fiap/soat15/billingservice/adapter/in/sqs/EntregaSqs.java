package br.com.fiap.soat15.billingservice.adapter.in.sqs;

import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

/** Transporte compartilhado: ACK somente após o callback concluir a gravação durável. */
public class EntregaSqs {
    private static final Logger LOG = LoggerFactory.getLogger(EntregaSqs.class);
    private final SqsClient sqs;
    private final String fila;
    private final int visibilidade;

    public EntregaSqs(SqsClient sqs, String fila, int visibilidade) {
        this.sqs = sqs;
        this.fila = fila;
        this.visibilidade = visibilidade;
    }

    public void consumir(Consumer<String> processar) {
        try {
            var mensagens = sqs.receiveMessage(ReceiveMessageRequest.builder().queueUrl(fila)
                    .maxNumberOfMessages(1).waitTimeSeconds(5).visibilityTimeout(visibilidade).build()).messages();
            for (var mensagem : mensagens) {
                try {
                    processar.accept(mensagem.body());
                    sqs.deleteMessage(DeleteMessageRequest.builder().queueUrl(fila)
                            .receiptHandle(mensagem.receiptHandle()).build());
                } catch (RuntimeException e) {
                    LOG.warn("Mensagem não confirmada; mantida para retry/DLQ.");
                }
            }
        } catch (RuntimeException e) {
            LOG.warn("Fila de entrada indisponível.");
        }
    }
}
