package br.com.fiap.soat15.billingservice.application.port.out;

import br.com.fiap.soat15.billingservice.domain.model.Orcamento;
import java.util.Optional;
import java.util.UUID;

public interface OrcamentoRepository {
    Optional<Orcamento> buscarPorId(UUID id);
    Optional<Orcamento> buscarPorComandoId(UUID comandoId);
    Orcamento salvar(Orcamento orcamento);
}
