package br.com.fiap.soat15.billingservice.adapter.out.mercadopago;

import br.com.fiap.soat15.billingservice.application.exception.*;
import br.com.fiap.soat15.billingservice.config.MercadoPagoProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.WebClient;
import static org.assertj.core.api.Assertions.*;

class MercadoPagoEstornoAdapterTest {
    HttpServer server;
    String base;
    AtomicInteger chamadas = new AtomicInteger();
    AtomicReference<String> chave = new AtomicReference<>(), corpo = new AtomicReference<>(), caminho = new AtomicReference<>();
    AtomicInteger status = new AtomicInteger(201);
    @BeforeEach void iniciar() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", e -> {
            chamadas.incrementAndGet();
            caminho.set(e.getRequestURI().getPath());
            chave.set(e.getRequestHeaders().getFirst("X-Idempotency-Key"));
            corpo.set(new String(e.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            e.sendResponseHeaders(status.get(), -1);
            e.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }
    @AfterEach void fechar() { server.stop(0); }
    @Test void enviaIntegralSemValorEComChaveEstavel() {
        var id = UUID.randomUUID();
        adapter(true).estornarIntegral("12345", id);
        assertThat(chave.get()).isEqualTo(id.toString());
        assertThat(caminho.get()).isEqualTo("/v1/payments/12345/refunds");
        assertThat(corpo.get()).isEqualTo("{}");
        assertThat(chamadas.get()).isEqualTo(1);
    }
    @Test void rejeicaoNaoTemRetryAutomatico() {
        status.set(500);
        assertThatThrownBy(() -> adapter(true).estornarIntegral("12345", UUID.randomUUID()))
                .isInstanceOf(IntegracaoPagamentoException.class);
        assertThat(chamadas.get()).isEqualTo(1);
    }
    @Test void desabilitadoNaoChamaProvedor() {
        assertThatThrownBy(() -> adapter(false).estornarIntegral("12345", UUID.randomUUID()))
                .isInstanceOf(PagamentoNaoConfiguradoException.class);
        assertThat(chamadas.get()).isZero();
    }
    private MercadoPagoEstornoAdapter adapter(boolean enabled) {
        return new MercadoPagoEstornoAdapter(WebClient.builder().baseUrl(base).build(),
                new MercadoPagoProperties(enabled, "token-ficticio", Duration.ofSeconds(2)));
    }
}
