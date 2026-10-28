package br.com.fiap.soat15.billingservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataOrcamentoRepository extends JpaRepository<OrcamentoEntity, UUID> {
    Optional<OrcamentoEntity> findByComandoId(UUID comandoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrcamentoEntity o where o.id = :id")
    Optional<OrcamentoEntity> bloquearPorId(@Param("id") UUID id);
}
