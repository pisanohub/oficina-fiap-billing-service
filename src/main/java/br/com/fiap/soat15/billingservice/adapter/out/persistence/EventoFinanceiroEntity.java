package br.com.fiap.soat15.billingservice.adapter.out.persistence;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "eventos_financeiros")
public class EventoFinanceiroEntity {
    @Id UUID messageId;
    @Column(nullable = false, columnDefinition = "text") String corpo;
    @Column(nullable = false) boolean enviado;
    protected EventoFinanceiroEntity() { }
    public EventoFinanceiroEntity(UUID id, String corpo) { messageId = id; this.corpo = corpo; }
    public UUID id() { return messageId; }
    public String corpo() { return corpo; }
}
