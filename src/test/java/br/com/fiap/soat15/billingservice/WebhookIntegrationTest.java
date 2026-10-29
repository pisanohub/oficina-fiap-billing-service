package br.com.fiap.soat15.billingservice;

import br.com.fiap.soat15.billingservice.adapter.in.scheduler.WebhookWorker;
import br.com.fiap.soat15.billingservice.adapter.in.web.MercadoPagoWebhookAssinaturaTest;
import br.com.fiap.soat15.billingservice.application.exception.IntegracaoPagamentoException;
import br.com.fiap.soat15.billingservice.application.port.out.*;
import br.com.fiap.soat15.billingservice.application.usecase.*;
import br.com.fiap.soat15.billingservice.domain.model.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("webhook-demo")
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:webhooks-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
        "mercadopago.enabled=true", "mercadopago.access-token=token-ficticio-somente-testes",
        "mercadopago.webhook.enabled=true", "mercadopago.webhook.secret=0123456789abcdef0123456789abcdef",
        "mercadopago.webhook.initial-delay=3600000", "mercadopago.webhook.poll-delay=3600000"})
@AutoConfigureMockMvc
class WebhookIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired GerarOrcamentoUseCase gerar;
    @Autowired PreferenciaCheckoutRepository preferencias;
    @Autowired PagamentoRepository pagamentos;
    @MockitoSpyBean WebhookInbox inbox;
    @Autowired WebhookWorker worker;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PagamentoGateway gateway;
    @MockitoBean Clock clock;
    private static final AtomicLong SEQUENCIA = new AtomicLong(800000);
    private final String pagamentoId = "" + SEQUENCIA.incrementAndGet();
    private final AtomicReference<Instant> agora = new AtomicReference<>(Instant.parse("2026-10-29T12:00:00Z"));
    private String requestId;

    @BeforeEach void preparar() {
        when(clock.instant()).thenAnswer(invocation -> agora.get());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        jdbc.update("delete from mercadopago_webhook_inbox");
        requestId = UUID.randomUUID().toString();
    }
    @Test void recebeSemConsultarProvedorDepoisProcessaERepeteSemDuplicar() throws Exception {
        var orcamento = orcamento(true);
        when(gateway.consultar(pagamentoId)).thenReturn(dados(orcamento, "approved", orcamento.total(), "BRL"));
        for (int i = 0; i < 2; i++) enviar(body(pagamentoId)).andExpect(status().isOk()).andExpect(content().string(""));
        verifyNoInteractions(gateway);
        assertThat(contagem()).isEqualTo(1);
        assertThat(pagamentos.listarPorOrcamento(orcamento.id())).isEmpty();
        worker.executar();
        assertThat(estado()).isEqualTo("PROCESSADO");
        assertThat(pagamentos.listarPorOrcamento(orcamento.id())).singleElement().satisfies(p -> assertThat(p.confirmado()).isTrue());
        enviar(body(pagamentoId)).andExpect(status().isOk());
        worker.executar();
        verify(gateway, times(1)).consultar(pagamentoId);
        // A rota pública do webhook existe sem habilitar as entradas internas locais.
        mvc.perform(get("/internal/orcamentos/" + orcamento.id() + "/pagamentos")).andExpect(status().isNotFound());
    }
    @Test void novasEntregasConsultamEstadoAtualSemConfiarNoStatusDoCorpo() throws Exception {
        var orcamento = orcamento(true);
        when(gateway.consultar(pagamentoId)).thenReturn(dados(orcamento, "pending", orcamento.total(), "BRL"),
                new PagamentoConsultado(pagamentoId, orcamento.id().toString(), "approved", "accredited",
                        orcamento.total(), "BRL", BigDecimal.ZERO, agora.get().plusSeconds(1)));
        enviar(body(pagamentoId)).andExpect(status().isOk()); worker.executar();
        assertThat(pagamentos.listarPorOrcamento(orcamento.id()).getFirst().confirmado()).isFalse();
        requestId = UUID.randomUUID().toString();
        enviar(body(pagamentoId)).andExpect(status().isOk()); worker.executar();
        assertThat(pagamentos.listarPorOrcamento(orcamento.id())).hasSize(1);
        assertThat(pagamentos.listarPorOrcamento(orcamento.id()).getFirst().confirmado()).isTrue();
    }
    @Test void rejeitaHeaderAusenteAssinaturaFalsaOuParametrosDuplicadosSemConsultar() throws Exception {
        mvc.perform(post(rota()).contentType("application/json").content(body(pagamentoId))).andExpect(status().isUnauthorized());
        mvc.perform(post(rota()).header("x-request-id", requestId).header("x-signature", "ts=1,v1=abc")
                .contentType("application/json").content(body(pagamentoId))).andExpect(status().isUnauthorized());
        mvc.perform(post("/webhooks/mercadopago").param("data.id", pagamentoId, "999")
                .header("x-request-id", requestId).header("x-signature", assinatura())
                .contentType("application/json").content(body(pagamentoId))).andExpect(status().isUnauthorized());
        mvc.perform(post(rota()).header("x-request-id", requestId, "outro").header("x-signature", assinatura())
                .contentType("application/json").content(body(pagamentoId))).andExpect(status().isUnauthorized());
        mvc.perform(post(rota()).header("x-request-id", requestId).header("x-signature", assinatura(), assinatura())
                .contentType("application/json").content(body(pagamentoId))).andExpect(status().isUnauthorized());
        mvc.perform(post("/webhooks/mercadopago").header("x-request-id", requestId).header("x-signature", assinatura())
                .contentType("application/json").content(body(pagamentoId))).andExpect(status().isUnauthorized());
        assertThat(contagem()).isZero(); verifyNoInteractions(gateway);
    }
    @Test void corpoNaoPodeTrocarIdOuTipoNemInformarPagamentoAprovado() throws Exception {
        enviar(body("999")).andExpect(status().isBadRequest());
        enviar("{\"type\":\"merchant_order\",\"data\":{\"id\":\"" + pagamentoId + "\"}}")
                .andExpect(status().isBadRequest());
        enviar("{}").andExpect(status().isBadRequest());
        enviar("{\"type\":\"payment\"}").andExpect(status().isBadRequest());
        enviar("null").andExpect(status().isBadRequest());
        enviar("{invalido}").andExpect(status().isBadRequest());
        assertThat(contagem()).isZero(); verifyNoInteractions(gateway);
    }
    @Test void indisponibilidadeDaInboxNaoRetornaSucessoNemConsultaProvedor() throws Exception {
        doThrow(new org.springframework.dao.TransientDataAccessResourceException("Mensagem interna não deve sair"))
                .when(inbox).receber(anyString(), anyString(), any());
        enviar(body(pagamentoId)).andExpect(status().isServiceUnavailable()).andExpect(content().string(""));
        assertThat(contagem()).isZero(); verifyNoInteractions(gateway);
    }
    @Test void perfilDeDemonstracaoNaoExpoeSwaggerOuRotasLocais() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
        mvc.perform(post("/internal/orcamentos").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
    }
    @Test void falhaTemporariaReagendaDepoisDaConfirmacaoDuravelDoRecebimento() throws Exception {
        var orcamento = orcamento(true);
        when(gateway.consultar(pagamentoId)).thenThrow(new IntegracaoPagamentoException("Não persistir esta mensagem", false, true))
                .thenReturn(dados(orcamento, "approved", orcamento.total(), "BRL"));
        enviar(body(pagamentoId)).andExpect(status().isOk()); worker.executar();
        assertThat(estado()).isEqualTo("PENDENTE");
        assertThat(pagamentos.listarPorOrcamento(orcamento.id())).isEmpty();
        worker.executar(); verify(gateway, times(1)).consultar(pagamentoId);
        agora.updateAndGet(i -> i.plusSeconds(61)); worker.executar();
        assertThat(estado()).isEqualTo("PROCESSADO");
        assertThat(pagamentos.listarPorOrcamento(orcamento.id()).getFirst().confirmado()).isTrue();
    }
    @Test void valorMoedaReferenciaOuPreferenciaIncompativelVaiParaFalhaSemConfirmacao() throws Exception {
        var orcamento = orcamento(true);
        var semPreferencia = orcamento(false);
        var respostas = List.of(dados(orcamento, "approved", BigDecimal.ONE, "BRL"),
                dados(orcamento, "approved", orcamento.total(), "USD"),
                dados(semPreferencia, "approved", semPreferencia.total(), "BRL"),
                new PagamentoConsultado(pagamentoId, "referencia-externa", "approved", null, BigDecimal.TEN, "BRL", BigDecimal.ZERO, agora.get()),
                new PagamentoConsultado(pagamentoId, UUID.randomUUID().toString(), "approved", null, BigDecimal.TEN, "BRL", BigDecimal.ZERO, agora.get()));
        for (var resposta : respostas) {
            jdbc.update("delete from mercadopago_webhook_inbox");
            when(gateway.consultar(pagamentoId)).thenReturn(resposta);
            enviar(body(pagamentoId)).andExpect(status().isOk()); worker.executar();
            assertThat(estado()).isEqualTo("FALHA");
            assertThat(pagamentos.listarPorOrcamento(orcamento.id())).isEmpty();
            assertThat(pagamentos.listarPorOrcamento(semPreferencia.id())).isEmpty();
        }
    }
    @Test void naoAceitaOperacaoDiferenteEParaAposOitoTentativas() throws Exception {
        var orcamento = orcamento(true);
        when(gateway.consultar(pagamentoId)).thenReturn(new PagamentoConsultado("9999999", orcamento.id().toString(), "approved", null,
                orcamento.total(), "BRL", BigDecimal.ZERO, agora.get()));
        enviar(body(pagamentoId)).andExpect(status().isOk());
        for (int i = 0; i < 8; i++) { worker.executar(); agora.updateAndGet(t -> t.plusSeconds(901)); }
        assertThat(estado()).isEqualTo("FALHA");
        worker.executar(); verify(gateway, times(8)).consultar(pagamentoId);
        assertThat(pagamentos.listarPorOrcamento(orcamento.id())).isEmpty();
    }
    @Test void reservaExpiraEWorkerAntigoNaoPodeConcluirOutraReserva() {
        String id = "a".repeat(64);
        inbox.receber(id, pagamentoId, agora.get());
        var antiga = inbox.reservar(id, agora.get()).orElseThrow();
        assertThat(inbox.reservar(id, agora.get())).isEmpty();
        agora.updateAndGet(t -> t.plusSeconds(61));
        var nova = inbox.reservar(id, agora.get()).orElseThrow();
        inbox.concluir(antiga, agora.get());
        inbox.falhar(antiga, agora.get(), true, "INVALIDO");
        assertThat(estado()).isEqualTo("PROCESSANDO");
        inbox.concluir(nova, agora.get());
        assertThat(estado()).isEqualTo("PROCESSADO");
        assertThat(inbox.reservar(id, agora.get())).isEmpty();
        assertThat(inbox.reservar("nao-existe", agora.get())).isEmpty();
    }
    @Test void oitoQuedasDuranteProcessamentoNaoGeramLoopInfinito() {
        String id = "b".repeat(64); inbox.receber(id, pagamentoId, agora.get());
        for (int i = 0; i < 8; i++) {
            assertThat(inbox.reservar(id, agora.get())).isPresent();
            agora.updateAndGet(t -> t.plusSeconds(61));
        }
        assertThat(inbox.reservar(id, agora.get())).isEmpty();
        assertThat(estado()).isEqualTo("FALHA");
    }
    @Test void entregasEWorkersConcorrentesNaoDuplicamNemReservamDuasVezes() throws Exception {
        var iniciar = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var futuros = new ArrayList<Future<?>>();
            for (int i = 0; i < 4; i++) futuros.add(executor.submit(() -> {
                iniciar.await(); enviar(body(pagamentoId)).andExpect(status().isOk()); return null;
            }));
            iniciar.countDown(); for (var futuro : futuros) futuro.get(20, TimeUnit.SECONDS);
            assertThat(contagem()).isEqualTo(1);
            String id = inbox.pendentes(agora.get()).getFirst();
            var reservas = new AtomicInteger(); futuros.clear();
            for (int i = 0; i < 4; i++) futuros.add(executor.submit(() -> {
                if (inbox.reservar(id, agora.get()).isPresent()) reservas.incrementAndGet();
            }));
            for (var futuro : futuros) futuro.get(20, TimeUnit.SECONDS);
            assertThat(reservas.get()).isEqualTo(1);
        }
    }
    private org.springframework.test.web.servlet.ResultActions enviar(String body) throws Exception {
        return mvc.perform(post(rota()).header("x-request-id", requestId).header("x-signature", assinatura())
                .contentType("application/json").content(body));
    }
    private String rota() { return "/webhooks/mercadopago?data.id=" + pagamentoId; }
    private String assinatura() { return MercadoPagoWebhookAssinaturaTest.assinar(pagamentoId, requestId, "" + agora.get().getEpochSecond()); }
    private String body(String id) { return "{\"type\":\"payment\",\"status\":\"approved\",\"data\":{\"id\":\"" + id + "\"}}"; }
    private long contagem() { return jdbc.queryForObject("select count(*) from mercadopago_webhook_inbox", Long.class); }
    private String estado() { return jdbc.queryForObject("select estado from mercadopago_webhook_inbox", String.class); }
    private Orcamento orcamento(boolean comPreferencia) {
        var orcamento = gerar.executar(new GerarOrcamentoCommand(UUID.randomUUID(), 1, 1,
                List.of(new ItemOrcamento("s1", TipoItem.SERVICO, "Revisão", 1, new BigDecimal("199.80")))));
        if (comPreferencia) {
            preferencias.tentarIniciar(orcamento.id(), agora.get());
            preferencias.concluir(orcamento.id(), new PreferenciaGerada("pref-" + orcamento.id(), "https://www.mercadopago.com.br/checkout", null), agora.get());
        }
        return orcamento;
    }
    private PagamentoConsultado dados(Orcamento o, String status, BigDecimal valor, String moeda) {
        return new PagamentoConsultado(pagamentoId, o.id().toString(), status, "accredited", valor, moeda, BigDecimal.ZERO, agora.get());
    }
}
