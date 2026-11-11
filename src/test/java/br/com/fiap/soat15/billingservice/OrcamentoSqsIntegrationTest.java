package br.com.fiap.soat15.billingservice;

import br.com.fiap.soat15.billingservice.application.port.out.*;
import br.com.fiap.soat15.billingservice.application.usecase.*;
import br.com.fiap.soat15.billingservice.application.exception.ComandoConflitanteException;
import br.com.fiap.soat15.billingservice.domain.model.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:orcamento-sqs-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "billing.orcamento-sqs.enabled=false",
        "mercadopago.enabled=false", "mercadopago.webhook.enabled=false"})
class OrcamentoSqsIntegrationTest {
    @Autowired RegistroMensagensOrcamento registro;
    @Autowired GerarOrcamentoUseCase gerar;
    @Autowired OrcamentoRepository orcamentos;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationContext context;
    final JsonMapper json = JsonMapper.builder().build();
    ProcessarMensagemOrcamentoUseCase processar;

    @BeforeEach void preparar() {
        jdbc.update("delete from mensagens_orcamento");
        processar = new ProcessarMensagemOrcamentoUseCase(registro, gerar, json);
    }
    @Test void geraSnapshotComRespostaPlanaCorrelacionada() {
        var c = mensagem(UUID.randomUUID(), UUID.randomUUID(), "45.50", new BigDecimal("2"), 2);
        processar.executar(json.writeValueAsString(c));
        var r = resultado(c.comandoId());
        assertThat(r.path("status").asString()).isEqualTo("AGUARDANDO_APROVACAO");
        assertThat(r.path("total").decimalValue()).isEqualByComparingTo("91.00");
        assertThat(r.path("causationId").asString()).isEqualTo(c.messageId().toString());
        assertThat(r.path("versao").asInt()).isEqualTo(2);
        assertThat(r.has("payload")).isFalse();
        assertThat(r.path("itens").get(0).path("valorUnitario").decimalValue()).isEqualByComparingTo("45.50");
        assertThat(orcamentos.buscarPorId(UUID.fromString(r.path("id").asString()))).isPresent();
    }
    @Test void redeliveryMantemRespostaEPrimeiraCorrelacao() {
        var cmd = UUID.randomUUID();
        var c = mensagem(cmd, UUID.randomUUID(), "45.50", new BigDecimal("2"), null);
        processar.executar(json.writeValueAsString(c));
        String original = registro.buscar(cmd).orElseThrow().resultado();
        processar.executar(json.writeValueAsString(mensagem(cmd, UUID.randomUUID(), "45.5", new BigDecimal("2.0"), null)));
        assertThat(registro.buscar(cmd).orElseThrow().resultado()).isEqualTo(original);
        assertThat(resultado(cmd).has("versao")).isFalse(); // Não inventa versão 1.
        assertThat(registro.pendentes()).hasSize(1);
        registro.enviado(cmd);
        processar.executar(json.writeValueAsString(c));
        assertThat(registro.pendentes()).isEmpty();
    }
    @Test void crashDepoisDoOrcamentoPermiteReprocessarSemDuplicar() {
        var c = mensagem(UUID.randomUUID(), UUID.randomUUID(), "45.50", BigDecimal.ONE, null);
        var wrapper = mock(RegistroMensagensOrcamento.class, org.mockito.AdditionalAnswers.delegatesTo(registro));
        AtomicBoolean primeira = new AtomicBoolean(true);
        doAnswer(i -> {
            if (primeira.getAndSet(false)) throw new IllegalStateException("crash simulado");
            registro.concluir(i.getArgument(0), i.getArgument(1));
            return null;
        }).when(wrapper).concluir(any(), any());
        var caso = new ProcessarMensagemOrcamentoUseCase(wrapper, gerar, json);
        assertThatIllegalStateException().isThrownBy(() -> caso.executar(json.writeValueAsString(c)));
        var id = orcamentos.buscarPorComandoId(c.comandoId()).orElseThrow().id();
        caso.executar(json.writeValueAsString(c));
        assertThat(resultado(c.comandoId()).path("id").asString()).isEqualTo(id.toString());
        assertThat(registro.pendentes()).hasSize(1);
    }
    @Test void precoAusenteGeraFalhaSemOrcamento() {
        var c = mensagem(UUID.randomUUID(), UUID.randomUUID(), null, BigDecimal.ONE, null);
        processar.executar(json.writeValueAsString(c));
        assertThat(resultado(c.comandoId()).path("status").asString()).isEqualTo("FALHA");
        assertThat(resultado(c.comandoId()).path("erro").path("codigo").asString()).isEqualTo("DADOS_ORCAMENTO_INVALIDOS");
        assertThat(orcamentos.buscarPorComandoId(c.comandoId())).isEmpty();
    }
    @Test void quantidadeFracionariaNaoETruncada() {
        var c = mensagem(UUID.randomUUID(), UUID.randomUUID(), "10", new BigDecimal("1.5"), 2);
        processar.executar(json.writeValueAsString(c));
        assertThat(resultado(c.comandoId()).path("status").asString()).isEqualTo("FALHA");
        assertThat(orcamentos.buscarPorComandoId(c.comandoId())).isEmpty();
    }
    @Test void alteracaoDoComandoNaoSobrescreveResultado() {
        var cmd = UUID.randomUUID();
        var c = mensagem(cmd, UUID.randomUUID(), "45.50", BigDecimal.ONE, null);
        processar.executar(json.writeValueAsString(c));
        String original = registro.buscar(cmd).orElseThrow().resultado();
        assertThatThrownBy(() -> processar.executar(json.writeValueAsString(
                mensagem(cmd, c.messageId(), "99", BigDecimal.ONE, null)))).isInstanceOf(ComandoConflitanteException.class);
        assertThat(registro.buscar(cmd).orElseThrow().resultado()).isEqualTo(original);
    }
    @Test void conflitoComOrcamentoLocalGeraFalhaCorrelacionada() {
        var cmd = UUID.randomUUID();
        gerar.executar(new GerarOrcamentoCommand(cmd, 123, 1,
                List.of(new ItemOrcamento("peca", TipoItem.PECA, "Peça", 1, new BigDecimal("1.00")))));
        var c = mensagem(cmd, UUID.randomUUID(), "45.50", BigDecimal.ONE, null);
        processar.executar(json.writeValueAsString(c));
        assertThat(resultado(cmd).path("erro").path("codigo").asString()).isEqualTo("COMANDO_CONFLITANTE");
    }
    @Test void indisponibilidadeDeBancoNaoViraFalhaDefinitiva() {
        var falhando = mock(GerarOrcamentoUseCase.class);
        when(falhando.executar(any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("offline"));
        var caso = new ProcessarMensagemOrcamentoUseCase(registro, falhando, json);
        var c = mensagem(UUID.randomUUID(), UUID.randomUUID(), "45", BigDecimal.ONE, null);
        assertThatThrownBy(() -> caso.executar(json.writeValueAsString(c)));
        assertThat(registro.buscar(c.comandoId()).orElseThrow().resultado()).isNull();
        assertThat(registro.pendentes()).isEmpty();
    }
    @Test void metadadosInvalidosNaoSaoInventados() {
        var c = mensagem(UUID.randomUUID(), UUID.randomUUID(), "45", BigDecimal.ONE, null);
        assertThatThrownBy(() -> processar.executar(json.writeValueAsString(c).replace("GerarOrcamento", "EstornarPagamento")));
        assertThatThrownBy(() -> processar.executar("{}"));
        assertThat(registro.pendentes()).isEmpty();
        assertThat(context.getBeansOfType(br.com.fiap.soat15.billingservice.adapter.in.sqs.OrcamentoSqsWorker.class)).isEmpty();
    }
    @Test void concorrenciaGeraUmOrcamentoEUmResultado() throws Exception {
        var c = mensagem(UUID.randomUUID(), UUID.randomUUID(), "45", BigDecimal.ONE, 2);
        String corpo = json.writeValueAsString(c);
        try (var pool = Executors.newFixedThreadPool(4)) {
            List<Callable<Boolean>> tarefas = java.util.stream.IntStream.range(0, 4).mapToObj(i ->
                    (Callable<Boolean>) () -> { try { processar.executar(corpo); return true; } catch (RuntimeException e) { return false; } }).toList();
            var resultados = pool.invokeAll(tarefas);
            assertThat(resultados.stream().map(f -> { try { return f.get(); } catch (Exception e) { return false; } })).contains(true);
        }
        processar.executar(corpo);
        assertThat(orcamentos.buscarPorComandoId(c.comandoId())).isPresent();
        assertThat(registro.pendentes()).hasSize(1);
    }
    private SolicitarOrcamentoMensagem mensagem(UUID comando, UUID mensagem, String preco, BigDecimal quantidade, Integer versao) {
        return new SolicitarOrcamentoMensagem("GerarOrcamento", mensagem, "saga-123", null, comando,
                123, 1, versao, List.of(new SolicitarOrcamentoMensagem.Item("filtro", "PECA", "Filtro", quantidade,
                preco == null ? null : new BigDecimal(preco))));
    }
    private tools.jackson.databind.JsonNode resultado(UUID cmd) {
        return json.readTree(registro.buscar(cmd).orElseThrow().resultado());
    }
}
