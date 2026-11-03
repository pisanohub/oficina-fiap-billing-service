package br.com.fiap.soat15.billingservice.domain.model;

import java.time.Instant;
import java.util.*;

/** Metadados de origem. Versão do diagnóstico NÃO é versão do orçamento. */
public record OrcamentoCotado(Orcamento orcamento, int versaoDiagnostico, Instant cotacaoGeradaEm) {
    public OrcamentoCotado {
        if (orcamento == null || versaoDiagnostico <= 0 || cotacaoGeradaEm == null) {
            throw new IllegalArgumentException("Orçamento cotado exige snapshot e versão positiva do diagnóstico.");
        }
    }
    public boolean corresponde(long ordem, long cliente, int versao, List<ItemCotacao> pedidos) {
        if (orcamento.ordemId() != ordem || orcamento.clienteId() != cliente || versaoDiagnostico != versao
                || orcamento.itens().size() != pedidos.size()) return false;
        var quantidades = new HashMap<String, Integer>();
        for (var item : orcamento.itens()) quantidades.put(item.tipo() + ":" + item.referencia(), item.quantidade());
        return pedidos.stream().allMatch(p -> Objects.equals(quantidades.get(p.chave()), p.quantidade()));
    }
}
