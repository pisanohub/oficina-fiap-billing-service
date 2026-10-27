package br.com.fiap.soat15.billingservice.application.port.out;

import br.com.fiap.soat15.billingservice.domain.model.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PreferenciaCheckoutRepository {
    Optional<PreferenciaCheckout> buscar(UUID orcamentoId);
    /** Reserva atômica; só inexistente ou falha comprovadamente definitiva permite nova chamada. */
    boolean tentarIniciar(UUID orcamentoId, Instant agora);
    void concluir(UUID orcamentoId, PreferenciaGerada preferencia, Instant agora);
    void registrarFalha(UUID orcamentoId, boolean resultadoIncerto, Instant agora);
}
