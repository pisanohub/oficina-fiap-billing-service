package br.com.fiap.soat15.billingservice.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Domínio independente de Spring/JPA. Nesta etapa o orçamento é imutável. */
public record Orcamento(UUID id, UUID comandoId, long ordemId, long clienteId,
                        List<ItemOrcamento> itens, Instant criadoEm) {
    public Orcamento {
        if (id == null || comandoId == null || criadoEm == null) {
            throw new IllegalArgumentException("Identificadores e data de criação são obrigatórios.");
        }
        if (ordemId <= 0 || clienteId <= 0) {
            throw new IllegalArgumentException("OS e cliente devem ter identificadores positivos.");
        }
        if (itens == null || itens.isEmpty() || itens.size() > 100 || itens.stream().anyMatch(item -> item == null)) {
            throw new IllegalArgumentException("Informe entre 1 e 100 itens válidos.");
        }
        itens = List.copyOf(itens);
        var referencias = new HashSet<String>();
        for (var item : itens) {
            if (!referencias.add(item.tipo() + ":" + item.referencia())) {
                throw new IllegalArgumentException("Item duplicado; consolide a quantidade na mesma referência e tipo.");
            }
        }
        ItemOrcamento.dinheiro(itens.stream().map(ItemOrcamento::subtotal)
                .reduce(new BigDecimal("0.00"), BigDecimal::add));
    }

    public BigDecimal total() {
        return itens.stream().map(ItemOrcamento::subtotal).reduce(new BigDecimal("0.00"), BigDecimal::add);
    }

    public String status() {
        return "AGUARDANDO_APROVACAO";
    }

    public boolean corresponde(long ordem, long cliente, List<ItemOrcamento> novosItens) {
        return ordemId == ordem && clienteId == cliente && itens.equals(novosItens);
    }
}
