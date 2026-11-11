package br.com.fiap.soat15.billingservice;

import br.com.fiap.soat15.billingservice.adapter.out.persistence.RegistroFinanceiro;
import br.com.fiap.soat15.billingservice.application.usecase.ComandoPagamento;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import br.com.fiap.soat15.billingservice.application.usecase.*;
import br.com.fiap.soat15.billingservice.application.port.out.*;
import br.com.fiap.soat15.billingservice.domain.model.*;
import java.math.BigDecimal;
import java.time.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:financeiro-saga;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
        "billing.sqs.enabled=false", "mercadopago.enabled=false", "mercadopago.webhook.enabled=false"})
class RegistroFinanceiroIntegrationTest {
    @Autowired RegistroFinanceiro registro;
    @Autowired JdbcTemplate jdbc;
    @Autowired GerarOrcamentoUseCase gerar;
    @Autowired ConsultarOrcamentoUseCase consultar;
    @Autowired PagamentoRepository pagamentos;
    final JsonMapper json = JsonMapper.builder().build();
    @BeforeEach void limpar() {
        jdbc.update("delete from eventos_financeiros");
        jdbc.update("delete from comandos_financeiros");
    }
    @Test void reservaSobreviveAReleituraEGuardaCorrelacaoOriginal() {
        var c = comando(UUID.randomUUID(), UUID.randomUUID(), 2);
        registro.reservar(c, json.writeValueAsString(c));
        assertThat(registro.estado(c)).contains("PROCESSANDO");
        var novoTransporte = new ComandoPagamento(c.type(), UUID.randomUUID(), c.sagaId(), null,
                c.comandoId(), c.ordemId(), c.clienteId(), c.orcamentoId(), c.versao(), null);
        assertThat(registro.estado(novoTransporte)).contains("PROCESSANDO");
        assertThat(registro.checkout(c.orcamentoId(), json)).contains(c);
    }
    @Test void outroComandoNaoReservaMesmoCheckout() {
        var id = UUID.randomUUID();
        var c = comando(UUID.randomUUID(), id, 2);
        registro.reservar(c, json.writeValueAsString(c));
        var concorrente = comando(UUID.randomUUID(), id, 2);
        assertThatThrownBy(() -> registro.reservar(concorrente, json.writeValueAsString(concorrente)));
        assertThat(jdbc.queryForObject("select count(*) from comandos_financeiros", Integer.class)).isEqualTo(1);
    }
    @Test void mesmaChaveComVersaoAlteradaEConflito() {
        var c = comando(UUID.randomUUID(), UUID.randomUUID(), 2);
        registro.reservar(c, json.writeValueAsString(c));
        var alterado = comando(c.comandoId(), c.orcamentoId(), 3);
        assertThatIllegalArgumentException().isThrownBy(() -> registro.estado(alterado));
    }
    @Test void outboxPersisteAntesDeMarcarEnviado() {
        var c = comando(UUID.randomUUID(), UUID.randomUUID(), 2);
        registro.reservar(c, json.writeValueAsString(c));
        var evento = UUID.randomUUID();
        registro.concluir(c, evento, "{\"status\":\"CHECKOUT_CRIADO\"}", "CHECKOUT_CRIADO");
        assertThat(registro.aguardando()).containsExactly(json.writeValueAsString(c));
        assertThat(registro.pendentes()).hasSize(1);
        assertThat(registro.pendentes().getFirst().id()).isEqualTo(evento);
        registro.enviado(evento);
        assertThat(registro.pendentes()).isEmpty();
    }
    @Test void falhaNaOutboxReverteConclusaoDoComando() {
        var c = comando(UUID.randomUUID(), UUID.randomUUID(), 2);
        registro.reservar(c, json.writeValueAsString(c));
        assertThatThrownBy(() -> registro.concluir(c, UUID.randomUUID(), null, "CONCLUIDO"));
        assertThat(registro.estado(c)).contains("PROCESSANDO");
        assertThat(registro.pendentes()).isEmpty();
        registro.incerto(c.comandoId());
        assertThat(registro.estado(c)).contains("INCERTO");
    }

    @Test void fluxoFinanceiroCompletoComBancoRealDeTesteEProvedorSimulado() {
        var o = gerar.executar(new GerarOrcamentoCommand(UUID.randomUUID(), 123, 1,
                List.of(new ItemOrcamento("peca", TipoItem.PECA, "Peça", 1, new BigDecimal("100.00")))));
        var checkout = mock(CriarPreferenciaUseCase.class);
        when(checkout.executar(o.id())).thenReturn(new PreferenciaCheckout(o.id(), EstadoPreferencia.CRIADA,
                "pref", "https://www.mercadopago.com.br/checkout/test", null, Instant.EPOCH));
        var gateway = mock(PagamentoGateway.class);
        var estornos = mock(EstornoGateway.class);
        var usecase = new ProcessarComandoPagamentoUseCase(registro, consultar, checkout, gateway,
                pagamentos, estornos, json, Clock.systemUTC());
        var pagar = comando(UUID.randomUUID(), o.id(), 2);
        String corpo = json.writeValueAsString(pagar);
        usecase.executar(corpo);
        usecase.executar(corpo); // Redelivery não cria outra preferência.
        verify(checkout, times(1)).executar(o.id());
        var aprovado = new PagamentoConsultado("98765", o.id().toString(), "approved", null,
                new BigDecimal("100.00"), "BRL", BigDecimal.ZERO, Instant.EPOCH);
        pagamentos.registrar(new Pagamento(o.id(), aprovado, Instant.EPOCH));
        usecase.confirmarRegistrados();
        usecase.confirmarRegistrados(); // Sem outro evento para o mesmo sucesso.
        assertThat(registro.pendentes()).hasSize(2);

        var refund = new PagamentoConsultado("98765", o.id().toString(), "refunded", null,
                new BigDecimal("100.00"), "BRL", new BigDecimal("100.00"), Instant.EPOCH.plusSeconds(1));
        when(gateway.consultar("98765")).thenReturn(aprovado, refund);
        var estornar = new ComandoPagamento(ComandoPagamento.Tipo.EstornarPagamento, UUID.randomUUID(),
                pagar.sagaId(), null, UUID.randomUUID(), 123, 1, o.id(), 2, "98765");
        usecase.executar(json.writeValueAsString(estornar));
        usecase.executar(json.writeValueAsString(estornar));
        verify(estornos, times(1)).estornarIntegral("98765", estornar.comandoId());
        assertThat(registro.pendentes()).hasSize(3);
        assertThat(registro.estado(estornar)).contains("CONCLUIDO");
        assertThat(pagamentos.listarPorOrcamento(o.id()).getFirst().dados().estado())
                .isEqualTo(EstadoPagamento.ESTORNADO);
    }
    private ComandoPagamento comando(UUID comando, UUID orcamento, int versao) {
        return new ComandoPagamento(ComandoPagamento.Tipo.ProcessarPagamento, UUID.randomUUID(),
                "saga-123", null, comando, 123, 1, orcamento, versao, null);
    }
}
