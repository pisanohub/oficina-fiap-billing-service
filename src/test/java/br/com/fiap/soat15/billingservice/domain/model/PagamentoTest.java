package br.com.fiap.soat15.billingservice.domain.model;

import br.com.fiap.soat15.billingservice.domain.exception.PagamentoIncompativelException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class PagamentoTest {
    private final UUID orcamento = UUID.randomUUID();
    private final Instant agora = Instant.parse("2026-10-28T12:00:00Z");

    @ParameterizedTest
    @CsvSource({"approved,0,APROVADO,true", "approved,10,PARCIALMENTE_ESTORNADO,false", "approved,100,ESTORNADO,false",
            "pending,0,PENDENTE,false", "in_process,0,EM_PROCESSAMENTO,false", "authorized,0,AUTORIZADO,false",
            "rejected,0,RECUSADO,false", "cancelled,0,CANCELADO,false", "refunded,100,ESTORNADO,false",
            "charged_back,0,CONTESTADO,false", "in_mediation,0,EM_MEDIACAO,false", "status_novo,0,DESCONHECIDO,false"})
    void classificaSemConfundirPendenteComAprovado(String status, String estorno, EstadoPagamento estado, boolean confirmado) {
        var pagamento = new Pagamento(orcamento, dados(status, estorno, agora), agora);
        assertThat(pagamento.dados().estado()).isEqualTo(estado);
        assertThat(pagamento.confirmado()).isEqualTo(confirmado);
    }

    @Test
    void detalheDeEstornoParcialNuncaConfirmaSaldoIntegral() {
        var dados = new PagamentoConsultado("123", orcamento.toString(), "approved", "partially_refunded",
                new BigDecimal("100"), "BRL", BigDecimal.ZERO, agora);
        assertThat(dados.estado()).isEqualTo(EstadoPagamento.PARCIALMENTE_ESTORNADO);
    }

    @Test
    void impedeSnapshotIncompletoOuFinanceiramenteInvalido() {
        for (var id : new String[]{null, "", "0", "-1", "1/2", "a", "1".repeat(26)}) {
            assertThatIllegalArgumentException().isThrownBy(() -> PagamentoConsultado.validarId(id));
        }
        assertThatIllegalArgumentException().isThrownBy(() -> new PagamentoConsultado("123", null, "approved", null, BigDecimal.TEN, "BRL", BigDecimal.ZERO, agora));
        assertThatIllegalArgumentException().isThrownBy(() -> new PagamentoConsultado("123", orcamento.toString(), null, null, BigDecimal.TEN, "BRL", BigDecimal.ZERO, agora));
        assertThatIllegalArgumentException().isThrownBy(() -> new PagamentoConsultado("123", orcamento.toString(), "a".repeat(41), null, BigDecimal.TEN, "BRL", BigDecimal.ZERO, agora));
        assertThatIllegalArgumentException().isThrownBy(() -> new PagamentoConsultado("123", orcamento.toString(), "approved", "a".repeat(101), BigDecimal.TEN, "BRL", BigDecimal.ZERO, agora));
        assertThatIllegalArgumentException().isThrownBy(() -> new PagamentoConsultado("123", orcamento.toString(), "approved", null, BigDecimal.ZERO, "BRL", BigDecimal.ZERO, agora));
        assertThatIllegalArgumentException().isThrownBy(() -> new PagamentoConsultado("123", orcamento.toString(), "approved", null, BigDecimal.TEN, "B", BigDecimal.ZERO, agora));
        assertThatIllegalArgumentException().isThrownBy(() -> new PagamentoConsultado("123", orcamento.toString(), "approved", null, BigDecimal.TEN, null, BigDecimal.ZERO, agora));
        assertThatIllegalArgumentException().isThrownBy(() -> new PagamentoConsultado("123", orcamento.toString(), "approved", null, BigDecimal.TEN, "BRL", new BigDecimal("11"), agora));
        assertThatIllegalArgumentException().isThrownBy(() -> new PagamentoConsultado("123", orcamento.toString(), "approved", null, BigDecimal.TEN, "BRL", null, agora));
        assertThatIllegalArgumentException().isThrownBy(() -> new PagamentoConsultado("123", orcamento.toString(), "approved", null, BigDecimal.TEN, "BRL", BigDecimal.ZERO, null));
    }

    @Test
    void repeticaoERespostaAntigaNaoSobrescrevemRegistroAtual() {
        var atual = new Pagamento(orcamento, dados("approved", "0", agora), agora);
        assertThat(atual.atualizarCom(new Pagamento(orcamento, dados("approved", "0", agora), agora.plusSeconds(9)))).isSameAs(atual);
        assertThat(atual.atualizarCom(new Pagamento(orcamento, dados("pending", "0", agora.minusSeconds(1)), agora))).isSameAs(atual);
        var novo = new Pagamento(orcamento, dados("refunded", "100", agora.plusSeconds(1)), agora.plusSeconds(2));
        assertThat(atual.atualizarCom(novo)).isSameAs(novo);
        assertThatThrownBy(() -> atual.atualizarCom(new Pagamento(orcamento, dados("pending", "0", agora), agora)))
                .isInstanceOf(PagamentoIncompativelException.class);
    }

    @Test
    void validaVinculoEImutabilidadeFinanceira() {
        var atual = new Pagamento(orcamento, dados("approved", "0", agora), agora);
        assertThatThrownBy(() -> new Pagamento(null, atual.dados(), agora)).isInstanceOf(PagamentoIncompativelException.class);
        assertThatThrownBy(() -> new Pagamento(orcamento, null, agora)).isInstanceOf(PagamentoIncompativelException.class);
        assertThatThrownBy(() -> new Pagamento(orcamento, atual.dados(), null)).isInstanceOf(PagamentoIncompativelException.class);
        assertThatThrownBy(() -> new Pagamento(UUID.randomUUID(), atual.dados(), agora)).isInstanceOf(PagamentoIncompativelException.class);
        var outroId = UUID.randomUUID();
        var outro = new Pagamento(outroId, new PagamentoConsultado("123", outroId.toString(), "approved", null, new BigDecimal("100"), "BRL", BigDecimal.ZERO, agora), agora);
        assertThatThrownBy(() -> atual.atualizarCom(outro)).isInstanceOf(PagamentoIncompativelException.class);
        for (var novo : new PagamentoConsultado[]{
                new PagamentoConsultado("456", orcamento.toString(), "approved", null, new BigDecimal("100"), "BRL", BigDecimal.ZERO, agora),
                new PagamentoConsultado("123", orcamento.toString(), "approved", null, new BigDecimal("101"), "BRL", BigDecimal.ZERO, agora),
                new PagamentoConsultado("123", orcamento.toString(), "approved", null, new BigDecimal("100"), "USD", BigDecimal.ZERO, agora)}) {
            assertThatThrownBy(() -> atual.atualizarCom(new Pagamento(orcamento, novo, agora))).isInstanceOf(PagamentoIncompativelException.class);
        }
    }
    private PagamentoConsultado dados(String status, String estorno, Instant data) {
        return new PagamentoConsultado("123", orcamento.toString(), status, "accredited", new BigDecimal("100"), "BRL", new BigDecimal(estorno), data);
    }
}
