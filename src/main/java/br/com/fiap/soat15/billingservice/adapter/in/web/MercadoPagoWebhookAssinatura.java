package br.com.fiap.soat15.billingservice.adapter.in.web;

import br.com.fiap.soat15.billingservice.config.WebhookProperties;
import br.com.fiap.soat15.billingservice.domain.model.PagamentoConsultado;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

/** Checkout Pro payment: assina data.id da QUERY, x-request-id e ts; não assina o corpo JSON. */
public class MercadoPagoWebhookAssinatura {
    private final WebhookProperties properties;
    private final Clock clock;
    public MercadoPagoWebhookAssinatura(WebhookProperties properties, Clock clock) {
        properties.validar(); this.properties = properties; this.clock = clock;
    }
    public String validar(String dataId, String requestId, String signature) {
        try {
            PagamentoConsultado.validarId(dataId);
            if (requestId == null || !requestId.matches("[A-Za-z0-9_-]{1,128}") || signature == null || signature.length() > 512) {
                throw new AssinaturaWebhookInvalidaException();
            }
            Map<String, String> partes = new HashMap<>();
            for (String parte : signature.split(",", -1)) {
                String[] par = parte.trim().split("=", -1);
                if (par.length != 2 || partes.putIfAbsent(par[0], par[1]) != null) throw new AssinaturaWebhookInvalidaException();
            }
            String ts = partes.get("ts");
            String v1 = partes.get("v1");
            if (ts == null || !ts.matches("[0-9]{10}|[0-9]{13}") || v1 == null || !v1.matches("[0-9a-fA-F]{64}")) {
                throw new AssinaturaWebhookInvalidaException();
            }
            long valor = Long.parseLong(ts);
            Instant emitido = ts.length() == 13 ? Instant.ofEpochMilli(valor) : Instant.ofEpochSecond(valor);
            Instant agora = clock.instant();
            if (emitido.isBefore(agora.minus(properties.tolerance())) || emitido.isAfter(agora.plusSeconds(30))) {
                throw new AssinaturaWebhookInvalidaException();
            }
            String manifesto = "id:" + dataId + ";request-id:" + requestId + ";ts:" + ts + ";";
            byte[] conteudo = manifesto.getBytes(StandardCharsets.UTF_8);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            if (!MessageDigest.isEqual(mac.doFinal(conteudo), HexFormat.of().parseHex(v1))) {
                throw new AssinaturaWebhookInvalidaException();
            }
            // Identificador opaco: não persistir assinatura, segredo ou corpo do aviso.
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo));
        } catch (IllegalArgumentException ex) {
            throw new AssinaturaWebhookInvalidaException();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Validação criptográfica indisponível.");
        }
    }
}
