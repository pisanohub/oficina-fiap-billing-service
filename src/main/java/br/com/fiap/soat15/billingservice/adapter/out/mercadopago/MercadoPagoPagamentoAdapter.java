package br.com.fiap.soat15.billingservice.adapter.out.mercadopago;

import br.com.fiap.soat15.billingservice.application.exception.*;
import br.com.fiap.soat15.billingservice.application.port.out.PagamentoGateway;
import br.com.fiap.soat15.billingservice.config.MercadoPagoProperties;
import br.com.fiap.soat15.billingservice.domain.model.PagamentoConsultado;
import com.fasterxml.jackson.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.TimeoutException;

public class MercadoPagoPagamentoAdapter implements PagamentoGateway {
    private final WebClient client;
    private final MercadoPagoProperties properties;
    public MercadoPagoPagamentoAdapter(WebClient client, MercadoPagoProperties properties) {
        this.client = client;
        this.properties = properties;
    }
    public PagamentoConsultado consultar(String pagamentoId) {
        PagamentoConsultado.validarId(pagamentoId);
        properties.validar();
        ApiPagamento resposta = client.get().uri("/v1/payments/{id}", pagamentoId)
                .headers(headers -> headers.setBearerAuth(properties.accessToken()))
                .accept(MediaType.APPLICATION_JSON)
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) return response.bodyToMono(ApiPagamento.class);
                    RuntimeException erro = response.statusCode().value() == 404 ? new PagamentoNaoEncontradoException()
                            : new IntegracaoPagamentoException("Não foi possível consultar o pagamento no Mercado Pago (HTTP "
                                    + response.statusCode().value() + "). Confira a credencial e tente novamente.", false, false);
                    return response.releaseBody().then(Mono.error(erro));
                })
                .timeout(properties.timeout())
                .onErrorMap(TimeoutException.class, ex -> new IntegracaoPagamentoException(
                        "Tempo limite na consulta. Nenhum pagamento foi confirmado por esta tentativa.", false, true))
                .onErrorMap(ex -> !(ex instanceof IntegracaoPagamentoException || ex instanceof PagamentoNaoEncontradoException), ex -> invalida())
                .block();
        if (resposta == null) throw invalida();
        try {
            return new PagamentoConsultado(resposta.id(), resposta.referenciaExterna(), resposta.status(), resposta.statusDetalhe(),
                    resposta.valor(), resposta.moeda(), resposta.valorEstornado(), resposta.atualizadoNoProvedor());
        } catch (IllegalArgumentException ex) { throw invalida(); }
    }
    private IntegracaoPagamentoException invalida() {
        return new IntegracaoPagamentoException("Resposta de consulta inválida ou conexão interrompida. Nenhum novo resultado foi registrado.", false, false);
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiPagamento(String id, @JsonProperty("external_reference") String referenciaExterna,
            String status, @JsonProperty("status_detail") String statusDetalhe,
            @JsonProperty("transaction_amount") BigDecimal valor, @JsonProperty("currency_id") String moeda,
            @JsonProperty("transaction_amount_refunded") BigDecimal valorEstornado,
            @JsonProperty("date_last_updated") Instant atualizadoNoProvedor) { }
}
