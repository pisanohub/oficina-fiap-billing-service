package br.com.fiap.soat15.billingservice.adapter.out.persistence;

import br.com.fiap.soat15.billingservice.application.exception.*;
import br.com.fiap.soat15.billingservice.application.port.out.OrcamentoCotadoRepository;
import br.com.fiap.soat15.billingservice.domain.model.OrcamentoCotado;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

@Repository
public class OrcamentoCotadoPersistenceAdapter implements OrcamentoCotadoRepository {
    private final SpringDataOrcamentoRepository orcamentos;
    private final SpringDataCotacaoOrcamentoRepository cotacoes;
    private final TransactionTemplate transaction;
    public OrcamentoCotadoPersistenceAdapter(SpringDataOrcamentoRepository orcamentos,
            SpringDataCotacaoOrcamentoRepository cotacoes, PlatformTransactionManager manager) {
        this.orcamentos = orcamentos; this.cotacoes = cotacoes; transaction = new TransactionTemplate(manager);
    }
    public Optional<OrcamentoCotado> buscarPorComandoId(UUID comandoId) {
        return transaction.execute(status -> orcamentos.findByComandoId(comandoId).map(entidade -> {
            var orcamento = entidade.paraDominio();
            // Não tratar como cotado um orçamento antigo criado com preços enviados manualmente.
            return cotacoes.findById(orcamento.id()).orElseThrow(ComandoConflitanteException::new).paraDominio(orcamento);
        }));
    }
    public OrcamentoCotado salvar(OrcamentoCotado novo) {
        try {
            return transaction.execute(status -> {
                var orcamento = orcamentos.saveAndFlush(new OrcamentoEntity(novo.orcamento())).paraDominio();
                return cotacoes.saveAndFlush(new CotacaoOrcamentoEntity(novo)).paraDominio(orcamento);
            });
        } catch (DataIntegrityViolationException ex) { throw new ConflitoPersistenciaException(ex); }
    }
}
