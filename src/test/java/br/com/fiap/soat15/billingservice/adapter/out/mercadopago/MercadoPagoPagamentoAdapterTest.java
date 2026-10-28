package br.com.fiap.soat15.billingservice.adapter.out.mercadopago;

import br.com.fiap.soat15.billingservice.application.exception.*;
import br.com.fiap.soat15.billingservice.config.MercadoPagoProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.reactive.function.client.WebClient;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class MercadoPagoPagamentoAdapterTest {
    private HttpServer servidor;
    private final AtomicInteger chamadas = new AtomicInteger();
    private final AtomicReference<String> metodo = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private int status = 200;
    private long atraso;
    private String resposta = """
        {"id":186426882414,"external_reference":"11111111-1111-4111-8111-111111111111",
         "status":"approved","status_detail":"accredited","transaction_amount":"199.80",
         "currency_id":"BRL","transaction_amount_refunded":0,"date_last_updated":"2026-10-28T09:00:00-03:00",
         "payer":{"email":"nao-armazenar@example.com"}}
        """;
    @BeforeEach
    void iniciar() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/v1/payments/186426882414", exchange -> {
            chamadas.incrementAndGet(); metodo.set(exchange.getRequestMethod());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            try {
                if (atraso > 0) Thread.sleep(atraso);
                var bytes = resposta.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        servidor.start();
    }
    @AfterEach void encerrar() { servidor.stop(0); }
    @Test
    void usaGetAutenticadoELeNumerosDecimaisDatasESomenteCamposNecessarios() {
        var dados = adapter(Duration.ofSeconds(3)).consultar("186426882414");
        assertThat(dados.id()).isEqualTo("186426882414");
        assertThat(dados.valor()).isEqualByComparingTo("199.80");
        assertThat(dados.atualizadoNoProvedor()).isEqualTo(Instant.parse("2026-10-28T12:00:00Z"));
        assertThat(metodo.get()).isEqualTo("GET");
        assertThat(authorization.get()).isEqualTo("Bearer token-ficticio-local");
        assertThat(dados.toString()).doesNotContain("nao-armazenar", "token-ficticio");
    }
    @ParameterizedTest @ValueSource(ints = {400,401,403,429,500,302})
    void erroNaoVazaCorpoNemCredencial(int codigo) {
        status = codigo; resposta = "{\"message\":\"segredo-do-corpo\"}";
        assertThatThrownBy(() -> adapter(Duration.ofSeconds(3)).consultar("186426882414"))
                .isInstanceOfSatisfying(IntegracaoPagamentoException.class, ex -> {
                    assertThat(ex.getMessage()).doesNotContain("segredo-do-corpo", "token-ficticio");
                    assertThat(ex.resultadoIncerto()).isFalse();
                });
        assertThat(chamadas.get()).isEqualTo(1);
    }
    @Test void pagamentoNaoEncontradoTemErroProprio() {
        status = 404;
        assertThatThrownBy(() -> adapter(Duration.ofSeconds(3)).consultar("186426882414")).isInstanceOf(PagamentoNaoEncontradoException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"", "{}", "nao-json", "{\"id\":186426882414,\"status\":\"approved\"}"})
    void naoAceitaRespostaIncompleta(String corpo) {
        resposta = corpo;
        assertThatThrownBy(() -> adapter(Duration.ofSeconds(3)).consultar("186426882414")).isInstanceOf(IntegracaoPagamentoException.class);
    }
    @Test void timeoutENaoConfiguradoNaoConfirmamPagamento() {
        atraso = 400;
        assertThatThrownBy(() -> adapter(Duration.ofMillis(100)).consultar("186426882414"))
                .isInstanceOfSatisfying(IntegracaoPagamentoException.class, ex -> assertThat(ex.timeout()).isTrue());
        assertThatThrownBy(() -> new MercadoPagoPagamentoAdapter(cliente(), new MercadoPagoProperties(false, "", Duration.ofSeconds(1)))
                .consultar("186426882414")).isInstanceOf(PagamentoNaoConfiguradoException.class);
        assertThatIllegalArgumentException().isThrownBy(() -> adapter(Duration.ofSeconds(3)).consultar("../me"));
    }
    private WebClient cliente() { return WebClient.builder().baseUrl("http://127.0.0.1:"+servidor.getAddress().getPort()).build(); }
    private MercadoPagoPagamentoAdapter adapter(Duration timeout) {
        return new MercadoPagoPagamentoAdapter(cliente(), new MercadoPagoProperties(true, "token-ficticio-local", timeout));
    }
}
