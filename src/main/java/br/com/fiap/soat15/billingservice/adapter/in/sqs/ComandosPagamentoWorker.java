package br.com.fiap.soat15.billingservice.adapter.in.sqs;

import br.com.fiap.soat15.billingservice.adapter.out.persistence.RegistroFinanceiro;
import br.com.fiap.soat15.billingservice.application.usecase.ProcessarComandoPagamentoUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

/** At-least-once. Sem logar corpo, credenciais ou exceções brutas de pagamento. */
public class ComandosPagamentoWorker {
    private static final Logger LOG = LoggerFactory.getLogger(ComandosPagamentoWorker.class);
    private final SqsClient sqs;
    private final String entrada;
    private final String saida;
    private final ProcessarComandoPagamentoUseCase processar;
    private final RegistroFinanceiro registro;

    public ComandosPagamentoWorker(SqsClient sqs, String entrada, String saida,
            ProcessarComandoPagamentoUseCase processar, RegistroFinanceiro registro) {
        this.sqs = sqs; this.entrada = entrada; this.saida = saida;
        this.processar = processar; this.registro = registro;
    }
    @Scheduled(fixedDelayString = "${billing.sqs.poll-delay:3000}")
    public void consumir() {
        try {
            var mensagens = sqs.receiveMessage(ReceiveMessageRequest.builder().queueUrl(entrada)
                    .maxNumberOfMessages(1).waitTimeSeconds(5).visibilityTimeout(120).build()).messages();
            for (var m : mensagens) {
                try {
                    processar.executar(m.body());
                    // Resultado já persistido na outbox; não depende do SendMessage.
                    sqs.deleteMessage(DeleteMessageRequest.builder().queueUrl(entrada)
                            .receiptHandle(m.receiptHandle()).build());
                } catch (RuntimeException e) {
                    LOG.warn("Comando financeiro não confirmado; mantido na fila para retry/DLQ.");
                }
            }
        } catch (RuntimeException e) {
            LOG.warn("Fila de comandos financeiros indisponível.");
        }
    }
    @Scheduled(fixedDelayString = "${billing.sqs.poll-delay:3000}")
    public void publicar() {
        try { processar.confirmarRegistrados(); }
        catch (RuntimeException e) { LOG.warn("Confirmação financeira aguardando nova tentativa."); }
        for (var e : registro.pendentes()) {
            try {
                sqs.sendMessage(SendMessageRequest.builder().queueUrl(saida).messageBody(e.corpo()).build());
                registro.enviado(e.id());
            } catch (RuntimeException ex) {
                LOG.warn("Evento financeiro permanece na outbox para reenvio.");
            }
        }
    }
}
