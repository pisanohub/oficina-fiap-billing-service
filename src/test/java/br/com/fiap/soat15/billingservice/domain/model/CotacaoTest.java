package br.com.fiap.soat15.billingservice.domain.model;

import br.com.fiap.soat15.billingservice.application.usecase.GerarOrcamentoCotadoCommand;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CotacaoTest {
    private final ItemCotacao pedido = new ItemCotacao(TipoItem.PECA, "filtro", 1);
    private final ItemOrcamento item = new ItemOrcamento("filtro", TipoItem.PECA, "Filtro", 1, BigDecimal.TEN);
    private final Instant data = Instant.parse("2026-11-03T12:00:00Z");
    @Test void validaIdentificacaoQuantidadeELimitesDaLista() {
        for (String id : Arrays.asList(null, "", " ", " filtro", "x".repeat(81))) {
            assertThatIllegalArgumentException().isThrownBy(() -> new ItemCotacao(TipoItem.PECA, id, 1));
        }
        assertThatIllegalArgumentException().isThrownBy(() -> new ItemCotacao(null, "filtro", 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new ItemCotacao(TipoItem.PECA, "filtro", 0));
        for (List<ItemCotacao> lista : Arrays.asList(null, List.<ItemCotacao>of(), Arrays.asList(pedido, null),
                Collections.nCopies(21, pedido), List.of(pedido, pedido))) {
            assertThatIllegalArgumentException().isThrownBy(() -> ItemCotacao.validarLista(lista));
        }
        assertThat(ItemCotacao.validarLista(List.of(pedido))).containsExactly(pedido);
    }
    @Test void validaCotacaoEConfereConjuntoCompletoDeItens() {
        for (List<ItemOrcamento> lista : Arrays.asList(null, List.<ItemOrcamento>of(), Arrays.asList(item, null),
                Collections.nCopies(21, item), List.of(item, item))) {
            assertThatIllegalArgumentException().isThrownBy(() -> new Cotacao("BRL", data, lista));
        }
        assertThatIllegalArgumentException().isThrownBy(() -> new Cotacao("USD", data, List.of(item)));
        assertThatIllegalArgumentException().isThrownBy(() -> new Cotacao("BRL", null, List.of(item)));
        var cotacao = new Cotacao("BRL", data, List.of(item));
        assertThat(cotacao.conferir(List.of(pedido))).containsExactly(item);
        assertThatIllegalArgumentException().isThrownBy(() -> cotacao.conferir(List.of(pedido, new ItemCotacao(TipoItem.SERVICO, "s1", 1))));
        assertThatIllegalArgumentException().isThrownBy(() -> cotacao.conferir(List.of(new ItemCotacao(TipoItem.PECA, "outro", 1))));
        assertThatIllegalArgumentException().isThrownBy(() -> cotacao.conferir(List.of(new ItemCotacao(TipoItem.PECA, "filtro", 2))));
    }
    @Test void metadadosEComandoExigemIdentificadoresPositivos() {
        var orcamento = new Orcamento(UUID.randomUUID(), UUID.randomUUID(), 1, 1, List.of(item), data);
        assertThatIllegalArgumentException().isThrownBy(() -> new OrcamentoCotado(null, 1, data));
        assertThatIllegalArgumentException().isThrownBy(() -> new OrcamentoCotado(orcamento, 0, data));
        assertThatIllegalArgumentException().isThrownBy(() -> new OrcamentoCotado(orcamento, 1, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new GerarOrcamentoCotadoCommand(null, 1, 1, 1, List.of(pedido)));
        assertThatIllegalArgumentException().isThrownBy(() -> new GerarOrcamentoCotadoCommand(UUID.randomUUID(), 0, 1, 1, List.of(pedido)));
        assertThatIllegalArgumentException().isThrownBy(() -> new GerarOrcamentoCotadoCommand(UUID.randomUUID(), 1, 0, 1, List.of(pedido)));
        assertThatIllegalArgumentException().isThrownBy(() -> new GerarOrcamentoCotadoCommand(UUID.randomUUID(), 1, 1, 0, List.of(pedido)));
        var cotado = new OrcamentoCotado(orcamento, 1, data);
        assertThat(cotado.corresponde(1, 1, 1, List.of(pedido))).isTrue();
        assertThat(cotado.corresponde(1, 1, 1, List.of())).isFalse();
    }
}
