package br.com.fiap.soat15.billingservice.adapter.out.persistence;

import br.com.fiap.soat15.billingservice.domain.model.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pagamentos", indexes = @Index(name = "idx_pagamento_orcamento", columnList = "orcamento_id"))
public class PagamentoEntity {
    @Id @Column(length = 25)
    private String pagamentoId;
    @Column(name = "orcamento_id", nullable = false, updatable = false)
    private UUID orcamentoId;
    @Column(nullable = false, length = 40)
    private String statusProvedor;
    @Column(length = 100)
    private String statusDetalhe;
    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal valor;
    @Column(nullable = false, length = 3, updatable = false)
    private String moeda;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valorEstornado;
    @Column(nullable = false)
    private Instant atualizadoNoProvedor;
    @Column(nullable = false)
    private Instant consultadoEm;
    protected PagamentoEntity() { }
    public PagamentoEntity(Pagamento pagamento) {
        pagamentoId = pagamento.dados().id();
        orcamentoId = pagamento.orcamentoId();
        atualizar(pagamento);
    }
    public void atualizar(Pagamento pagamento) {
        var dados = pagamento.dados();
        statusProvedor = dados.status(); statusDetalhe = dados.statusDetalhe();
        valor = dados.valor(); moeda = dados.moeda(); valorEstornado = dados.valorEstornado();
        atualizadoNoProvedor = dados.atualizadoNoProvedor(); consultadoEm = pagamento.consultadoEm();
    }
    public Pagamento paraDominio() {
        return new Pagamento(orcamentoId, new PagamentoConsultado(pagamentoId, orcamentoId.toString(), statusProvedor,
                statusDetalhe, valor, moeda, valorEstornado, atualizadoNoProvedor), consultadoEm);
    }
}
