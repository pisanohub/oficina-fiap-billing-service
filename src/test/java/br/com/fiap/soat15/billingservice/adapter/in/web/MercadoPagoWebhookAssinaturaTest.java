package br.com.fiap.soat15.billingservice.adapter.in.web;

import br.com.fiap.soat15.billingservice.config.WebhookProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.HexFormat;
import static org.assertj.core.api.Assertions.*;

public class MercadoPagoWebhookAssinaturaTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final Instant AGORA = Instant.parse("2026-10-29T12:00:00Z");
    private final MercadoPagoWebhookAssinatura validator = new MercadoPagoWebhookAssinatura(
            new WebhookProperties(true, SECRET, Duration.ofMinutes(5)), Clock.fixed(AGORA, ZoneOffset.UTC));

    public static String assinar(String id, String request, String ts) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String texto = "id:" + id + ";request-id:" + request + ";ts:" + ts + ";";
            return "ts=" + ts + ",v1=" + HexFormat.of().formatHex(mac.doFinal(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new AssertionError(ex); }
    }
    @Test void aceitaSegundosMilissegundosOrdemDosCamposERepeticao() {
        // Vetor conferido independentemente com HMACSHA256 do .NET (segredo fictício).
        assertThat(assinar("123", "req-1", "1793275200"))
                .isEqualTo("ts=1793275200,v1=3b7fe5ea247e2181a1c1601180d9acb7117ce1127555ab1ddc7828c1d2077876");
        for (String ts : new String[]{"" + AGORA.getEpochSecond(), "" + AGORA.toEpochMilli()}) {
            String signature = assinar("123", "req-1", ts);
            String id = validator.validar("123", "req-1", signature);
            assertThat(id).matches("[a-f0-9]{64}");
            assertThat(validator.validar("123", "req-1", signature)).isEqualTo(id);
            String[] campos = signature.split(",");
            assertThat(validator.validar("123", "req-1", campos[1] + ", " + campos[0])).isEqualTo(id);
        }
    }
    @ParameterizedTest @NullAndEmptySource
    @ValueSource(strings = {"x", "ts=1,v1=abc", "ts=1234567890", "v1=abc", "ts=1234567890,ts=1234567890,v1=abc", "ts=x=v,v1=abc", ",", "ts=1234567890,v1=zzzz"})
    void rejeitaHeaderInvalido(String header) {
        assertThatThrownBy(() -> validator.validar("123", "req-1", header)).isInstanceOf(AssinaturaWebhookInvalidaException.class);
    }
    @Test void rejeitaAusenciasTrocaDeIdRequestHashETamanhoExcessivo() {
        String ts = "" + AGORA.getEpochSecond();
        String signature = assinar("123", "req-1", ts);
        for (String id : new String[]{"0", "../123", "123,124", "124", ""}) {
            assertThatThrownBy(() -> validator.validar(id, "req-1", signature)).isInstanceOf(AssinaturaWebhookInvalidaException.class);
        }
        assertThatThrownBy(() -> validator.validar(null, "req-1", signature)).isInstanceOf(AssinaturaWebhookInvalidaException.class);
        for (String req : new String[]{"req-2", "req;ts:1", "", "a".repeat(129)}) {
            assertThatThrownBy(() -> validator.validar("123", req, signature)).isInstanceOf(AssinaturaWebhookInvalidaException.class);
        }
        assertThatThrownBy(() -> validator.validar("123", null, signature)).isInstanceOf(AssinaturaWebhookInvalidaException.class);
        assertThatThrownBy(() -> validator.validar("123", "req-1", "x".repeat(513))).isInstanceOf(AssinaturaWebhookInvalidaException.class);
        assertThatThrownBy(() -> validator.validar("123", "req-1", "ts=" + ts + ",v1=" + "0".repeat(64)))
                .isInstanceOf(AssinaturaWebhookInvalidaException.class);
        assertThatThrownBy(() -> validator.validar("123", "req-1", signature + ",v1=" + "0".repeat(64)))
                .isInstanceOf(AssinaturaWebhookInvalidaException.class);
    }
    @Test void rejeitaReplayAntigoEFuturoEAceitaLimites() {
        for (long delta : new long[]{-301, 31, -86400}) {
            String ts = "" + AGORA.plusSeconds(delta).getEpochSecond();
            assertThatThrownBy(() -> validator.validar("123", "req-1", assinar("123", "req-1", ts)))
                    .isInstanceOf(AssinaturaWebhookInvalidaException.class);
        }
        for (long delta : new long[]{-300, 30}) {
            String ts = "" + AGORA.plusSeconds(delta).getEpochSecond();
            assertThat(validator.validar("123", "req-1", assinar("123", "req-1", ts))).isNotEmpty();
        }
    }
    @Test void exigeConfiguracaoSemExporSegredo() {
        for (var props : new WebhookProperties[]{
                new WebhookProperties(false, SECRET, Duration.ofMinutes(5)),
                new WebhookProperties(true, null, Duration.ofMinutes(5)),
                new WebhookProperties(true, "curto", Duration.ofMinutes(5)),
                new WebhookProperties(true, "a".repeat(513), Duration.ofMinutes(5)),
                new WebhookProperties(true, SECRET + " ", Duration.ofMinutes(5)),
                new WebhookProperties(true, SECRET, null),
                new WebhookProperties(true, SECRET, Duration.ZERO),
                new WebhookProperties(true, SECRET, Duration.ofSeconds(-1)),
                new WebhookProperties(true, SECRET, Duration.ofDays(2))}) {
            assertThatThrownBy(props::validar).isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining(SECRET);
        }
        var props = new WebhookProperties(true, SECRET, Duration.ofMinutes(5));
        assertThat(props.toString()).doesNotContain(SECRET).contains("REDACTED");
    }
}
