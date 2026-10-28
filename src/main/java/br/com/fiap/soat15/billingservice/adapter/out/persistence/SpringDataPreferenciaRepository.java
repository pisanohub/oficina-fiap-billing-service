package br.com.fiap.soat15.billingservice.adapter.out.persistence;

import br.com.fiap.soat15.billingservice.domain.model.EstadoPreferencia;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.UUID;

public interface SpringDataPreferenciaRepository extends JpaRepository<PreferenciaCheckoutEntity, UUID> {
    @Modifying(clearAutomatically = true)
    @Query("update PreferenciaCheckoutEntity p set p.estado = :novo, p.atualizadoEm = :agora where p.orcamentoId = :id and p.estado = :anterior")
    int mudarEstado(@Param("id") UUID id, @Param("anterior") EstadoPreferencia anterior,
            @Param("novo") EstadoPreferencia novo, @Param("agora") Instant agora);

    @Modifying(clearAutomatically = true)
    @Query("update PreferenciaCheckoutEntity p set p.estado = :novo, p.preferenciaId = :preferencia, p.checkoutUrl = :url, p.checkoutTesteUrl = :teste, p.atualizadoEm = :agora where p.orcamentoId = :id and p.estado = :anterior")
    int concluir(@Param("id") UUID id, @Param("anterior") EstadoPreferencia anterior, @Param("novo") EstadoPreferencia novo,
            @Param("preferencia") String preferencia, @Param("url") String url, @Param("teste") String teste, @Param("agora") Instant agora);
}
