package br.com.fiap.soat15.billingservice.adapter.out.persistence;

import br.com.fiap.soat15.billingservice.application.exception.ConflitoPersistenciaException;
import br.com.fiap.soat15.billingservice.application.port.out.OrcamentoRepository;
import br.com.fiap.soat15.billingservice.domain.model.Orcamento;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.Optional;
import java.util.UUID;

@Repository
public class OrcamentoPersistenceAdapter implements OrcamentoRepository {
    private final SpringDataOrcamentoRepository repository;
    private final TransactionTemplate transaction;

    public OrcamentoPersistenceAdapter(SpringDataOrcamentoRepository repository, PlatformTransactionManager manager) {
        this.repository = repository;
        transaction = new TransactionTemplate(manager);
    }

    @Override
    public Optional<Orcamento> buscarPorId(UUID id) {
        return repository.findById(id).map(OrcamentoEntity::paraDominio);
    }

    @Override
    public Optional<Orcamento> buscarPorComandoId(UUID comandoId) {
        return repository.findByComandoId(comandoId).map(OrcamentoEntity::paraDominio);
    }

    @Override
    public Orcamento salvar(Orcamento orcamento) {
        try {
            return transaction.execute(status -> repository.saveAndFlush(new OrcamentoEntity(orcamento)).paraDominio());
        } catch (DataIntegrityViolationException ex) {
            throw new ConflitoPersistenciaException(ex);
        }
    }
}
