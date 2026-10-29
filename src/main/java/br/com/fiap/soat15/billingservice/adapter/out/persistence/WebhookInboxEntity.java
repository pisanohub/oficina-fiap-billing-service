package br.com.fiap.soat15.billingservice.adapter.out.persistence;

import br.com.fiap.soat15.billingservice.domain.model.WebhookTrabalho;
import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mercadopago_webhook_inbox", indexes = @Index(name = "idx_webhook_pendente", columnList = "estado,proximaTentativa"))
public class WebhookInboxEntity {
    @Id @Column(length = 64) String id;
    @Column(nullable = false, length = 25) String pagamentoId;
    @Column(nullable = false, length = 16) String estado;
    @Column(nullable = false) Instant recebidoEm;
    @Column(nullable = false) Instant atualizadoEm;
    @Column(nullable = false) Instant proximaTentativa;
    @Column(nullable = false) int tentativas;
    UUID reserva;
    @Column(length = 48) String ultimoErro;

    protected WebhookInboxEntity() { }
    WebhookInboxEntity(String id, String pagamentoId, Instant agora) {
        this.id = id; this.pagamentoId = pagamentoId; estado = "PENDENTE";
        recebidoEm = agora; atualizadoEm = agora; proximaTentativa = agora;
    }
    boolean disponivel(Instant agora) {
        return (estado.equals("PENDENTE") || estado.equals("PROCESSANDO")) && !proximaTentativa.isAfter(agora);
    }
    WebhookTrabalho reservar(Instant agora) {
        estado = "PROCESSANDO"; reserva = UUID.randomUUID(); tentativas++;
        atualizadoEm = agora; proximaTentativa = agora.plus(Duration.ofMinutes(1));
        return new WebhookTrabalho(id, pagamentoId, reserva, tentativas);
    }
    boolean pertence(WebhookTrabalho trabalho) {
        return estado.equals("PROCESSANDO") && trabalho.reserva().equals(reserva);
    }
    void concluir(Instant agora) { estado = "PROCESSADO"; atualizadoEm = agora; reserva = null; ultimoErro = null; }
    void falhar(Instant agora, boolean definitiva, String codigo) {
        estado = definitiva || tentativas >= 8 ? "FALHA" : "PENDENTE";
        atualizadoEm = agora; reserva = null; ultimoErro = codigo;
        proximaTentativa = agora.plusSeconds(Math.min(900, 60L << Math.min(tentativas - 1, 4)));
    }
}
