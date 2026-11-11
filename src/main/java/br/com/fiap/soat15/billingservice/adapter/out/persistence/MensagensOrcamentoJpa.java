package br.com.fiap.soat15.billingservice.adapter.out.persistence;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MensagensOrcamentoJpa extends JpaRepository<MensagemOrcamentoEntity, UUID> {
    List<MensagemOrcamentoEntity> findTop20ByEnviadoFalseAndResultadoIsNotNullOrderByComandoIdAsc();
}
