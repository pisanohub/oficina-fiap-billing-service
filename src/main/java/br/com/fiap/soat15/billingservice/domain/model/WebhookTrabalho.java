package br.com.fiap.soat15.billingservice.domain.model;

import java.util.UUID;

/** Reserva temporária de processamento; o token impede conclusão por worker vencido. */
public record WebhookTrabalho(String entregaId, String pagamentoId, UUID reserva, int tentativa) { }
