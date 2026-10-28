package br.com.fiap.soat15.billingservice.domain.model;

import java.time.Instant;
import java.util.UUID;

public record PreferenciaCheckout(UUID orcamentoId, EstadoPreferencia estado,
        String preferenciaId, String checkoutUrl, String checkoutTesteUrl, Instant atualizadoEm) {
    public PreferenciaCheckout {
        if (orcamentoId == null || estado == null || atualizadoEm == null) {
            throw new IllegalArgumentException("Orçamento, estado e data da preferência são obrigatórios.");
        }
        if (estado == EstadoPreferencia.CRIADA) {
            new PreferenciaGerada(preferenciaId, checkoutUrl, checkoutTesteUrl);
        } else if (preferenciaId != null || checkoutUrl != null || checkoutTesteUrl != null) {
            throw new IllegalArgumentException("Somente preferência criada pode conter link de checkout.");
        }
    }
}
