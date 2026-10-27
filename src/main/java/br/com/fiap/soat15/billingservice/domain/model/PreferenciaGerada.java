package br.com.fiap.soat15.billingservice.domain.model;

import java.net.URI;

/** Somente dados necessários para abrir o checkout; não contém credenciais. */
public record PreferenciaGerada(String id, String checkoutUrl, String checkoutTesteUrl) {
    public PreferenciaGerada {
        if (id == null || id.isBlank() || id.length() > 120) {
            throw new IllegalArgumentException("Identificador de preferência inválido.");
        }
        validarUrl(checkoutUrl);
        if (checkoutTesteUrl != null) validarUrl(checkoutTesteUrl);
    }

    private static void validarUrl(String url) {
        if (url == null || url.length() > 2000) throw new IllegalArgumentException("URL de checkout inválida.");
        URI uri = URI.create(url);
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || host == null
                || !(host.equals("mercadopago.com") || host.endsWith(".mercadopago.com")
                || host.equals("mercadopago.com.br") || host.endsWith(".mercadopago.com.br"))) {
            throw new IllegalArgumentException("URL de checkout deve pertencer ao Mercado Pago e usar HTTPS.");
        }
    }
}
