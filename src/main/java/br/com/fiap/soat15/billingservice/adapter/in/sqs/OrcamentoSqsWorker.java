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
    private final String saida;
    private final EntregaSqs entrega;
    private final ProcessarMensagemOrcamentoUseCase processar;
    private final RegistroMensagensOrcamento registro;
    public OrcamentoSqsWorker(SqsClient sqs, String entrada, String saida,
            ProcessarMensagemOrcamentoUseCase processar, RegistroMensagensOrcamento registro) {
        this.sqs = sqs; this.saida = saida;
        this.entrega = new EntregaSqs(sqs, entrada, 60);
        this.processar = processar; this.registro = registro;
    }
    @Scheduled(fixedDelayString = "${billing.orcamento-sqs.poll-delay:3000}")
    public void consumir() {
        entrega.consumir(processar::executar);
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
