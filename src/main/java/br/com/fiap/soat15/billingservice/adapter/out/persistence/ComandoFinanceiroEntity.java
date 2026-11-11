package br.com.fiap.soat15.billingservice.adapter.out.persistence;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "comandos_financeiros")
public class ComandoFinanceiroEntity {
    @Id UUID comandoId;
    @Version Long revisao;
    @Column(nullable = false, unique = true, length = 150) String chaveOperacao;
    @Column(nullable = false, length = 600) String identidade;
    @Column(nullable = false, columnDefinition = "text") String corpo;
    @Column(nullable = false, length = 30) String estado;
    protected ComandoFinanceiroEntity() { }
    public ComandoFinanceiroEntity(UUID id, String chave, String identidade, String corpo) {
        comandoId = id; chaveOperacao = chave; this.identidade = identidade;
        this.corpo = corpo; estado = "PROCESSANDO";
    }
}
