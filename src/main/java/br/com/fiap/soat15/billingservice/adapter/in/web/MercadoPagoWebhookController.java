package br.com.fiap.soat15.billingservice.adapter.in.web;

import br.com.fiap.soat15.billingservice.application.port.out.WebhookInbox;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.*;
import java.time.Clock;
import java.util.Collections;

@RestController
@ConditionalOnProperty(name = "mercadopago.webhook.enabled", havingValue = "true")
@RequestMapping("/webhooks/mercadopago")
public class MercadoPagoWebhookController {
    private final MercadoPagoWebhookAssinatura assinatura;
    private final WebhookInbox inbox;
    private final Clock clock;
    public MercadoPagoWebhookController(MercadoPagoWebhookAssinatura assinatura, WebhookInbox inbox, Clock clock) {
        this.assinatura = assinatura; this.inbox = inbox; this.clock = clock;
    }
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> receber(HttpServletRequest request, @RequestBody Notificacao corpo) {
        String id = parametroUnico(request, "data.id");
        String entrega = assinatura.validar(id, headerUnico(request, "x-request-id"), headerUnico(request, "x-signature"));
        if (corpo == null || !"payment".equals(corpo.type()) || corpo.data() == null || !id.equals(corpo.data().id())) {
            return ResponseEntity.badRequest().build();
        }
        inbox.receber(entrega, id, clock.instant());
        // 200 somente após COMMIT da inbox; nenhuma chamada ao provedor nesta thread HTTP.
        return ResponseEntity.ok().build();
    }
    private String parametroUnico(HttpServletRequest request, String nome) {
        String[] valores = request.getParameterValues(nome);
        if (valores == null || valores.length != 1) throw new AssinaturaWebhookInvalidaException();
        return valores[0];
    }
    private String headerUnico(HttpServletRequest request, String nome) {
        var valores = Collections.list(request.getHeaders(nome));
        if (valores.size() != 1) throw new AssinaturaWebhookInvalidaException();
        return valores.getFirst();
    }
    @ExceptionHandler(AssinaturaWebhookInvalidaException.class)
    ResponseEntity<Void> assinaturaInvalida() { return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build(); }
    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ResponseEntity<Void> indisponivel() { return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build(); }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Notificacao(String type, Dados data) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Dados(String id) { }
}
