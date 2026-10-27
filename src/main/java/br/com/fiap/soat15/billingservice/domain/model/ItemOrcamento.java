package br.com.fiap.soat15.billingservice.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Snapshot do catálogo: mudanças posteriores de preço não alteram o orçamento. */
public record ItemOrcamento(String referencia, TipoItem tipo, String descricao,
                            int quantidade, BigDecimal valorUnitario) {
    public ItemOrcamento {
        referencia = texto(referencia, "Referência", 80);
        descricao = texto(descricao, "Descrição", 255);
        if (tipo == null) {
            throw new IllegalArgumentException("Tipo do item é obrigatório.");
        }
        if (quantidade <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser positiva.");
        }
        valorUnitario = dinheiro(valorUnitario);
        dinheiro(valorUnitario.multiply(BigDecimal.valueOf(quantidade)));
    }

    public BigDecimal subtotal() {
        return valorUnitario.multiply(BigDecimal.valueOf(quantidade));
    }

    static BigDecimal dinheiro(BigDecimal valor) {
        if (valor == null || valor.signum() < 0) {
            throw new IllegalArgumentException("Valor monetário deve ser informado e não negativo.");
        }
        try {
            BigDecimal normalizado = valor.setScale(2, RoundingMode.UNNECESSARY);
            if (normalizado.precision() > 19) {
                throw new IllegalArgumentException("Valor monetário excede o limite suportado.");
            }
            return normalizado;
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Valor monetário deve ter no máximo duas casas decimais.", ex);
        }
    }

    private static String texto(String valor, String campo, int limite) {
        if (valor == null || valor.isBlank() || valor.strip().length() > limite) {
            throw new IllegalArgumentException(campo + " é obrigatório e deve respeitar o limite de " + limite + " caracteres.");
        }
        return valor.strip();
    }
}
