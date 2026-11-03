package br.com.fiap.soat15.billingservice.adapter.out.execucao;

import br.com.fiap.soat15.billingservice.application.exception.*;
import br.com.fiap.soat15.billingservice.config.ExecucaoCotacaoProperties;
import br.com.fiap.soat15.billingservice.domain.model.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.web.reactive.function.client.WebClient;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;

class ExecucaoCotacaoAdapterTest {
    private HttpServer server;
    private int status = 200;
    private long atraso;
    private final AtomicInteger chamadas = new AtomicInteger();
    private final AtomicReference<String> corpo = new AtomicReference<>();
    private final AtomicReference<String> metodo = new AtomicReference<>();
    private String resposta = valida();
    private final List<ItemCotacao> pedidos = List.of(new ItemCotacao(TipoItem.PECA, "filtro-oleo", 2));
    private static String valida() {
        return "{\"moeda\":\"BRL\",\"geradaEm\":\"2026-11-03T09:00:00-03:00\",\"itens\":[{\"tipo\":\"PECA\",\"itemId\":\"filtro-oleo\",\"nome\":\"Filtro de óleo\",\"quantidade\":2,\"precoUnitario\":45.50}]}";
    }
    @BeforeEach void iniciar() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/cotacoes", exchange -> {
            chamadas.incrementAndGet(); metodo.set(exchange.getRequestMethod());
            corpo.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                if (atraso > 0) Thread.sleep(atraso);
                var bytes = resposta.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes);
            } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        }); server.start();
    }
    @AfterEach void fechar() { server.stop(0); }
    @Test void postEnviaSoReferenciasERecebePrecoNomeMoedaEData() {
        var cotacao = adapter(Duration.ofSeconds(3)).consultar(pedidos);
        assertThat(metodo.get()).isEqualTo("POST");
        assertThat(corpo.get()).contains("\"itemId\":\"filtro-oleo\"", "\"quantidade\":2", "\"tipo\":\"PECA\"")
                .doesNotContain("preco", "valor", "nome", "chave", "token");
        assertThat(cotacao.geradaEm()).isEqualTo(Instant.parse("2026-11-03T12:00:00Z"));
        assertThat(cotacao.itens().getFirst().valorUnitario()).isEqualByComparingTo("45.50");
        assertThat(cotacao.itens().getFirst().subtotal()).isEqualByComparingTo("91.00");
        assertThat(chamadas.get()).isEqualTo(1);
    }
    @Test void catalogoRecusadoViraErroDeNegocioSanitizado() {
        status = 400; resposta = "{\"detail\":\"nao-vazar-corpo\"}";
        assertThatThrownBy(() -> adapter(Duration.ofSeconds(3)).consultar(pedidos))
                .isInstanceOf(CotacaoRecusadaException.class).hasMessageNotContaining("nao-vazar-corpo");
    }
    @ParameterizedTest @ValueSource(ints = {201,204,302,401,403,404,429,500,503})
    void naoAceitaOutrosStatusNemRepetePost(int codigo) {
        status = codigo; resposta = "{\"detail\":\"nao-vazar-corpo\"}";
        assertThatThrownBy(() -> adapter(Duration.ofSeconds(3)).consultar(pedidos))
                .isInstanceOf(IntegracaoCotacaoException.class).hasMessageNotContaining("nao-vazar-corpo");
        assertThat(chamadas.get()).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings = {"", "{}", "null", "nao-json", "{\"moeda\":\"BRL\",\"itens\":[]}"})
    void naoAceitaRespostaIncompleta(String json) {
        resposta = json;
        assertThatThrownBy(() -> adapter(Duration.ofSeconds(3)).consultar(pedidos)).isInstanceOf(IntegracaoCotacaoException.class);
    }
    @Test void validaItensQuantidadesMoedaEPrecisaoSemArredondar() {
        for (String json : List.of(valida().replace("BRL", "USD"), valida().replace("filtro-oleo", "outro"),
                valida().replace("\"quantidade\":2", "\"quantidade\":3"),
                valida().replace("\"quantidade\":2", "\"quantidade\":2.5"),
                valida().replace("\"quantidade\":2", "\"quantidade\":2147483648"),
                valida().replace("\"quantidade\":2", "\"quantidade\":null"),
                valida().replace("\"itemId\":\"filtro-oleo\"", "\"itemId\":null"),
                valida().replace("filtro-oleo", " filtro-oleo "),
                valida().replace("45.50", "45.501"), valida().replace("45.50", "-1"),
                valida().replace("45.50", "null"), valida().replace("PECA", "INVALIDO"),
                valida().replace("Filtro de óleo", ""), valida().replace("[{", "[null,{"))) {
            resposta = json;
            assertThatThrownBy(() -> adapter(Duration.ofSeconds(3)).consultar(pedidos)).as(json).isInstanceOf(IntegracaoCotacaoException.class);
        }
    }
    @Test void timeoutENaoConfiguradoNaoChamamOutrasAPIs() {
        atraso = 400;
        assertThatThrownBy(() -> adapter(Duration.ofMillis(50)).consultar(pedidos))
                .isInstanceOfSatisfying(IntegracaoCotacaoException.class, ex -> assertThat(ex.timeout()).isTrue());
        assertThatThrownBy(() -> new ExecucaoCotacaoAdapter(WebClient.create(), new ExecucaoCotacaoProperties(false, url(), Duration.ofSeconds(1)))
                .consultar(pedidos)).isInstanceOf(CotacaoNaoConfiguradaException.class);
    }
    @Test void limitaTamanhoDaResposta() {
        resposta = "{\"extra\":\"" + "x".repeat(70000) + "\"}";
        assertThatThrownBy(() -> adapter(Duration.ofSeconds(3)).consultar(pedidos)).isInstanceOf(IntegracaoCotacaoException.class);
    }
    @ParameterizedTest @NullAndEmptySource
    @ValueSource(strings = {"url-invalida", "file:///tmp", "https://user:senha@example.com", "http://localhost/api", "http://localhost?token=abc", "http://localhost#x"})
    void exigeUrlDeServicoSemCredenciaisOuParametros(String url) {
        var properties = new ExecucaoCotacaoProperties(true, url, Duration.ofSeconds(1));
        assertThatThrownBy(properties::validar).isInstanceOf(CotacaoNaoConfiguradaException.class)
                .hasMessageNotContaining("senha").hasMessageNotContaining("token=abc");
        assertThat(properties.toString()).doesNotContain("senha", "token=abc");
    }
    @Test void validaTimeout() {
        for (Duration timeout : Arrays.asList(null, Duration.ZERO, Duration.ofSeconds(-1), Duration.ofSeconds(31))) {
            assertThatThrownBy(() -> new ExecucaoCotacaoProperties(true, url(), timeout).validar()).isInstanceOf(CotacaoNaoConfiguradaException.class);
        }
    }
    private String url() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    private ExecucaoCotacaoAdapter adapter(Duration timeout) {
        return new ExecucaoCotacaoAdapter(WebClient.builder().codecs(c -> c.defaultCodecs().maxInMemorySize(65536)).build(),
                new ExecucaoCotacaoProperties(true, url(), timeout));
    }
}
