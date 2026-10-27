package br.com.fiap.soat15.billingservice;

import br.com.fiap.soat15.billingservice.application.exception.*;
import br.com.fiap.soat15.billingservice.application.port.out.*;
import br.com.fiap.soat15.billingservice.application.usecase.*;
import br.com.fiap.soat15.billingservice.domain.model.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("local")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:preferencias-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
class PreferenciaLocalIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired GerarOrcamentoUseCase gerar;
    @Autowired CriarPreferenciaUseCase criar;
    @Autowired ConsultarOrcamentoUseCase consultar;
    @Autowired PreferenciaCheckoutRepository repository;
    @MockitoBean PreferenciaPagamentoGateway gateway; // Sem conexão com a conta real nos testes.

    private final PreferenciaGerada resultado = new PreferenciaGerada("p1", "https://www.mercadopago.com.br/checkout/p1", null);

    @BeforeEach
    void configurar() { when(gateway.criar(any())).thenReturn(resultado); }

    @Test
    void criaSalvaConsultaEReutilizaLinkSemAlterarOrcamento() throws Exception {
        var orcamento = orcamento("199.80");
        mvc.perform(post(rota(orcamento.id())).contentType("application/json").content("{\"total\":0.01}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.preferenciaId").value("p1"))
                .andExpect(jsonPath("$.estado").value("CRIADA"));
        mvc.perform(get(rota(orcamento.id()))).andExpect(status().isOk()).andExpect(jsonPath("$.checkoutUrl").value(resultado.checkoutUrl()));
        mvc.perform(post(rota(orcamento.id()))).andExpect(status().isOk()).andExpect(jsonPath("$.preferenciaId").value("p1"));
        verify(gateway, times(1)).criar(argThat(o -> o.total().compareTo(new BigDecimal("199.80")) == 0));
        assertThat(consultar.executar(orcamento.id()).status()).isEqualTo("AGUARDANDO_APROVACAO");
        assertThat(repository.tentarIniciar(orcamento.id(), Instant.now())).isFalse();
        assertThatThrownBy(() -> repository.concluir(orcamento.id(), resultado, Instant.now())).hasRootCauseInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> repository.registrarFalha(UUID.randomUUID(), true, Instant.now())).hasRootCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void falhaDefinitivaPermiteNovaTentativaExplicita() throws Exception {
        var orcamento = orcamento("10");
        when(gateway.criar(any())).thenThrow(new IntegracaoPagamentoException("Credencial recusada.", false, false)).thenReturn(resultado);
        mvc.perform(post(rota(orcamento.id()))).andExpect(status().isBadGateway()).andExpect(jsonPath("$.resultadoIncerto").value(false));
        mvc.perform(get(rota(orcamento.id()))).andExpect(jsonPath("$.estado").value("FALHA_DEFINITIVA"));
        mvc.perform(post(rota(orcamento.id()))).andExpect(status().isOk());
        verify(gateway, times(2)).criar(any());
    }

    @Test
    void timeoutFicaPersistidoENaoRepeteMesmoAposNovaChamada() throws Exception {
        var orcamento = orcamento("10");
        when(gateway.criar(any())).thenThrow(new IntegracaoPagamentoException("Timeout.", true, true));
        mvc.perform(post(rota(orcamento.id()))).andExpect(status().isGatewayTimeout()).andExpect(jsonPath("$.resultadoIncerto").value(true));
        mvc.perform(get(rota(orcamento.id()))).andExpect(jsonPath("$.estado").value("RESULTADO_INCERTO"));
        mvc.perform(post(rota(orcamento.id()))).andExpect(status().isConflict());
        verify(gateway, times(1)).criar(any());
    }

    @Test
    void faltaConfiguracaoNaoReservaOperacao() throws Exception {
        var orcamento = orcamento("10");
        doThrow(new PagamentoNaoConfiguradoException()).when(gateway).validarConfiguracao();
        mvc.perform(post(rota(orcamento.id()))).andExpect(status().isServiceUnavailable());
        assertThat(repository.buscar(orcamento.id())).isEmpty();
        verify(gateway, never()).criar(any());
    }

    @Test
    void validaOrcamentoAntesDeChamarProvedor() throws Exception {
        mvc.perform(post(rota(UUID.randomUUID()))).andExpect(status().isNotFound());
        mvc.perform(get(rota(UUID.randomUUID()))).andExpect(status().isNotFound());
        mvc.perform(post("/internal/orcamentos/invalido/preferencia")).andExpect(status().isBadRequest());
        mvc.perform(post(rota(orcamento("0").id()))).andExpect(status().isBadRequest());
        verify(gateway, never()).criar(any());
    }

    @Test
    void reservaPersistidaSobreviveANovaInstanciaDoCasoDeUso() {
        var orcamento = orcamento("10");
        assertThat(repository.tentarIniciar(orcamento.id(), Instant.now())).isTrue();
        assertThat(repository.tentarIniciar(orcamento.id(), Instant.now())).isFalse();
        assertThatThrownBy(() -> criar.executar(orcamento.id())).isInstanceOf(PreferenciaBloqueadaException.class);
        verify(gateway, never()).criar(any());
    }

    @Test
    void concorrenciaNaoCriaDuasPreferenciasExternas() throws Exception {
        var orcamento = orcamento("10");
        var iniciar = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<Future<String>> futuros = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futuros.add(executor.submit(() -> {
                    iniciar.await();
                    try { return criar.executar(orcamento.id()).preferenciaId(); }
                    catch (PreferenciaBloqueadaException ex) { return "EM_ANDAMENTO"; }
                }));
            }
            iniciar.countDown();
            for (var futuro : futuros) assertThat(futuro.get(30, TimeUnit.SECONDS)).isIn("p1", "EM_ANDAMENTO");
            verify(gateway, times(1)).criar(any());
            assertThat(repository.buscar(orcamento.id()).orElseThrow().estado()).isEqualTo(EstadoPreferencia.CRIADA);
        }
    }

    private String rota(UUID id) { return "/internal/orcamentos/" + id + "/preferencia"; }
    private Orcamento orcamento(String valor) {
        return gerar.executar(new GerarOrcamentoCommand(UUID.randomUUID(), 1, 1,
                List.of(new ItemOrcamento("s1", TipoItem.SERVICO, "Serviço", 1, new BigDecimal(valor)))));
    }
}
