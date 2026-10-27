package br.com.fiap.soat15.billingservice.adapter.out.persistence;

import br.com.fiap.soat15.billingservice.domain.model.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "preferencias_checkout")
public class PreferenciaCheckoutEntity {
    @Id
    private UUID orcamentoId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoPreferencia estado;
    @Column(length = 120)
    private String preferenciaId;
    @Column(length = 2000)
    private String checkoutUrl;
    @Column(length = 2000)
    private String checkoutTesteUrl;
    @Column(nullable = false)
    private Instant atualizadoEm;

    protected PreferenciaCheckoutEntity() { }

    public PreferenciaCheckoutEntity(UUID orcamentoId, Instant agora) {
        this.orcamentoId = orcamentoId;
        estado = EstadoPreferencia.CRIANDO;
        atualizadoEm = agora;
    }

    public PreferenciaCheckout paraDominio() {
        return new PreferenciaCheckout(orcamentoId, estado, preferenciaId, checkoutUrl, checkoutTesteUrl, atualizadoEm);
    }
}
