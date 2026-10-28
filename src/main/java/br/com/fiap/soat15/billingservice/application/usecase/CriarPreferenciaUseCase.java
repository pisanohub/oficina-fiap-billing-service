package br.com.fiap.soat15.billingservice.application.usecase;

import br.com.fiap.soat15.billingservice.application.exception.*;
import br.com.fiap.soat15.billingservice.application.port.out.*;
import br.com.fiap.soat15.billingservice.domain.model.*;
import java.time.Clock;
import java.util.UUID;

/** Preparação local do checkout; não representa aprovação de orçamento nem confirmação de pagamento. */
public class CriarPreferenciaUseCase {
    private final ConsultarOrcamentoUseCase consultar;
    private final PreferenciaCheckoutRepository repository;
    private final PreferenciaPagamentoGateway gateway;
    private final Clock clock;

    public CriarPreferenciaUseCase(ConsultarOrcamentoUseCase consultar, PreferenciaCheckoutRepository repository,
            PreferenciaPagamentoGateway gateway, Clock clock) {
        this.consultar = consultar;
        this.repository = repository;
        this.gateway = gateway;
        this.clock = clock;
    }

    public PreferenciaCheckout executar(UUID orcamentoId) {
        Orcamento orcamento = consultar.executar(orcamentoId);
        if (orcamento.total().signum() <= 0) throw new IllegalArgumentException("Checkout requer orçamento com total positivo.");
        var existente = repository.buscar(orcamentoId);
        if (existente.isPresent()) {
            if (existente.get().estado() == EstadoPreferencia.CRIADA) return existente.get();
            if (existente.get().estado() != EstadoPreferencia.FALHA_DEFINITIVA) throw new PreferenciaBloqueadaException();
        }
        gateway.validarConfiguracao();
        if (!repository.tentarIniciar(orcamentoId, clock.instant())) {
            var concorrente = repository.buscar(orcamentoId);
            if (concorrente.isPresent() && concorrente.get().estado() == EstadoPreferencia.CRIADA) return concorrente.get();
            throw new PreferenciaBloqueadaException();
        }
        PreferenciaGerada resultado;
        try {
            resultado = gateway.criar(orcamento);
        } catch (IntegracaoPagamentoException ex) {
            repository.registrarFalha(orcamentoId, ex.resultadoIncerto(), clock.instant());
            throw ex;
        }
        // Sem transação de banco aberta durante a chamada externa.
        // Se esta gravação falhar, CRIANDO permanece e impede nova chamada cega após reinício.
        repository.concluir(orcamentoId, resultado, clock.instant());
        return new PreferenciaCheckout(orcamentoId, EstadoPreferencia.CRIADA,
                resultado.id(), resultado.checkoutUrl(), resultado.checkoutTesteUrl(), clock.instant());
    }
}
