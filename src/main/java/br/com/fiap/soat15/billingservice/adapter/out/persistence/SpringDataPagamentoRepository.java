package br.com.fiap.soat15.billingservice.adapter.out.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface SpringDataPagamentoRepository extends JpaRepository<PagamentoEntity, String> {
    List<PagamentoEntity> findAllByOrcamentoIdOrderByPagamentoIdAsc(UUID orcamentoId);
}
