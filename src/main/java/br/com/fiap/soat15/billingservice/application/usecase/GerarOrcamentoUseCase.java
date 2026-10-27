package br.com.fiap.soat15.billingservice.application.usecase;

import br.com.fiap.soat15.billingservice.application.exception.ComandoConflitanteException;
import br.com.fiap.soat15.billingservice.application.exception.ConflitoPersistenciaException;
import br.com.fiap.soat15.billingservice.application.port.out.OrcamentoRepository;
import br.com.fiap.soat15.billingservice.domain.model.Orcamento;
import java.time.Clock;
import java.util.UUID;

public class GerarOrcamentoUseCase {
    private final OrcamentoRepository repository;
    private final Clock clock;

    public GerarOrcamentoUseCase(OrcamentoRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Orcamento executar(GerarOrcamentoCommand comando) {
        if (comando == null) {
            throw new IllegalArgumentException("Comando é obrigatório.");
        }
        var novo = new Orcamento(UUID.randomUUID(), comando.comandoId(), comando.ordemId(),
                comando.clienteId(), comando.itens(), clock.instant());
        var existente = repository.buscarPorComandoId(novo.comandoId());
        if (existente.isPresent()) {
            return verificarRepeticao(existente.get(), novo);
        }
        try {
            return repository.salvar(novo);
        } catch (ConflitoPersistenciaException ex) {
            // Outra requisição pode ter confirmado o mesmo comando entre a consulta e o INSERT.
            // A transação de escrita já foi revertida pelo adapter; reler em uma nova transação.
            var concorrente = repository.buscarPorComandoId(novo.comandoId());
            if (concorrente.isEmpty()) {
                throw ex;
            }
            return verificarRepeticao(concorrente.get(), novo);
        }
    }

    private Orcamento verificarRepeticao(Orcamento existente, Orcamento novo) {
        if (!existente.corresponde(novo.ordemId(), novo.clienteId(), novo.itens())) {
            throw new ComandoConflitanteException();
        }
        return existente;
    }
}
