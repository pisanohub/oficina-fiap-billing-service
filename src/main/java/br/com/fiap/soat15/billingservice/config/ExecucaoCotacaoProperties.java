package br.com.fiap.soat15.billingservice.config;

import br.com.fiap.soat15.billingservice.application.exception.CotacaoNaoConfiguradaException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("execucao.cotacao")
public record ExecucaoCotacaoProperties(@DefaultValue("false") boolean enabled, @DefaultValue("") String baseUrl,
        @DefaultValue("5s") Duration timeout) {
    public void validar() {
        try {
            URI uri = URI.create(baseUrl);
            if (!enabled || uri.getHost() == null || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))
                    || timeout == null || timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
                throw new CotacaoNaoConfiguradaException();
            }
        } catch (IllegalArgumentException | NullPointerException ex) { throw new CotacaoNaoConfiguradaException(); }
    }
    @Override public String toString() { return "ExecucaoCotacaoProperties[enabled=" + enabled + ", baseUrl=CONFIGURED, timeout=" + timeout + "]"; }
}
