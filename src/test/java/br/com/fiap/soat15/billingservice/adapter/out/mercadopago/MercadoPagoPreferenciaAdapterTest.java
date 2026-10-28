package br.com.fiap.soat15.billingservice.adapter.out.mercadopago;

import br.com.fiap.soat15.billingservice.application.exception.*;
import br.com.fiap.soat15.billingservice.config.MercadoPagoProperties;
import br.com.fiap.soat15.billingservice.domain.model.*;
import com.sun.net.httpserver.HttpServer;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.reactive.function.client.WebClient;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;

/** Servidor HTTP local controlado: estes testes nunca chamam a API real. */
class MercadoPagoPreferenciaAdapterTest {
    private HttpServer servidor;
    private final AtomicInteger chamadas = new AtomicInteger();
    private final AtomicReference<String> payload = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private int status = 201;
    private String resposta = """
            {"id":"pref-teste","init_point":"https://www.mercadopago.com.br/checkout/start?pref_id=pref-teste",
            "sandbox_init_point":"https://sandbox.mercadopago.com.br/checkout/pay?pref_id=pref-teste","collector_id":123}
            """;
    private long atraso;

    @BeforeEach
    void iniciar() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/checkout/preferences", exchange -> {
            chamadas.incrementAndGet();
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            payload.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                if (atraso > 0) Thread.sleep(atraso);
                byte[] bytes = resposta.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        servidor.start();
    }

    @AfterEach
    void encerrar() { servidor.stop(0); }

    @Test
    void enviaValoresDoOrcamentoSalvoEReferenciaSemCredencialNoCorpo() {
        var orcamento = orcamento();
        var resultado = adapter(Duration.ofSeconds(3)).criar(orcamento);
        assertThat(resultado.id()).isEqualTo("pref-teste");
        assertThat(resultado.checkoutTesteUrl()).contains("sandbox.mercadopago");
        assertThat(authorization.get()).isEqualTo("Bearer token-ficticio-apenas-no-servidor-local");
        assertThat((String) JsonPath.read(payload.get(), "$.external_reference")).isEqualTo(orcamento.id().toString());
        assertThat((String) JsonPath.read(payload.get(), "$.items[0].currency_id")).isEqualTo("BRL");
        assertThat((Integer) JsonPath.read(payload.get(), "$.items[0].quantity")).isEqualTo(2);
        assertThat((Double) JsonPath.read(payload.get(), "$.items[0].unit_price")).isEqualTo(49.9);
        assertThat((List<?>) JsonPath.read(payload.get(), "$.items")).hasSize(1); // Cortesia não é cobrada.
        assertThat(payload.get()).doesNotContain("token-ficticio", "access_token");
        assertThat(chamadas.get()).isEqualTo(1);
    }

    @Test
    void permiteRespostaSemLinkSandboxETrataCampoVazio() {
        resposta = "{\"id\":\"p1\",\"init_point\":\"https://www.mercadopago.com.br/checkout/p1\"}";
        assertThat(adapter(Duration.ofSeconds(3)).criar(orcamento()).checkoutTesteUrl()).isNull();
        resposta = "{\"id\":\"p1\",\"init_point\":\"https://www.mercadopago.com.br/checkout/p1\",\"sandbox_init_point\":\"\"}";
        assertThat(adapter(Duration.ofSeconds(3)).criar(orcamento()).checkoutTesteUrl()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 408, 429, 500, 302})
    void sanitizaErroSemRetryOuVazamentoDoCorpo(int codigo) {
        status = codigo;
        resposta = "{\"message\":\"token-secreto-que-nao-pode-ser-retornado\"}";
        assertThatThrownBy(() -> adapter(Duration.ofSeconds(3)).criar(orcamento()))
                .isInstanceOfSatisfying(IntegracaoPagamentoException.class, ex -> {
                    assertThat(ex.getMessage()).doesNotContain("token-secreto");
                    assertThat(ex.resultadoIncerto()).isEqualTo(codigo >= 500 || codigo == 408 || codigo == 429 || codigo < 400);
                    assertThat(ex.timeout()).isFalse();
                });
        assertThat(chamadas.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "nao-json", "{\"id\":\"p1\",\"init_point\":\"https://outro-site.example/checkout\"}"})
    void respostaIncompletaOuInvalidaEIncerta(String corpo) {
        resposta = corpo;
        assertThatThrownBy(() -> adapter(Duration.ofSeconds(3)).criar(orcamento()))
                .isInstanceOfSatisfying(IntegracaoPagamentoException.class, ex -> assertThat(ex.resultadoIncerto()).isTrue());
    }

    @Test
    void timeoutNaoProvocaNovaSolicitacao() {
        atraso = 400;
        assertThatThrownBy(() -> adapter(Duration.ofMillis(100)).criar(orcamento()))
                .isInstanceOfSatisfying(IntegracaoPagamentoException.class, ex -> {
                    assertThat(ex.resultadoIncerto()).isTrue();
                    assertThat(ex.timeout()).isTrue();
                });
        assertThat(chamadas.get()).isLessThanOrEqualTo(1);
    }

    @Test
    void configuracaoDesativadaOuSemTokenNaoEnviaRequestENaoExpoeSegredo() {
        for (var config : List.of(new MercadoPagoProperties(false, "ficticio", Duration.ofSeconds(1)),
                new MercadoPagoProperties(true, "", Duration.ofSeconds(1)),
                new MercadoPagoProperties(true, null, Duration.ofSeconds(1)),
                new MercadoPagoProperties(true, "segredo com espaco", Duration.ofSeconds(1)))) {
            assertThatThrownBy(() -> new MercadoPagoPreferenciaAdapter(cliente(), config).criar(orcamento()))
                    .isInstanceOf(PagamentoNaoConfiguradoException.class);
            assertThat(config.toString()).doesNotContain("ficticio", "segredo com espaco");
        }
        assertThat(chamadas.get()).isZero();
        for (Duration timeout : List.of(Duration.ZERO, Duration.ofSeconds(-1), Duration.ofSeconds(31))) {
            assertThatIllegalArgumentException().isThrownBy(() -> new MercadoPagoProperties(true, "ficticio", timeout));
        }
        assertThatIllegalArgumentException().isThrownBy(() -> new MercadoPagoProperties(true, "ficticio", null));
    }

    private WebClient cliente() { return WebClient.builder().baseUrl("http://127.0.0.1:" + servidor.getAddress().getPort()).build(); }
    private MercadoPagoPreferenciaAdapter adapter(Duration timeout) {
        return new MercadoPagoPreferenciaAdapter(cliente(), new MercadoPagoProperties(true, "token-ficticio-apenas-no-servidor-local", timeout));
    }
    private Orcamento orcamento() {
        return new Orcamento(UUID.randomUUID(), UUID.randomUUID(), 1, 2,
                List.of(new ItemOrcamento("oleo", TipoItem.PECA, "Óleo", 2, new BigDecimal("49.90")),
                        new ItemOrcamento("cortesia", TipoItem.SERVICO, "Cortesia", 1, BigDecimal.ZERO)), Instant.now());
    }
}
