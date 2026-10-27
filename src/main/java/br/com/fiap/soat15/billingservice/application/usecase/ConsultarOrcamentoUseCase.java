package br.com.fiap.soat15.billingservice.application.usecase;

import br.com.fiap.soat15.billingservice.application.exception.OrcamentoNaoEncontradoException;
import br.com.fiap.soat15.billingservice.application.port.out.OrcamentoRepository;
import br.com.fiap.soat15.billingservice.domain.model.Orcamento;
import java.util.UUID;

public class ConsultarOrcamentoUseCase {
    private final OrcamentoRepository repository;

    public ConsultarOrcamentoUseCase(OrcamentoRepository repository) {
        this.repository = repository;
    }

    public Orcamento executar(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("Identificador do orçamento é obrigatório.");
        }
        return repository.buscarPorId(id).orElseThrow(OrcamentoNaoEncontradoException::new);
    }
}
