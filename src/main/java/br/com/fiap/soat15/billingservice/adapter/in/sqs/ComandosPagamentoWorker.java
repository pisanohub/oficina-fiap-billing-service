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
    private final EntregaSqs entrega;
    private final String saida;
    private final ProcessarComandoPagamentoUseCase processar;
    private final RegistroFinanceiro registro;

    public ComandosPagamentoWorker(SqsClient sqs, String entrada, String saida,
            ProcessarComandoPagamentoUseCase processar, RegistroFinanceiro registro) {
        this.sqs = sqs; this.saida = saida;
        this.entrega = new EntregaSqs(sqs, entrada, 120);
        this.processar = processar; this.registro = registro;
    }
    @Scheduled(fixedDelayString = "${billing.sqs.poll-delay:3000}")
    public void consumir() {
        entrega.consumir(processar::executar);
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
