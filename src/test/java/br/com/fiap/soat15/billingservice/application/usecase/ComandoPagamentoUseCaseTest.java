package br.com.fiap.soat15.billingservice.application.usecase;

import br.com.fiap.soat15.billingservice.application.port.out.*;
import br.com.fiap.soat15.billingservice.application.exception.OrcamentoNaoEncontradoException;
import br.com.fiap.soat15.billingservice.domain.model.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ComandoPagamentoUseCaseTest {
    final JsonMapper json = JsonMapper.builder().disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).build();
    final RegistroComandosFinanceiros registro = mock(RegistroComandosFinanceiros.class);
    final ConsultarOrcamentoUseCase consultar = mock(ConsultarOrcamentoUseCase.class);
    final CriarPreferenciaUseCase checkout = mock(CriarPreferenciaUseCase.class);
    final PagamentoGateway pagamentos = mock(PagamentoGateway.class);
    final PagamentoRepository salvos = mock(PagamentoRepository.class);
    final EstornoGateway estornos = mock(EstornoGateway.class);
    final Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
    final UUID id = UUID.randomUUID();
    final Orcamento o = new Orcamento(id, UUID.randomUUID(), 123, 1,
            List.of(new ItemOrcamento("oleo", TipoItem.PECA, "Óleo", 2, new BigDecimal("50.00"))), Instant.EPOCH);
    ProcessarComandoPagamentoUseCase usecase;
    ComandoPagamento pagar;
    ComandoPagamento estornar;

    @BeforeEach void preparar() {
        pagar = comando(ComandoPagamento.Tipo.ProcessarPagamento, null, 2);
        estornar = comando(ComandoPagamento.Tipo.EstornarPagamento, "12345", 2);
        when(registro.estado(any())).thenReturn(Optional.empty());
        when(consultar.executar(id)).thenReturn(o);
        when(registro.checkout(id, json)).thenReturn(Optional.of(pagar));
        when(checkout.executar(id)).thenReturn(new PreferenciaCheckout(id, EstadoPreferencia.CRIADA,
                "pref", "https://www.mercadopago.com.br/checkout/test", null, Instant.EPOCH));
        when(salvos.listarPorOrcamento(id)).thenReturn(List.of(new Pagamento(id, dados("approved", "0.00"), Instant.EPOCH)));
        usecase = new ProcessarComandoPagamentoUseCase(registro, consultar, checkout, pagamentos, salvos, estornos, json, clock);
    }

    @Test void processarCriaLinkMasNaoConfirmaNemEstorna() {
        executar(pagar);
        verify(checkout).executar(id);
        verifyNoInteractions(estornos, pagamentos);
        var r = resposta();
        assertThat(r.path("status").asString()).isEqualTo("CHECKOUT_CRIADO");
        assertThat(r.path("versao").asInt()).isEqualTo(2);
    }
    @Test void estornarUsaPostUmaVezEConfirmaPorGet() {
        when(pagamentos.consultar("12345")).thenReturn(dados("approved", "0.00"), dados("refunded", "100.00"));
        executar(estornar);
        verify(estornos).estornarIntegral("12345", estornar.comandoId());
        assertThat(resposta().path("status").asString()).isEqualTo("ESTORNADO");
        verify(salvos).registrar(argThat(p -> p.dados().estado() == EstadoPagamento.ESTORNADO));
        verifyNoInteractions(checkout);
    }
    @Test void jaEstornadoNaoRepetePost() {
        when(pagamentos.consultar("12345")).thenReturn(dados("refunded", "100.00"));
        executar(estornar);
        verifyNoInteractions(estornos);
        assertThat(resposta().path("type").asString()).isEqualTo("PagamentoEstornado");
    }
    @Test void redeliveryConcluidoNaoChamaProvedor() {
        when(registro.estado(estornar)).thenReturn(Optional.of("CONCLUIDO"));
        executar(estornar);
        verifyNoInteractions(estornos, pagamentos);
        verify(registro, never()).reservar(any(), any());
    }
    @Test void timeoutNaoViraFalhaNemSucesso() {
        when(pagamentos.consultar("12345")).thenReturn(dados("approved", "0.00"));
        doThrow(new IllegalStateException("timeout")).when(estornos).estornarIntegral(any(), any());
        assertThatIllegalStateException().isThrownBy(() -> executar(estornar));
        verify(registro).incerto(estornar.comandoId());
        verify(registro, never()).concluir(any(), any(), any(), any());
    }
    @Test void resultadoIncertoReconciliaSoComGet() {
        when(registro.estado(estornar)).thenReturn(Optional.of("INCERTO"));
        when(pagamentos.consultar("12345")).thenReturn(dados("refunded", "100.00"));
        executar(estornar);
        verifyNoInteractions(estornos);
        assertThat(resposta().path("status").asString()).isEqualTo("ESTORNADO");
    }
    @Test void consultaAindaAprovadaNaoRepetePostIncerto() {
        when(registro.estado(estornar)).thenReturn(Optional.of("INCERTO"));
        when(pagamentos.consultar("12345")).thenReturn(dados("approved", "0.00"));
        assertThatIllegalStateException().isThrownBy(() -> executar(estornar));
        verifyNoInteractions(estornos);
    }
    @Test void parcialNaoGeraNovoEstorno() {
        when(pagamentos.consultar("12345")).thenReturn(dados("approved", "20.00"));
        executar(estornar);
        verifyNoInteractions(estornos);
        assertThat(resposta().path("erro").path("codigo").asString()).isEqualTo("ESTORNO_PARCIAL_NAO_SUPORTADO");
    }
    @Test void versaoDiferenteNaoEstorna() {
        executar(comando(ComandoPagamento.Tipo.EstornarPagamento, "12345", 3));
        verifyNoInteractions(pagamentos, estornos);
        assertThat(resposta().path("status").asString()).isEqualTo("FALHA");
    }
    @Test void pagamentoDesconhecidoNaoEstorna() {
        when(salvos.listarPorOrcamento(id)).thenReturn(List.of());
        executar(estornar);
        verifyNoInteractions(estornos, pagamentos);
        assertThat(resposta().path("erro").path("codigo").asString()).isEqualTo("PAGAMENTO_NAO_REGISTRADO");
    }
    @Test void reservaConcorrenteNaoChamaProvedor() {
        doThrow(new DataIntegrityViolationException("unique")).when(registro).reservar(any(), any());
        assertThatIllegalStateException().isThrownBy(() -> executar(estornar));
        verifyNoInteractions(estornos, pagamentos, checkout);
    }
    @Test void comandoProcessandoNaoEReexecutado() {
        when(registro.estado(estornar)).thenReturn(Optional.of("PROCESSANDO"));
        assertThatIllegalStateException().isThrownBy(() -> executar(estornar));
        verifyNoInteractions(estornos, pagamentos);
    }
    @Test void erroDeOrcamentoPublicaFalhaCorrelacionada() {
        when(consultar.executar(id)).thenThrow(new OrcamentoNaoEncontradoException());
        executar(pagar);
        assertThat(resposta().path("erro").path("codigo").asString()).isEqualTo("ORCAMENTO_NAO_ENCONTRADO");
    }
    @Test void outraOsNaoCriaLink() {
        var errado = new ComandoPagamento(pagar.type(), pagar.messageId(), pagar.sagaId(), null,
                pagar.comandoId(), 999, 1, id, 2, null);
        executar(errado);
        verifyNoInteractions(checkout);
        assertThat(resposta().path("status").asString()).isEqualTo("FALHA");
    }
    @Test void registroAprovadoGeraEventoSemRecobrar() {
        when(registro.aguardando()).thenReturn(List.of(json.writeValueAsString(pagar)));
        usecase.confirmarRegistrados();
        assertThat(resposta().path("status").asString()).isEqualTo("APROVADO");
        assertThat(resposta().path("causationId").asString()).isEqualTo(pagar.messageId().toString());
        verifyNoInteractions(checkout, estornos);
    }
    @Test void pendenteNaoGeraAprovacao() {
        when(registro.aguardando()).thenReturn(List.of(json.writeValueAsString(pagar)));
        when(salvos.listarPorOrcamento(id)).thenReturn(List.of(new Pagamento(id, dados("pending", "0.00"), Instant.EPOCH)));
        usecase.confirmarRegistrados();
        verify(registro, never()).concluir(any(), any(), any(), any());
    }
    @Test void rejeitaTipoDesconhecidoAusenteEQuantidadeFracionariaDeVersao() {
        var corpo = json.writeValueAsString(pagar);
        assertThatThrownBy(() -> usecase.executar(corpo.replace("ProcessarPagamento", "PagarOutraCoisa")));
        assertThatThrownBy(() -> usecase.executar(corpo.replace("\"versao\":2", "\"versao\":2.5")));
        assertThatIllegalArgumentException().isThrownBy(() -> comando(ComandoPagamento.Tipo.EstornarPagamento, null, 2));
        assertThatIllegalArgumentException().isThrownBy(() -> comando(ComandoPagamento.Tipo.ProcessarPagamento, "12345", 2));
        assertThatIllegalArgumentException().isThrownBy(() -> comando(null, null, 2));
    }
    private void executar(ComandoPagamento c) { usecase.executar(json.writeValueAsString(c)); }
    private tools.jackson.databind.JsonNode resposta() {
        var captor = ArgumentCaptor.forClass(String.class);
        verify(registro).concluir(any(), any(), captor.capture(), any());
        return json.readTree(captor.getValue());
    }
    private ComandoPagamento comando(ComandoPagamento.Tipo tipo, String pagamentoId, int versao) {
        return new ComandoPagamento(tipo, UUID.randomUUID(), "saga-123", null, UUID.randomUUID(),
                123, 1, id, versao, pagamentoId);
    }
    private PagamentoConsultado dados(String status, String estornado) {
        return new PagamentoConsultado("12345", id.toString(), status, null, new BigDecimal("100.00"),
                "BRL", new BigDecimal(estornado), Instant.EPOCH);
    }
}
