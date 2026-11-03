package br.com.fiap.soat15.billingservice.adapter.out.execucao;

import br.com.fiap.soat15.billingservice.application.exception.*;
import br.com.fiap.soat15.billingservice.application.port.out.CotacaoGateway;
import br.com.fiap.soat15.billingservice.config.ExecucaoCotacaoProperties;
import br.com.fiap.soat15.billingservice.domain.model.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeoutException;

public class ExecucaoCotacaoAdapter implements CotacaoGateway {
    private final WebClient client;
    private final ExecucaoCotacaoProperties properties;
    public ExecucaoCotacaoAdapter(WebClient client, ExecucaoCotacaoProperties properties) { this.client = client; this.properties = properties; }
    public Cotacao consultar(List<ItemCotacao> itens) {
        var pedidos = ItemCotacao.validarLista(itens);
        properties.validar();
        var resposta = client.post().uri(URI.create(properties.baseUrl()).resolve("/api/v1/cotacoes"))
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                .bodyValue(new Pedido(pedidos)).exchangeToMono(response -> {
                    if (response.statusCode().value() == 200) return response.bodyToMono(Resposta.class);
                    RuntimeException erro = response.statusCode().value() == 400 ? new CotacaoRecusadaException()
                            : new IntegracaoCotacaoException(false);
                    return response.releaseBody().then(Mono.error(erro));
                }).timeout(properties.timeout())
                .onErrorMap(TimeoutException.class, ex -> new IntegracaoCotacaoException(true))
                .onErrorMap(ex -> !(ex instanceof CotacaoRecusadaException || ex instanceof IntegracaoCotacaoException),
                        ex -> new IntegracaoCotacaoException(false)).block();
        if (resposta == null || resposta.itens() == null) throw new IntegracaoCotacaoException(false);
        try {
            var snapshot = new ArrayList<ItemOrcamento>();
            for (var item : resposta.itens()) {
                if (item == null || item.quantidade() == null || item.itemId() == null || !item.itemId().equals(item.itemId().strip())) {
                    throw new IllegalArgumentException("Item incompleto.");
                }
                snapshot.add(new ItemOrcamento(item.itemId(), item.tipo(), item.nome(), item.quantidade().intValueExact(), item.precoUnitario()));
            }
            var cotacao = new Cotacao(resposta.moeda(), resposta.geradaEm(), snapshot);
            return new Cotacao(cotacao.moeda(), cotacao.geradaEm(), cotacao.conferir(pedidos));
        } catch (IllegalArgumentException | ArithmeticException ex) { throw new IntegracaoCotacaoException(false); }
    }
    public record Pedido(List<ItemCotacao> itens) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Resposta(String moeda, Instant geradaEm, List<ItemResposta> itens) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ItemResposta(TipoItem tipo, String itemId, String nome, BigDecimal quantidade, BigDecimal precoUnitario) { }
}
