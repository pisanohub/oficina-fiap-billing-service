package br.com.fiap.soat15.billingservice.adapter.out.persistence;

import br.com.fiap.soat15.billingservice.domain.model.Orcamento;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orcamentos", uniqueConstraints = @UniqueConstraint(name = "uk_orcamento_comando", columnNames = "comando_id"))
public class OrcamentoEntity {
    @Id
    private UUID id;
    @Column(name = "comando_id", nullable = false, updatable = false)
    private UUID comandoId;
    @Column(name = "ordem_id", nullable = false, updatable = false)
    private long ordemId;
    @Column(name = "cliente_id", nullable = false, updatable = false)
    private long clienteId;
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;
    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal total;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "orcamento_itens", joinColumns = @JoinColumn(name = "orcamento_id"))
    @OrderColumn(name = "posicao")
    private List<ItemOrcamentoEmbeddable> itens = new ArrayList<>();

    protected OrcamentoEntity() { }

    public OrcamentoEntity(Orcamento orcamento) {
        id = orcamento.id();
        comandoId = orcamento.comandoId();
        ordemId = orcamento.ordemId();
        clienteId = orcamento.clienteId();
        criadoEm = orcamento.criadoEm();
        total = orcamento.total();
        itens = new ArrayList<>(orcamento.itens().stream().map(ItemOrcamentoEmbeddable::new).toList());
    }

    public Orcamento paraDominio() {
        return new Orcamento(id, comandoId, ordemId, clienteId,
                itens.stream().map(ItemOrcamentoEmbeddable::paraDominio).toList(), criadoEm);
    }
}
