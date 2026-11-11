package br.com.fiap.soat15.billingservice.adapter.in.sqs;

import br.com.fiap.soat15.billingservice.application.port.out.RegistroMensagensOrcamento;
import br.com.fiap.soat15.billingservice.application.usecase.ProcessarMensagemOrcamentoUseCase;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

public class OrcamentoSqsWorker {
    private static final Logger LOG = LoggerFactory.getLogger(OrcamentoSqsWorker.class);
    private final SqsClient sqs;
    private final String entrada, saida;
    private final ProcessarMensagemOrcamentoUseCase processar;
    private final RegistroMensagensOrcamento registro;
    public OrcamentoSqsWorker(SqsClient sqs, String entrada, String saida,
            ProcessarMensagemOrcamentoUseCase processar, RegistroMensagensOrcamento registro) {
        this.sqs = sqs; this.entrada = entrada; this.saida = saida;
        this.processar = processar; this.registro = registro;
    }
    @Scheduled(fixedDelayString = "${billing.orcamento-sqs.poll-delay:3000}")
    public void consumir() {
        try {
            var mensagens = sqs.receiveMessage(ReceiveMessageRequest.builder().queueUrl(entrada)
                    .maxNumberOfMessages(1).waitTimeSeconds(5).visibilityTimeout(60).build()).messages();
            for (var m : mensagens) {
                try {
                    processar.executar(m.body());
                    sqs.deleteMessage(DeleteMessageRequest.builder().queueUrl(entrada).receiptHandle(m.receiptHandle()).build());
                } catch (RuntimeException e) {
                    LOG.warn("Orçamento não confirmado; mensagem mantida para retry/DLQ.");
                }
            }
        } catch (RuntimeException e) { LOG.warn("Fila de orçamento indisponível."); }
    }
    @Scheduled(fixedDelayString = "${billing.orcamento-sqs.poll-delay:3000}")
    public void publicar() {
        for (var evento : registro.pendentes()) {
            try {
                sqs.sendMessage(SendMessageRequest.builder().queueUrl(saida).messageBody(evento.resultado()).build());
                registro.enviado(evento.comandoId());
            } catch (RuntimeException e) { LOG.warn("Resultado do orçamento permanece na outbox."); }
        }
    }
}
