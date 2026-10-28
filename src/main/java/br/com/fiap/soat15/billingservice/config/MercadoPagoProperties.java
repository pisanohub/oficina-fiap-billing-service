package br.com.fiap.soat15.billingservice.config;

import br.com.fiap.soat15.billingservice.application.exception.PagamentoNaoConfiguradoException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.time.Duration;

@ConfigurationProperties("mercadopago")
public record MercadoPagoProperties(@DefaultValue("false") boolean enabled,
        @DefaultValue("") String accessToken, @DefaultValue("10s") Duration timeout) {
    public MercadoPagoProperties {
        if (timeout == null || timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("Timeout do Mercado Pago deve ser positivo e no máximo 30 segundos.");
        }
    }

    public void validar() {
        if (!enabled || accessToken == null || accessToken.isBlank()
                || accessToken.chars().anyMatch(Character::isWhitespace)) {
            throw new PagamentoNaoConfiguradoException();
        }
    }

    @Override
    public String toString() { return "MercadoPagoProperties[enabled=" + enabled + ", accessToken=REDACTED, timeout=" + timeout + "]"; }
}
