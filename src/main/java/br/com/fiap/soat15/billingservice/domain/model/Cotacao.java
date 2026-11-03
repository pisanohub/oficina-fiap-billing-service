package br.com.fiap.soat15.billingservice.domain.model;

import java.time.Instant;
import java.util.*;

public record Cotacao(String moeda, Instant geradaEm, List<ItemOrcamento> itens) {
    public Cotacao {
        if (!"BRL".equals(moeda) || geradaEm == null || itens == null || itens.isEmpty() || itens.size() > 20
                || itens.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("Cotação incompleta ou moeda inválida.");
        itens = List.copyOf(itens);
        var chaves = new HashSet<String>();
        for (var item : itens) {
            if (!chaves.add(item.tipo() + ":" + item.referencia())) throw new IllegalArgumentException("Cotação com item repetido.");
        }
    }
    public List<ItemOrcamento> conferir(List<ItemCotacao> pedidos) {
        ItemCotacao.validarLista(pedidos);
        if (pedidos.size() != itens.size()) throw new IllegalArgumentException("Cotação não corresponde aos itens pedidos.");
        Map<String, ItemOrcamento> porChave = new HashMap<>();
        for (var item : itens) porChave.put(item.tipo() + ":" + item.referencia(), item);
        List<ItemOrcamento> ordenados = new ArrayList<>();
        for (var pedido : pedidos) {
            var item = porChave.get(pedido.chave());
            if (item == null || item.quantidade() != pedido.quantidade()) {
                throw new IllegalArgumentException("Cotação retornou item ou quantidade diferente do pedido.");
            }
            ordenados.add(item);
        }
        return List.copyOf(ordenados);
    }
}
