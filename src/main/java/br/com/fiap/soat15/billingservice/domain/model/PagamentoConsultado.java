package br.com.fiap.soat15.billingservice.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Snapshot mínimo retornado pela API; não armazena dados pessoais ou de cartão. */
public record PagamentoConsultado(String id, String referenciaExterna, String status,
        String statusDetalhe, BigDecimal valor, String moeda, BigDecimal valorEstornado,
        Instant atualizadoNoProvedor) {
    public PagamentoConsultado {
        validarId(id);
        if (referenciaExterna == null || referenciaExterna.isBlank() || referenciaExterna.length() > 100
                || status == null || status.isBlank() || status.length() > 40
                || statusDetalhe != null && statusDetalhe.length() > 100
                || moeda == null || moeda.length() != 3 || atualizadoNoProvedor == null) {
            throw new IllegalArgumentException("Resposta financeira incompleta.");
        }
        valor = ItemOrcamento.dinheiro(valor);
        valorEstornado = ItemOrcamento.dinheiro(valorEstornado);
        if (valor.signum() <= 0 || valorEstornado.compareTo(valor) > 0) {
            throw new IllegalArgumentException("Valores financeiros inconsistentes.");
        }
    }

    public static void validarId(String id) {
        if (id == null || !id.matches("[1-9][0-9]{0,24}")) {
            throw new IllegalArgumentException("Informe um identificador numérico positivo de pagamento.");
        }
    }

    public EstadoPagamento estado() {
        return switch (status) {
            case "approved" -> valorEstornado.compareTo(valor) == 0 ? EstadoPagamento.ESTORNADO
                    : valorEstornado.signum() > 0 || "partially_refunded".equals(statusDetalhe)
                    ? EstadoPagamento.PARCIALMENTE_ESTORNADO : EstadoPagamento.APROVADO;
            case "pending" -> EstadoPagamento.PENDENTE;
            case "in_process" -> EstadoPagamento.EM_PROCESSAMENTO;
            case "authorized" -> EstadoPagamento.AUTORIZADO;
            case "rejected" -> EstadoPagamento.RECUSADO;
            case "cancelled" -> EstadoPagamento.CANCELADO;
            case "refunded" -> EstadoPagamento.ESTORNADO;
            case "charged_back" -> EstadoPagamento.CONTESTADO;
            case "in_mediation" -> EstadoPagamento.EM_MEDIACAO;
            default -> EstadoPagamento.DESCONHECIDO;
        };
    }
}
