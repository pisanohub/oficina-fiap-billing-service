package br.com.fiap.soat15.billingservice.adapter.out.persistence;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EventosFinanceirosJpa extends JpaRepository<EventoFinanceiroEntity, UUID> {
    List<EventoFinanceiroEntity> findTop20ByEnviadoFalseOrderByMessageIdAsc();
}
