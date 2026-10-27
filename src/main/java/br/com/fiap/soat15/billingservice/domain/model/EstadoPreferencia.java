package br.com.fiap.soat15.billingservice.domain.model;

/** Estado da solicitação de checkout, não do pagamento no provedor. */
public enum EstadoPreferencia {
    CRIANDO, CRIADA, FALHA_DEFINITIVA, RESULTADO_INCERTO
}
