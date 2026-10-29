package br.com.fiap.soat15.billingservice.application.port.out;

import br.com.fiap.soat15.billingservice.domain.model.WebhookTrabalho;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WebhookInbox {
    void receber(String entregaId, String pagamentoId, Instant agora);
    List<String> pendentes(Instant agora);
    Optional<WebhookTrabalho> reservar(String entregaId, Instant agora);
    void concluir(WebhookTrabalho trabalho, Instant agora);
    void falhar(WebhookTrabalho trabalho, Instant agora, boolean definitiva, String codigo);
}
