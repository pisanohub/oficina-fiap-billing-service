package br.com.fiap.soat15.billingservice.adapter.out.persistence;

import br.com.fiap.soat15.billingservice.domain.model.ItemOrcamento;
import br.com.fiap.soat15.billingservice.domain.model.TipoItem;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Embeddable
public class ItemOrcamentoEmbeddable {
    @Column(nullable = false, length = 80)
    private String referencia;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoItem tipo;
    @Column(nullable = false, length = 255)
    private String descricao;
    @Column(nullable = false)
    private int quantidade;
    @Column(name = "valor_unitario", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorUnitario;

    protected ItemOrcamentoEmbeddable() { }

    public ItemOrcamentoEmbeddable(ItemOrcamento item) {
        referencia = item.referencia();
        tipo = item.tipo();
        descricao = item.descricao();
        quantidade = item.quantidade();
        valorUnitario = item.valorUnitario();
    }

    public ItemOrcamento paraDominio() {
        return new ItemOrcamento(referencia, tipo, descricao, quantidade, valorUnitario);
    }
}
