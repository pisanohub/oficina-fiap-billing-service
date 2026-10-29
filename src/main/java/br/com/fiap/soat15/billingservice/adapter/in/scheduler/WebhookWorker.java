package br.com.fiap.soat15.billingservice.adapter.in.scheduler;

import br.com.fiap.soat15.billingservice.application.usecase.ProcessarWebhooksUseCase;
import org.springframework.scheduling.annotation.Scheduled;

public class WebhookWorker {
    private final ProcessarWebhooksUseCase processar;
    public WebhookWorker(ProcessarWebhooksUseCase processar) { this.processar = processar; }
    @Scheduled(fixedDelayString = "${mercadopago.webhook.poll-delay:2000}", initialDelayString = "${mercadopago.webhook.initial-delay:2000}")
    public void executar() { processar.executar(); }
}
