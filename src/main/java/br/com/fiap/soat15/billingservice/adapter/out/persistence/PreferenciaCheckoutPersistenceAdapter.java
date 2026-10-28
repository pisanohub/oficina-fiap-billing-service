package br.com.fiap.soat15.billingservice.adapter.out.persistence;

import br.com.fiap.soat15.billingservice.application.port.out.PreferenciaCheckoutRepository;
import br.com.fiap.soat15.billingservice.domain.model.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PreferenciaCheckoutPersistenceAdapter implements PreferenciaCheckoutRepository {
    private final SpringDataPreferenciaRepository repository;
    private final EntityManager entityManager;
    private final TransactionTemplate transaction;

    public PreferenciaCheckoutPersistenceAdapter(SpringDataPreferenciaRepository repository,
            EntityManager entityManager, PlatformTransactionManager manager) {
        this.repository = repository;
        this.entityManager = entityManager;
        transaction = new TransactionTemplate(manager);
    }

    public Optional<PreferenciaCheckout> buscar(UUID id) { return repository.findById(id).map(PreferenciaCheckoutEntity::paraDominio); }

    public boolean tentarIniciar(UUID id, Instant agora) {
        try {
            return Boolean.TRUE.equals(transaction.execute(status -> {
                if (repository.mudarEstado(id, EstadoPreferencia.FALHA_DEFINITIVA, EstadoPreferencia.CRIANDO, agora) == 1) return true;
                if (repository.existsById(id)) return false;
                // persist, não merge: duas instâncias não podem sobrescrever a reserva uma da outra.
                entityManager.persist(new PreferenciaCheckoutEntity(id, agora));
                entityManager.flush();
                return true;
            }));
        } catch (DataIntegrityViolationException ex) {
            return false;
        } catch (jakarta.persistence.PersistenceException ex) {
            // Hibernate pode propagar a violação do INSERT diretamente pelo EntityManager.
            if (buscar(id).isPresent()) return false;
            throw ex;
        }
    }

    public void concluir(UUID id, PreferenciaGerada preferencia, Instant agora) {
        Integer alterados = transaction.execute(status -> repository.concluir(id, EstadoPreferencia.CRIANDO,
                EstadoPreferencia.CRIADA, preferencia.id(), preferencia.checkoutUrl(), preferencia.checkoutTesteUrl(), agora));
        exigirAlteracao(alterados);
    }

    public void registrarFalha(UUID id, boolean incerto, Instant agora) {
        Integer alterados = transaction.execute(status -> repository.mudarEstado(id, EstadoPreferencia.CRIANDO,
                incerto ? EstadoPreferencia.RESULTADO_INCERTO : EstadoPreferencia.FALHA_DEFINITIVA, agora));
        exigirAlteracao(alterados);
    }

    private void exigirAlteracao(Integer quantidade) {
        if (quantidade == null || quantidade != 1) throw new IllegalStateException("Reserva de preferência não encontrada ou já finalizada.");
    }
}
