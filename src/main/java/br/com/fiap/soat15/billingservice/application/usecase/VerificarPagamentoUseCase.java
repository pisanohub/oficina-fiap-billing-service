package br.com.fiap.soat15.billingservice.application.usecase;

import br.com.fiap.soat15.billingservice.application.exception.IntegracaoPagamentoException;
import br.com.fiap.soat15.billingservice.application.port.out.*;
import br.com.fiap.soat15.billingservice.domain.exception.PagamentoIncompativelException;
import br.com.fiap.soat15.billingservice.domain.model.*;
import java.time.Clock;
import java.util.UUID;

public class VerificarPagamentoUseCase {
    private final ConsultarOrcamentoUseCase consultar;
    private final PreferenciaCheckoutRepository preferencias;
    private final PagamentoGateway gateway;
    private final PagamentoRepository repository;
    private final Clock clock;

    public VerificarPagamentoUseCase(ConsultarOrcamentoUseCase consultar, PreferenciaCheckoutRepository preferencias,
            PagamentoGateway gateway, PagamentoRepository repository, Clock clock) {
        this.consultar = consultar;
        this.preferencias = preferencias;
        this.gateway = gateway;
        this.repository = repository;
        this.clock = clock;
    }

    public Pagamento executar(UUID orcamentoId, String pagamentoId) {
        PagamentoConsultado.validarId(pagamentoId);
        Orcamento orcamento = consultar.executar(orcamentoId);
        if (preferencias.buscar(orcamentoId).filter(p -> p.estado() == EstadoPreferencia.CRIADA).isEmpty()) {
            throw new PagamentoIncompativelException("O orçamento ainda não tem preferência criada e registrada.");
        }
        PagamentoConsultado resposta = gateway.consultar(pagamentoId);
        if (!pagamentoId.equals(resposta.id())) {
            throw new IntegracaoPagamentoException("Provedor retornou outra operação financeira.", false, false);
        }
        if (!orcamentoId.toString().equals(resposta.referenciaExterna()) || !"BRL".equals(resposta.moeda())
                || orcamento.total().compareTo(resposta.valor()) != 0) {
            throw new PagamentoIncompativelException("Referência, valor ou moeda do pagamento não correspondem ao orçamento.");
        }
        return repository.registrar(new Pagamento(orcamentoId, resposta, clock.instant()));
    }
}
