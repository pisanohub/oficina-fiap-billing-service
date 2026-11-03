package br.com.fiap.soat15.billingservice.adapter.out.persistence;

import br.com.fiap.soat15.billingservice.domain.model.OrcamentoCotado;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orcamento_cotacoes")
public class CotacaoOrcamentoEntity {
    @Id @Column(name = "orcamento_id") private UUID orcamentoId;
    @Column(nullable = false, updatable = false) private int versaoDiagnostico;
    @Column(nullable = false, updatable = false) private Instant cotacaoGeradaEm;
    protected CotacaoOrcamentoEntity() { }
    public CotacaoOrcamentoEntity(OrcamentoCotado cotado) {
        orcamentoId = cotado.orcamento().id(); versaoDiagnostico = cotado.versaoDiagnostico(); cotacaoGeradaEm = cotado.cotacaoGeradaEm();
    }
    public OrcamentoCotado paraDominio(br.com.fiap.soat15.billingservice.domain.model.Orcamento orcamento) {
        return new OrcamentoCotado(orcamento, versaoDiagnostico, cotacaoGeradaEm);
    }
}
