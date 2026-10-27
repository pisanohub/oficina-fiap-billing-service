package br.com.fiap.soat15.billingservice.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class ItemOrcamentoTest {
    @Test
    void calculaSubtotalComPrecisaoDecimalENormalizaTexto() {
        var item = new ItemOrcamento(" p1 ", TipoItem.PECA, " Óleo ", 3, new BigDecimal("0.10"));
        assertThat(item.subtotal()).isEqualByComparingTo("0.30");
        assertThat(item.referencia()).isEqualTo("p1");
        assertThat(item.descricao()).isEqualTo("Óleo");
        assertThat(item.valorUnitario().scale()).isEqualTo(2);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejeitaReferenciaOuDescricaoVazia(String texto) {
        assertThatIllegalArgumentException().isThrownBy(() -> item(texto, "Descrição", 1, BigDecimal.ONE));
        assertThatIllegalArgumentException().isThrownBy(() -> item("p1", texto, 1, BigDecimal.ONE));
    }

    @Test
    void rejeitaTextoLongoETipoAusente() {
        assertThatIllegalArgumentException().isThrownBy(() -> item("a".repeat(81), "Descrição", 1, BigDecimal.ONE));
        assertThatIllegalArgumentException().isThrownBy(() -> item("p1", "a".repeat(256), 1, BigDecimal.ONE));
        assertThatIllegalArgumentException().isThrownBy(() -> new ItemOrcamento("p1", null, "Descrição", 1, BigDecimal.ONE));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void rejeitaQuantidadeNaoPositiva(int quantidade) {
        assertThatIllegalArgumentException().isThrownBy(() -> item("p1", "Descrição", quantidade, BigDecimal.ONE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "1.001", "100000000000000000.00"})
    void rejeitaValorNegativoOuForaDoLimite(String valor) {
        assertThatIllegalArgumentException().isThrownBy(() -> item("p1", "Descrição", 1, new BigDecimal(valor)));
    }

    @Test
    void aceitaZeroERejeitaAusenciaOuSubtotalMuitoGrande() {
        assertThat(item("p1", "Cortesia", 1, BigDecimal.ZERO).subtotal()).isEqualByComparingTo("0.00");
        assertThatIllegalArgumentException().isThrownBy(() -> item("p1", "Descrição", 1, null));
        assertThatIllegalArgumentException().isThrownBy(() -> item("p1", "Descrição", 2, new BigDecimal("99999999999999999.99")));
    }

    private ItemOrcamento item(String referencia, String descricao, int quantidade, BigDecimal valor) {
        return new ItemOrcamento(referencia, TipoItem.PECA, descricao, quantidade, valor);
    }
}
