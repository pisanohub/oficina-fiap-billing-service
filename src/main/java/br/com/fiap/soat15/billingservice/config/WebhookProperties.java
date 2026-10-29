package br.com.fiap.soat15.billingservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.time.Duration;

@ConfigurationProperties("mercadopago.webhook")
public record WebhookProperties(@DefaultValue("false") boolean enabled, @DefaultValue("") String secret,
        @DefaultValue("5m") Duration tolerance) {
    public void validar() {
        if (!enabled || secret == null || secret.length() < 32 || secret.length() > 512
                || secret.chars().anyMatch(Character::isWhitespace)
                || tolerance == null || tolerance.isZero() || tolerance.isNegative()
                || tolerance.compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalArgumentException("Webhook exige assinatura secreta configurada e tolerância positiva de até 24 horas.");
        }
    }
    @Override public String toString() {
        return "WebhookProperties[enabled=" + enabled + ", secret=REDACTED, tolerance=" + tolerance + "]";
    }
}
