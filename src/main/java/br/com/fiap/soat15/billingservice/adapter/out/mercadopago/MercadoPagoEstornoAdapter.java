package br.com.fiap.soat15.billingservice.adapter.out.mercadopago;

import br.com.fiap.soat15.billingservice.application.exception.IntegracaoPagamentoException;
import br.com.fiap.soat15.billingservice.application.port.out.EstornoGateway;
import br.com.fiap.soat15.billingservice.config.MercadoPagoProperties;
import br.com.fiap.soat15.billingservice.domain.model.PagamentoConsultado;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** Sem amount: estorno integral. Nunca faz retry automático de POST. */
public class MercadoPagoEstornoAdapter implements EstornoGateway {
    private final WebClient client;
    private final MercadoPagoProperties properties;
    public MercadoPagoEstornoAdapter(WebClient client, MercadoPagoProperties properties) {
        this.client = client;
        this.properties = properties;
    }
    public void estornarIntegral(String pagamentoId, UUID chaveIdempotencia) {
        PagamentoConsultado.validarId(pagamentoId);
        if (chaveIdempotencia == null) throw new IllegalArgumentException("Chave de estorno obrigatória.");
        properties.validar();
        client.post().uri("/v1/payments/{id}/refunds", pagamentoId)
                .headers(h -> { h.setBearerAuth(properties.accessToken());
                    h.set("X-Idempotency-Key", chaveIdempotencia.toString()); })
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                .exchangeToMono(r -> r.statusCode().is2xxSuccessful() ? r.releaseBody()
                        : r.releaseBody().then(Mono.error(new IntegracaoPagamentoException(
                            "Estorno não confirmado pelo provedor.", true, false))))
                .timeout(properties.timeout())
                .onErrorMap(e -> !(e instanceof IntegracaoPagamentoException),
                        e -> new IntegracaoPagamentoException("Resultado do estorno incerto.", true, true))
                .block();
    }
}
