package br.com.fiap.soat15.billingservice;

import br.com.fiap.soat15.billingservice.application.exception.ComandoConflitanteException;
import br.com.fiap.soat15.billingservice.application.port.out.OrcamentoRepository;
import br.com.fiap.soat15.billingservice.application.usecase.*;
import br.com.fiap.soat15.billingservice.domain.model.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("local")
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:snapshot-os-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "mercadopago.enabled=false", "mercadopago.webhook.enabled=false",
        "execucao.cotacao.enabled=true"})
@AutoConfigureMockMvc
class OrcamentoSnapshotIntegrationTest {
    private static final AtomicInteger CHAMADAS = new AtomicInteger();
    private static final AtomicInteger STATUS_CATALOGO = new AtomicInteger(503);
    private static final AtomicReference<String> PRECO_CATALOGO = new AtomicReference<>("999.00");
    private static final HttpServer EXECUCAO = iniciar();
    @DynamicPropertySource static void configurar(DynamicPropertyRegistry registry) {
        // Configuração legada deixada propositalmente: não pode reativar o caminho descartado.
        registry.add("execucao.cotacao.base-url", () -> "http://127.0.0.1:" + EXECUCAO.getAddress().getPort());
    }
    private static HttpServer iniciar() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                CHAMADAS.incrementAndGet();
                byte[] bytes = ("{\"precoUnitario\":" + PRECO_CATALOGO.get() + "}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(STATUS_CATALOGO.get(), bytes.length);
                exchange.getResponseBody().write(bytes); exchange.close();
            }); server.start(); return server;
        } catch (Exception ex) { throw new ExceptionInInitializerError(ex); }
    }
    @AfterAll static void encerrar() { EXECUCAO.stop(0); }
    @BeforeEach void preparar() { CHAMADAS.set(0); STATUS_CATALOGO.set(503); PRECO_CATALOGO.set("999.00"); }
    @AfterEach void naoChamouExecucao() { assertThat(CHAMADAS.get()).isZero(); }
    @Autowired MockMvc mvc;
    @Autowired GerarOrcamentoUseCase gerar;
    @Autowired OrcamentoRepository repository;
    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate jdbc;

    @Test void geraComPrecoDoPayloadMesmoComCatalogoIndisponivel() throws Exception {
        UUID comando = UUID.randomUUID();
        mvc.perform(post("/internal/orcamentos").contentType("application/json").content(body(comando, "45.50")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(186.00))
                .andExpect(jsonPath("$.itens[0].valorUnitario").value(45.50))
                .andExpect(jsonPath("$.itens[0].descricao").value("Filtro capturado na OS"));
        var salvo = repository.buscarPorComandoId(comando).orElseThrow();
        assertThat(salvo.itens().getFirst().referencia()).isEqualTo("filtro-oleo");
        mvc.perform(get("/internal/orcamentos/" + salvo.id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(186.00));
    }
    @Test void mudancaNoCatalogoNaoRecotaNemAlteraSnapshotRecebido() throws Exception {
        UUID comando = UUID.randomUUID();
        mvc.perform(post("/internal/orcamentos").contentType("application/json").content(body(comando, "45.50"))).andExpect(status().isOk());
        var original = repository.buscarPorComandoId(comando).orElseThrow();
        PRECO_CATALOGO.set("1.00"); STATUS_CATALOGO.set(200);
        mvc.perform(post("/internal/orcamentos").contentType("application/json").content(body(comando, "45.50")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(original.id().toString()))
                .andExpect(jsonPath("$.total").value(186.00));
        // Mesmo comando não pode trocar o snapshot, ainda que o catálogo tenha mudado.
        mvc.perform(post("/internal/orcamentos").contentType("application/json").content(body(comando, "1.00")))
                .andExpect(status().isConflict());
        // Até um NOVO comando usa o preço recebido, não o catálogo atual de 1.00.
        UUID novo = UUID.randomUUID();
        mvc.perform(post("/internal/orcamentos").contentType("application/json").content(body(novo, "45.50")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(186.00));
        assertThat(repository.buscarPorComandoId(novo).orElseThrow().id()).isNotEqualTo(original.id());
    }
    @Test void novoSnapshotExigeNovoComandoEPreservaAnterior() throws Exception {
        UUID antigo = UUID.randomUUID(), novo = UUID.randomUUID();
        mvc.perform(post("/internal/orcamentos").contentType("application/json").content(body(antigo, "45.50"))).andExpect(status().isOk());
        mvc.perform(post("/internal/orcamentos").contentType("application/json").content(body(novo, "50.50")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(196.00));
        assertThat(repository.buscarPorComandoId(antigo).orElseThrow().total()).isEqualByComparingTo("186.00");
    }
    @Test void payloadSemPrecoOuComPrecisaoNegativoQuantidadeInvalidaNaoPersiste() throws Exception {
        for (String preco : List.of("null", "-1.00", "45.501", "100000000000000000.00")) {
            UUID comando = UUID.randomUUID();
            mvc.perform(post("/internal/orcamentos").contentType("application/json").content(body(comando, preco))).andExpect(status().isBadRequest());
            assertThat(repository.buscarPorComandoId(comando)).isEmpty();
        }
        UUID comando = UUID.randomUUID();
        String base = body(comando, "45.50");
        for (String json : List.of(base.replace("\"valorUnitario\":45.50", "\"outroCampo\":45.50"),
                base.replace("\"quantidade\":2", "\"quantidade\":2.5"),
                base.replace("\"quantidade\":2", "\"quantidade\":0"),
                base.replace("\"quantidade\":2", "\"quantidade\":2147483648"),
                base.replace("\"referencia\":\"troca-oleo\",\"tipo\":\"SERVICO\"", "\"referencia\":\"filtro-oleo\",\"tipo\":\"PECA\""))) {
            mvc.perform(post("/internal/orcamentos").contentType("application/json").content(json)).andExpect(status().isBadRequest());
        }
        assertThat(repository.buscarPorComandoId(comando)).isEmpty();
    }
    @Test void concorrenciaComSnapshotsDiferentesNaoMisturaValores() throws Exception {
        UUID id = UUID.randomUUID();
        var inicio = new CountDownLatch(1);
        List<String> resultados = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<String>> futuros = new ArrayList<>();
            for (String preco : List.of("45.50", "50.50")) futuros.add(executor.submit(() -> {
                inicio.await();
                try { return gerar.executar(command(id, preco)).id().toString(); }
                catch (ComandoConflitanteException ex) { return "CONFLITO"; }
            }));
            inicio.countDown(); for (var futuro : futuros) resultados.add(futuro.get(20, TimeUnit.SECONDS));
        }
        assertThat(resultados.stream().filter("CONFLITO"::equals).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from orcamentos where comando_id = ?", Integer.class, id)).isEqualTo(1);
        var salvo = repository.buscarPorComandoId(id).orElseThrow();
        assertThat(salvo.total()).isIn(new BigDecimal("186.00"), new BigDecimal("196.00"));
    }
    @Test void registroHistoricoContinuaConsultavelSemUsarMetadadosDeCotacao() throws Exception {
        var salvo = gerar.executar(command(UUID.randomUUID(), "45.50"));
        jdbc.execute("create table if not exists orcamento_cotacoes (orcamento_id UUID primary key, versao_diagnostico integer, cotacao_gerada_em timestamp with time zone)");
        jdbc.update("insert into orcamento_cotacoes values (?, ?, current_timestamp)", salvo.id(), 2);
        mvc.perform(get("/internal/orcamentos/" + salvo.id())).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(186.00));
        assertThat(gerar.executar(command(salvo.comandoId(), "45.50")).id()).isEqualTo(salvo.id());
        assertThat(jdbc.queryForObject("select versao_diagnostico from orcamento_cotacoes where orcamento_id = ?", Integer.class, salvo.id())).isEqualTo(2);
    }
    @Test void arquiteturaNaoTemMaisGatewayOuRotaDeCotacao() throws Exception {
        assertThat(context.containsBean("cotacaoGateway")).isFalse();
        assertThat(context.containsBean("gerarOrcamentoCotadoUseCase")).isFalse();
        assertThat(GerarOrcamentoUseCase.class.getConstructors()).hasSize(1);
        assertThat(GerarOrcamentoUseCase.class.getConstructors()[0].getParameterTypes()).containsExactly(OrcamentoRepository.class, Clock.class);
        mvc.perform(post("/internal/orcamentos/cotados").contentType("application/json").content("{}"))
                .andExpect(status().is4xxClientError());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/internal/orcamentos/cotados']").doesNotExist());
    }
    @Test void snapshotSalvoNaoCompartilhaListaMutavelDoChamador() {
        var itens = new ArrayList<>(command(UUID.randomUUID(), "45.50").itens());
        var comando = new GerarOrcamentoCommand(UUID.randomUUID(), 123, 1, itens);
        var salvo = gerar.executar(comando); itens.clear();
        assertThat(salvo.itens()).hasSize(2);
        assertThat(repository.buscarPorComandoId(comando.comandoId()).orElseThrow().total()).isEqualByComparingTo("186.00");
    }
    private GerarOrcamentoCommand command(UUID id, String preco) {
        return new GerarOrcamentoCommand(id, 123, 1, List.of(
                new ItemOrcamento("filtro-oleo", TipoItem.PECA, "Filtro capturado na OS", 2, new BigDecimal(preco)),
                new ItemOrcamento("troca-oleo", TipoItem.SERVICO, "Troca de óleo", 1, new BigDecimal("95.00"))));
    }
    private String body(UUID id, String preco) {
        return "{\"comandoId\":\"" + id + "\",\"ordemId\":123,\"clienteId\":1,\"itens\":["
                + "{\"referencia\":\"filtro-oleo\",\"tipo\":\"PECA\",\"descricao\":\"Filtro capturado na OS\",\"quantidade\":2,\"valorUnitario\":" + preco + "},"
                + "{\"referencia\":\"troca-oleo\",\"tipo\":\"SERVICO\",\"descricao\":\"Troca de óleo\",\"quantidade\":1,\"valorUnitario\":95.00}]}";
    }
}
