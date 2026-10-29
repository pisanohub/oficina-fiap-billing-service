package br.com.fiap.soat15.billingservice.adapter.out.persistence;

import br.com.fiap.soat15.billingservice.application.port.out.WebhookInbox;
import br.com.fiap.soat15.billingservice.domain.model.WebhookTrabalho;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;

@Repository
public class WebhookInboxPersistenceAdapter implements WebhookInbox {
    private final SpringDataWebhookInboxRepository repository;
    private final TransactionTemplate transaction;
    public WebhookInboxPersistenceAdapter(SpringDataWebhookInboxRepository repository, PlatformTransactionManager manager) {
        this.repository = repository; transaction = new TransactionTemplate(manager);
        transaction.setTimeout(5);
    }
    public void receber(String id, String pagamentoId, Instant agora) {
        try {
            transaction.executeWithoutResult(status -> {
                if (!repository.existsById(id)) repository.saveAndFlush(new WebhookInboxEntity(id, pagamentoId, agora));
            });
        } catch (DataIntegrityViolationException ex) {
            if (!repository.existsById(id)) throw ex;
        }
    }
    public List<String> pendentes(Instant agora) { return repository.pendentes(agora, PageRequest.of(0, 20)); }
    public Optional<WebhookTrabalho> reservar(String id, Instant agora) {
        return transaction.execute(status -> repository.bloquear(id).filter(w -> w.disponivel(agora)).map(w -> {
            if (w.tentativas >= 8) {
                w.falhar(agora, true, "RESERVAS_ESGOTADAS"); repository.saveAndFlush(w); return null;
            }
            var trabalho = w.reservar(agora); repository.saveAndFlush(w); return trabalho;
        }));
    }
    public void concluir(WebhookTrabalho trabalho, Instant agora) {
        transaction.executeWithoutResult(status -> repository.bloquear(trabalho.entregaId()).filter(w -> w.pertence(trabalho))
                .ifPresent(w -> { w.concluir(agora); repository.saveAndFlush(w); }));
    }
    public void falhar(WebhookTrabalho trabalho, Instant agora, boolean definitiva, String codigo) {
        transaction.executeWithoutResult(status -> repository.bloquear(trabalho.entregaId()).filter(w -> w.pertence(trabalho))
                .ifPresent(w -> { w.falhar(agora, definitiva, codigo); repository.saveAndFlush(w); }));
    }
}
