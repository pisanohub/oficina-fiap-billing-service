package br.com.fiap.soat15.billingservice.adapter.out.persistence;

import br.com.fiap.soat15.billingservice.application.port.out.PagamentoRepository;
import br.com.fiap.soat15.billingservice.application.exception.OrcamentoNaoEncontradoException;
import br.com.fiap.soat15.billingservice.domain.exception.PagamentoIncompativelException;
import br.com.fiap.soat15.billingservice.domain.model.Pagamento;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.UUID;

@Repository
public class PagamentoPersistenceAdapter implements PagamentoRepository {
    private final SpringDataPagamentoRepository repository;
    private final SpringDataOrcamentoRepository orcamentos;
    private final TransactionTemplate transaction;
    public PagamentoPersistenceAdapter(SpringDataPagamentoRepository repository,
            SpringDataOrcamentoRepository orcamentos, PlatformTransactionManager manager) {
        this.repository = repository; this.orcamentos = orcamentos;
        transaction = new TransactionTemplate(manager);
    }
    public Pagamento registrar(Pagamento pagamento) {
        try {
            return transaction.execute(status -> {
                // Lock curto, somente durante persistência; consulta HTTP ocorre antes.
                orcamentos.bloquearPorId(pagamento.orcamentoId()).orElseThrow(OrcamentoNaoEncontradoException::new);
                var existente = repository.findById(pagamento.dados().id());
                if (existente.isPresent()) {
                    var atual = existente.get().paraDominio();
                    var novo = atual.atualizarCom(pagamento);
                    if (novo == atual) return atual;
                    existente.get().atualizar(novo);
                    return repository.saveAndFlush(existente.get()).paraDominio();
                }
                return repository.saveAndFlush(new PagamentoEntity(pagamento)).paraDominio();
            });
        } catch (DataIntegrityViolationException ex) {
            if (repository.existsById(pagamento.dados().id())) {
                throw new PagamentoIncompativelException("Operação financeira já registrada; confira o orçamento e consulte novamente.");
            }
            throw ex;
        }
    }
    public List<Pagamento> listarPorOrcamento(UUID id) {
        return repository.findAllByOrcamentoIdOrderByPagamentoIdAsc(id).stream().map(PagamentoEntity::paraDominio).toList();
    }
}
