package br.com.fiap.soat15.billingservice.config;

import br.com.fiap.soat15.billingservice.adapter.in.scheduler.WebhookWorker;
import br.com.fiap.soat15.billingservice.adapter.in.web.MercadoPagoWebhookAssinatura;
import br.com.fiap.soat15.billingservice.application.port.out.WebhookInbox;
import br.com.fiap.soat15.billingservice.application.usecase.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.Clock;

@Configuration
@ConditionalOnProperty(name = "mercadopago.webhook.enabled", havingValue = "true")
@EnableConfigurationProperties(WebhookProperties.class)
@EnableScheduling
public class WebhookConfiguration {
    @Bean MercadoPagoWebhookAssinatura webhookAssinatura(WebhookProperties webhook, MercadoPagoProperties pagamento, Clock clock) {
        pagamento.validar();
        return new MercadoPagoWebhookAssinatura(webhook, clock);
    }
    @Bean ProcessarWebhooksUseCase processarWebhooksUseCase(WebhookInbox inbox, VerificarPagamentoUseCase verificar, Clock clock) {
        return new ProcessarWebhooksUseCase(inbox, verificar, clock);
    }
    @Bean WebhookWorker webhookWorker(ProcessarWebhooksUseCase processar) { return new WebhookWorker(processar); }
}
