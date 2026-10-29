package br.com.fiap.soat15.billingservice.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

public interface SpringDataWebhookInboxRepository extends JpaRepository<WebhookInboxEntity, String> {
    @Query("select w.id from WebhookInboxEntity w where w.estado in ('PENDENTE','PROCESSANDO') and w.proximaTentativa <= :agora order by w.proximaTentativa, w.recebidoEm, w.id")
    List<String> pendentes(@Param("agora") Instant agora, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WebhookInboxEntity w where w.id = :id")
    Optional<WebhookInboxEntity> bloquear(@Param("id") String id);
}
