package br.com.fiap.soat15.billingservice;

import br.com.fiap.soat15.billingservice.adapter.out.persistence.SpringDataCotacaoOrcamentoRepository;
import br.com.fiap.soat15.billingservice.application.exception.*;
import br.com.fiap.soat15.billingservice.application.port.out.*;
import br.com.fiap.soat15.billingservice.application.usecase.*;
import br.com.fiap.soat15.billingservice.domain.model.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("local")
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:cotacao-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "execucao.cotacao.enabled=true", "execucao.cotacao.timeout=3s"})
@AutoConfigureMockMvc
class CotacaoLocalIntegrationTest {
    private static final AtomicInteger CHAMADAS = new AtomicInteger();
    private static final AtomicReference<String> CORPO = new AtomicReference<>();
    private static final AtomicReference<String> RESPOSTA = new AtomicReference<>();
    private static final AtomicInteger CODIGO = new AtomicInteger(200);
    private static final AtomicBoolean VARIAR = new AtomicBoolean();
    private static final HttpServer HTTP = iniciar();
    @DynamicPropertySource static void configurar(DynamicPropertyRegistry registry) {
        registry.add("execucao.cotacao.base-url", () -> "http://127.0.0.1:" + HTTP.getAddress().getPort());
    }
    private static HttpServer iniciar() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/v1/cotacoes", exchange -> {
                int chamada = CHAMADAS.incrementAndGet();
                CORPO.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                var json = RESPOSTA.get();
                if (VARIAR.get()) json = json.replace("45.50", "" + (45 + chamada) + ".50");
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(CODIGO.get(), bytes.length);
                exchange.getResponseBody().write(bytes); exchange.close();
            }); server.start(); return server;
        } catch (Exception ex) { throw new ExceptionInInitializerError(ex); }
    }
    @AfterAll static void fechar() { HTTP.stop(0); }
    @Autowired MockMvc mvc;
    @Autowired GerarOrcamentoCotadoUseCase gerar;
    @Autowired GerarOrcamentoUseCase gerarManual;
    @Autowired OrcamentoRepository orcamentos;
    @Autowired OrcamentoCotadoRepository cotados;
    @MockitoSpyBean CotacaoGateway gateway;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean SpringDataCotacaoOrcamentoRepository metadados;
    private final UUID comando = UUID.randomUUID();
    private static final String ROTA = "/internal/orcamentos/cotados";
    private final List<ItemCotacao> itens = List.of(new ItemCotacao(TipoItem.PECA, "filtro-oleo", 2),
            new ItemCotacao(TipoItem.SERVICO, "troca-oleo", 1));

    @BeforeEach void preparar() {
        CHAMADAS.set(0); CODIGO.set(200); VARIAR.set(false);
        RESPOSTA.set("""
            {"moeda":"BRL","geradaEm":"2026-11-03T12:00:00Z","itens":[
             {"tipo":"SERVICO","itemId":"troca-oleo","nome":"Troca de óleo","quantidade":1,"precoUnitario":95.00},
             {"tipo":"PECA","itemId":"filtro-oleo","nome":"Filtro","quantidade":2,"precoUnitario":45.50}]}
            """);
    }
    @Test void consultaHttpSalvaSnapshotMetadadosERetornaMesmoOrcamentoSemRecotar() throws Exception {
        mvc.perform(post(ROTA).contentType("application/json").content(body(comando, 2, 2)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(186.00))
                .andExpect(jsonPath("$.versaoDiagnostico").value(2))
                .andExpect(jsonPath("$.cotacaoGeradaEm").value("2026-11-03T12:00:00Z"))
                .andExpect(jsonPath("$.itens[0].referencia").value("filtro-oleo"));
        var salvo = cotados.buscarPorComandoId(comando).orElseThrow();
        assertThat(CORPO.get()).doesNotContain("valorUnitario", "precoUnitario", "clienteId", "versaoDiagnostico");
        CODIGO.set(500); // Execução indisponível, mas repetição deve devolver o snapshot já salvo.
        mvc.perform(post(ROTA).contentType("application/json").content(body(comando, 2, 2)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(salvo.orcamento().id().toString()))
                .andExpect(jsonPath("$.total").value(186.00));
        assertThat(CHAMADAS.get()).isEqualTo(1);
        mvc.perform(get("/internal/orcamentos/" + salvo.orcamento().id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(186.00));
        assertThat(jdbc.queryForObject("select count(*) from orcamento_cotacoes where orcamento_id = ?", Integer.class, salvo.orcamento().id())).isEqualTo(1);
    }
    @Test void novoComandoNovaCotacaoSemAlterarAnteriorNemSimularRevisaoAutomatica() {
        var antigo = gerar.executar(command(comando, 2));
        RESPOSTA.set(RESPOSTA.get().replace("45.50", "50.50"));
        var novo = gerar.executar(command(UUID.randomUUID(), 3));
        assertThat(novo.orcamento().total()).isEqualByComparingTo("196.00");
        assertThat(novo.orcamento().id()).isNotEqualTo(antigo.orcamento().id());
        assertThat(orcamentos.buscarPorId(antigo.orcamento().id()).orElseThrow().total()).isEqualByComparingTo("186.00");
        assertThat(novo.versaoDiagnostico()).isEqualTo(3);
    }
    @Test void repeticaoToleraOrdemDosItensMasRejeitaOutrosDadosSemHttp() throws Exception {
        var salvo = gerar.executar(command(comando, 2));
        var invertidos = new ArrayList<>(itens); Collections.reverse(invertidos);
        assertThat(gerar.executar(new GerarOrcamentoCotadoCommand(comando, 1, 1, 2, invertidos)).orcamento().id())
                .isEqualTo(salvo.orcamento().id());
        for (String json : List.of(body(comando, 3, 2), body(comando, 2, 3),
                body(comando, 2, 2).replace("\"ordemId\":1", "\"ordemId\":2"),
                body(comando, 2, 2).replace("\"clienteId\":1", "\"clienteId\":2"),
                body(comando, 2, 2).replace("filtro-oleo", "outro"))) {
            mvc.perform(post(ROTA).contentType("application/json").content(json)).andExpect(status().isConflict());
        }
        assertThat(CHAMADAS.get()).isEqualTo(1);
    }
    @Test void precosEnviadosNoBodyNaoSubstituemPrecosDoExecucao() throws Exception {
        String json = body(comando, 2, 2).replace("\"itemId\":\"filtro-oleo\"", "\"itemId\":\"filtro-oleo\",\"valorUnitario\":0.01,\"precoUnitario\":0.01");
        mvc.perform(post(ROTA).contentType("application/json").content(json)).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(186.00));
    }
    @Test void catalogoInvalidoFalhaTecnicaOuRespostaDivergenteNaoSalvamNada() throws Exception {
        CODIGO.set(400);
        mvc.perform(post(ROTA).contentType("application/json").content(body(comando, 2, 2)))
                .andExpect(status().is(422));
        CODIGO.set(503);
        mvc.perform(post(ROTA).contentType("application/json").content(body(comando, 2, 2))).andExpect(status().isBadGateway());
        CODIGO.set(200); RESPOSTA.set(RESPOSTA.get().replace("filtro-oleo", "outro"));
        mvc.perform(post(ROTA).contentType("application/json").content(body(comando, 2, 2))).andExpect(status().isBadGateway());
        assertThat(orcamentos.buscarPorComandoId(comando)).isEmpty();
    }
    @Test void comandoDeOrigemManualNaoPodeSerReutilizadoComoCotado() throws Exception {
        gerarManual.executar(new GerarOrcamentoCommand(comando, 1, 1, List.of(new ItemOrcamento("filtro-oleo", TipoItem.PECA, "Manual", 2, BigDecimal.ONE))));
        mvc.perform(post(ROTA).contentType("application/json").content(body(comando, 2, 2))).andExpect(status().isConflict());
        assertThat(CHAMADAS.get()).isZero();
    }
    @Test void faltaDeConfiguracaoETimeoutTemRespostasDistintasSemSalvar() throws Exception {
        doThrow(new CotacaoNaoConfiguradaException()).when(gateway).consultar(any());
        mvc.perform(post(ROTA).contentType("application/json").content(body(comando, 2, 2))).andExpect(status().isServiceUnavailable());
        doThrow(new IntegracaoCotacaoException(true)).when(gateway).consultar(any());
        mvc.perform(post(ROTA).contentType("application/json").content(body(comando, 2, 2))).andExpect(status().isGatewayTimeout());
        assertThat(orcamentos.buscarPorComandoId(comando)).isEmpty();
        assertThat(CHAMADAS.get()).isZero();
    }
    @Test void validaEntradaAntesDeChamarHttp() throws Exception {
        for (String json : List.of("{}", "null", "{invalido}", body(comando, 0, 2), body(comando, 2, 0),
                body(comando, 2, 2).replace("\"quantidade\":2", "\"quantidade\":2.5"),
                body(comando, 2, 2).replace("\"quantidade\":2", "\"quantidade\":2147483648"),
                body(comando, 2, 2).replace("filtro-oleo", " filtro-oleo "),
                body(comando, 2, 2).replace("\"tipo\":\"SERVICO\",\"itemId\":\"troca-oleo\"", "\"tipo\":\"PECA\",\"itemId\":\"filtro-oleo\""))) {
            mvc.perform(post(ROTA).contentType("application/json").content(json)).andExpect(status().isBadRequest());
        }
        assertThat(CHAMADAS.get()).isZero();
    }
    @Test void concorrenciaComPrecosDiferentesTemUmaUnicaGravacaoVencedora() throws Exception {
        VARIAR.set(true);
        var inicio = new CountDownLatch(1);
        List<OrcamentoCotado> resultados = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<Future<OrcamentoCotado>> futuros = new ArrayList<>();
            for (int i = 0; i < 4; i++) futuros.add(executor.submit(() -> { inicio.await(); return gerar.executar(command(comando, 2)); }));
            inicio.countDown();
            for (var futuro : futuros) resultados.add(futuro.get(30, TimeUnit.SECONDS));
        }
        assertThat(resultados.stream().map(o -> o.orcamento().id()).distinct()).hasSize(1);
        assertThat(resultados.stream().map(o -> o.orcamento().total()).distinct()).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from orcamentos where comando_id = ?", Integer.class, comando)).isEqualTo(1);
    }
    @Test void falhaNosMetadadosReverteTambemOOrcamento() {
        doThrow(new org.springframework.dao.DataIntegrityViolationException("falha simulada"))
                .when(metadados).saveAndFlush(any());
        assertThatThrownBy(() -> gerar.executar(command(comando, 2))).isInstanceOf(ConflitoPersistenciaException.class);
        assertThat(orcamentos.buscarPorComandoId(comando)).isEmpty();
    }
    private GerarOrcamentoCotadoCommand command(UUID id, int versao) { return new GerarOrcamentoCotadoCommand(id, 1, 1, versao, itens); }
    private String body(UUID id, int versao, int quantidade) {
        return "{\"comandoId\":\"" + id + "\",\"ordemId\":1,\"clienteId\":1,\"versaoDiagnostico\":" + versao
                + ",\"itens\":[{\"tipo\":\"PECA\",\"itemId\":\"filtro-oleo\",\"quantidade\":" + quantidade
                + "},{\"tipo\":\"SERVICO\",\"itemId\":\"troca-oleo\",\"quantidade\":1}]}";
    }
}
