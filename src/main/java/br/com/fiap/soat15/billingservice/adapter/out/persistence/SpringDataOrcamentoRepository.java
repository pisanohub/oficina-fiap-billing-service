package br.com.fiap.soat15.billingservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataOrcamentoRepository extends JpaRepository<OrcamentoEntity, UUID> {
    Optional<OrcamentoEntity> findByComandoId(UUID comandoId);
}
