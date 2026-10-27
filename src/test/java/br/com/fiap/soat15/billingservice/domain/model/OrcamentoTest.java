package br.com.fiap.soat15.billingservice.domain.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class OrcamentoTest {
    private final UUID id = UUID.randomUUID();
    private final UUID comando = UUID.randomUUID();
    private final Instant agora = Instant.parse("2026-10-27T12:00:00Z");

    @Test
    void somaItensEMantemSnapshotImutavel() {
        var itens = new ArrayList<>(List.of(item("a", "0.10"), item("b", "0.20")));
        var orcamento = novo(itens);
        itens.clear();
        assertThat(orcamento.total()).isEqualByComparingTo("0.30");
        assertThat(orcamento.status()).isEqualTo("AGUARDANDO_APROVACAO");
        assertThat(orcamento.itens()).hasSize(2);
        assertThatThrownBy(() -> orcamento.itens().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(orcamento.corresponde(1, 2, orcamento.itens())).isTrue();
        assertThat(orcamento.corresponde(9, 2, orcamento.itens())).isFalse();
        assertThat(orcamento.corresponde(1, 9, orcamento.itens())).isFalse();
        assertThat(orcamento.corresponde(1, 2, List.of(item("c", "1")))).isFalse();
    }

    @Test
    void exigeIdentificadoresEData() {
        var itens = List.of(item("a", "1"));
        assertThatIllegalArgumentException().isThrownBy(() -> new Orcamento(null, comando, 1, 2, itens, agora));
        assertThatIllegalArgumentException().isThrownBy(() -> new Orcamento(id, null, 1, 2, itens, agora));
        assertThatIllegalArgumentException().isThrownBy(() -> new Orcamento(id, comando, 1, 2, itens, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new Orcamento(id, comando, 0, 2, itens, agora));
        assertThatIllegalArgumentException().isThrownBy(() -> new Orcamento(id, comando, 1, -1, itens, agora));
    }

    @Test
    void exigeListaValidaSemItensDuplicados() {
        assertThatIllegalArgumentException().isThrownBy(() -> novo(null));
        assertThatIllegalArgumentException().isThrownBy(() -> novo(List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> novo(Arrays.asList((ItemOrcamento) null)));
        assertThatIllegalArgumentException().isThrownBy(() -> novo(Collections.nCopies(101, item("a", "1"))));
        assertThatIllegalArgumentException().isThrownBy(() -> novo(List.of(item("a", "1"), item("a", "2"))));
    }

    @Test
    void referenciaIgualEmTiposDiferentesEPermitida() {
        assertThat(novo(List.of(item("a", "1"), new ItemOrcamento("a", TipoItem.SERVICO, "Mão de obra", 1, BigDecimal.TEN))).total())
                .isEqualByComparingTo("11.00");
    }

    @Test
    void rejeitaTotalMaiorQueColunaMonetaria() {
        assertThatIllegalArgumentException().isThrownBy(() -> novo(List.of(item("a", "99999999999999999.99"), item("b", "0.01"))));
    }

    private ItemOrcamento item(String ref, String valor) {
        return new ItemOrcamento(ref, TipoItem.PECA, "Peça", 1, new BigDecimal(valor));
    }
    private Orcamento novo(List<ItemOrcamento> itens) {
        return new Orcamento(id, comando, 1, 2, itens, agora);
    }
}
