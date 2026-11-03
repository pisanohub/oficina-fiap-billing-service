package br.com.fiap.soat15.billingservice.domain.model;

import java.util.*;

/** Referência do catálogo do Execução, sem preço informado pelo solicitante. */
public record ItemCotacao(TipoItem tipo, String itemId, int quantidade) {
    public ItemCotacao {
        if (tipo == null || itemId == null || itemId.isBlank() || itemId.length() > 80
                || !itemId.equals(itemId.strip()) || quantidade <= 0) {
            throw new IllegalArgumentException("Informe tipo, itemId de até 80 caracteres sem espaços nas pontas e quantidade positiva.");
        }
    }
    public String chave() { return tipo + ":" + itemId; }
    public static List<ItemCotacao> validarLista(List<ItemCotacao> itens) {
        if (itens == null || itens.isEmpty() || itens.size() > 20 || itens.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("A cotação exige entre 1 e 20 itens válidos.");
        }
        var chaves = new HashSet<String>();
        for (var item : itens) {
            if (!chaves.add(item.chave())) throw new IllegalArgumentException("Não repita o mesmo tipo e itemId na cotação.");
        }
        return List.copyOf(itens);
    }
}
