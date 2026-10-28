package br.com.fiap.soat15.billingservice.application.port.out;

import br.com.fiap.soat15.billingservice.domain.model.Pagamento;
import java.util.List;
import java.util.UUID;

public interface PagamentoRepository {
    Pagamento registrar(Pagamento pagamento);
    List<Pagamento> listarPorOrcamento(UUID orcamentoId);
}
