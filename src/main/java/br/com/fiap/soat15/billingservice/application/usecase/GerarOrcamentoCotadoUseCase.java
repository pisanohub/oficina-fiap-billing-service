package br.com.fiap.soat15.billingservice.application.usecase;

import br.com.fiap.soat15.billingservice.application.exception.*;
import br.com.fiap.soat15.billingservice.application.port.out.*;
import br.com.fiap.soat15.billingservice.domain.model.*;
import java.time.Clock;
import java.util.UUID;

public class GerarOrcamentoCotadoUseCase {
    private final OrcamentoCotadoRepository repository;
    private final CotacaoGateway gateway;
    private final Clock clock;
    public GerarOrcamentoCotadoUseCase(OrcamentoCotadoRepository repository, CotacaoGateway gateway, Clock clock) {
        this.repository = repository; this.gateway = gateway; this.clock = clock;
    }
    public OrcamentoCotado executar(GerarOrcamentoCotadoCommand comando) {
        if (comando == null) throw new IllegalArgumentException("Comando é obrigatório.");
        var existente = repository.buscarPorComandoId(comando.comandoId());
        if (existente.isPresent()) return conferirRepeticao(existente.get(), comando);
        var cotacao = gateway.consultar(comando.itens());
        if (cotacao == null) throw new IntegracaoCotacaoException(false);
        OrcamentoCotado novo;
        try {
            var snapshot = cotacao.conferir(comando.itens());
            novo = new OrcamentoCotado(new Orcamento(UUID.randomUUID(), comando.comandoId(), comando.ordemId(),
                    comando.clienteId(), snapshot, clock.instant()), comando.versaoDiagnostico(), cotacao.geradaEm());
        } catch (IllegalArgumentException ex) { throw new IntegracaoCotacaoException(false); }
        try {
            return repository.salvar(novo);
        } catch (ConflitoPersistenciaException ex) {
            var concorrente = repository.buscarPorComandoId(comando.comandoId());
            if (concorrente.isEmpty()) throw ex;
            // Primeira gravação confirmada vence, mesmo se o catálogo mudou entre consultas concorrentes.
            return conferirRepeticao(concorrente.get(), comando);
        }
    }
    private OrcamentoCotado conferirRepeticao(OrcamentoCotado salvo, GerarOrcamentoCotadoCommand comando) {
        if (!salvo.corresponde(comando.ordemId(), comando.clienteId(), comando.versaoDiagnostico(), comando.itens())) {
            throw new ComandoConflitanteException();
        }
        return salvo;
    }
}
