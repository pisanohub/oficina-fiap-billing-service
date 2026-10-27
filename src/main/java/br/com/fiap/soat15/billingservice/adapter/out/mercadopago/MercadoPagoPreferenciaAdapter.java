package br.com.fiap.soat15.billingservice.adapter.out.mercadopago;

import br.com.fiap.soat15.billingservice.application.exception.IntegracaoPagamentoException;
import br.com.fiap.soat15.billingservice.application.port.out.PreferenciaPagamentoGateway;
import br.com.fiap.soat15.billingservice.config.MercadoPagoProperties;
import br.com.fiap.soat15.billingservice.domain.model.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public class MercadoPagoPreferenciaAdapter implements PreferenciaPagamentoGateway {
    private final WebClient client;
    private final MercadoPagoProperties properties;

    public MercadoPagoPreferenciaAdapter(WebClient client, MercadoPagoProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public void validarConfiguracao() { properties.validar(); }

    public PreferenciaGerada criar(Orcamento orcamento) {
        validarConfiguracao();
        // Somente valores do domínio salvo. Não há token nem valor financeiro no request do consumidor.
        var itens = orcamento.itens().stream().filter(item -> item.valorUnitario().signum() > 0)
                .map(item -> Map.<String, Object>of("id", item.tipo() + ":" + item.referencia(),
                        "title", item.descricao(), "quantity", item.quantidade(),
                        "currency_id", "BRL", "unit_price", item.valorUnitario())).toList();
        var payload = Map.of("items", itens, "external_reference", orcamento.id().toString(),
                "metadata", Map.of("orcamento_id", orcamento.id().toString(), "ordem_id", orcamento.ordemId()));
        ApiPreferencia resposta = client.post().uri("/checkout/preferences")
                .headers(headers -> headers.setBearerAuth(properties.accessToken()))
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchangeToMono(response -> {
                    int status = response.statusCode().value();
                    if (response.statusCode().is2xxSuccessful()) return response.bodyToMono(ApiPreferencia.class);
                    boolean incerto = status >= 500 || status == 408 || status == 429 || status < 400;
                    String mensagem = status == 401 || status == 403
                            ? "Mercado Pago recusou a credencial. Confira o token do ambiente Teste e a conta seller."
                            : "Mercado Pago não aceitou a criação da preferência (HTTP " + status + ").";
                    return response.releaseBody().then(Mono.error(new IntegracaoPagamentoException(mensagem, incerto, false)));
                })
                .timeout(properties.timeout())
                .onErrorMap(TimeoutException.class, ex -> new IntegracaoPagamentoException(
                        "Tempo limite ao criar preferência. Resultado incerto; não repetir automaticamente.", true, true))
                .onErrorMap(ex -> !(ex instanceof IntegracaoPagamentoException), ex -> respostaIncerta())
                .block();
        if (resposta == null) throw respostaIncerta();
        try {
            return new PreferenciaGerada(resposta.id(), resposta.initPoint(),
                    resposta.sandboxInitPoint() == null || resposta.sandboxInitPoint().isBlank() ? null : resposta.sandboxInitPoint());
        } catch (IllegalArgumentException ex) {
            throw respostaIncerta();
        }
    }

    private IntegracaoPagamentoException respostaIncerta() {
        return new IntegracaoPagamentoException("Resposta do Mercado Pago ausente, inválida ou conexão interrompida. Resultado incerto.", true, false);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiPreferencia(String id, @JsonProperty("init_point") String initPoint,
            @JsonProperty("sandbox_init_point") String sandboxInitPoint) { }
}
