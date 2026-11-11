package br.com.fiap.soat15.billingservice.application.usecase;

import br.com.fiap.soat15.billingservice.domain.model.*;
import java.math.BigDecimal;
import java.util.*;

/** DTO plano enviado pelo OS. A fila já identifica o comando; type é opcional. */
public record SolicitarOrcamentoMensagem(String type, UUID messageId, String sagaId, UUID causationId,
        UUID comandoId, long ordemId, long clienteId, Integer versao, List<Item> itens) {
    public SolicitarOrcamentoMensagem {
        if (type != null && !type.equals("GerarOrcamento") || messageId == null
                || sagaId == null || sagaId.isBlank() || sagaId.length() > 120
                || comandoId == null || ordemId <= 0 || clienteId <= 0
                || versao != null && versao <= 0) {
            throw new IllegalArgumentException("Metadados de orçamento inválidos.");
        }
        if (itens != null) itens = Collections.unmodifiableList(new ArrayList<>(itens));
    }
    public record Item(String referencia, String tipo, String descricao,
            BigDecimal quantidade, BigDecimal valorUnitario) {
        ItemOrcamento dominio() {
            if (tipo == null || quantidade == null) throw new IllegalArgumentException("Item incompleto.");
            try {
                return new ItemOrcamento(referencia, TipoItem.valueOf(tipo), descricao,
                        quantidade.intValueExact(), valorUnitario);
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("Quantidade deve ser inteira e positiva.");
            }
        }
    }
    public GerarOrcamentoCommand dominio() {
        if (itens == null || itens.isEmpty() || itens.size() > 100 || itens.contains(null)) {
            throw new IllegalArgumentException("Itens incompletos.");
        }
        return new GerarOrcamentoCommand(comandoId, ordemId, clienteId,
                itens.stream().map(Item::dominio).toList());
    }
}
