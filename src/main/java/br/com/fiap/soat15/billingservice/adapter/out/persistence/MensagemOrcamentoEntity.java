package br.com.fiap.soat15.billingservice.adapter.out.persistence;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "mensagens_orcamento")
public class MensagemOrcamentoEntity {
    @Id UUID comandoId;
    @Version Long revisao;
    @Column(nullable = false, columnDefinition = "text") String corpo;
    @Column(nullable = false, columnDefinition = "text") String identidade;
    @Column(columnDefinition = "text") String resultado;
    @Column(nullable = false) boolean enviado;
    protected MensagemOrcamentoEntity() { }
    public MensagemOrcamentoEntity(UUID id, String corpo, String identidade) {
        comandoId = id; this.corpo = corpo; this.identidade = identidade;
    }
}
