package br.com.fiap.soat15.billingservice.adapter.out.persistence;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ComandosFinanceirosJpa extends JpaRepository<ComandoFinanceiroEntity, UUID> {
    Optional<ComandoFinanceiroEntity> findByChaveOperacao(String chave);
    List<ComandoFinanceiroEntity> findTop20ByEstadoOrderByComandoIdAsc(String estado);
}
