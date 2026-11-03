package br.com.fiap.soat15.billingservice.adapter.out.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface SpringDataCotacaoOrcamentoRepository extends JpaRepository<CotacaoOrcamentoEntity, UUID> { }
