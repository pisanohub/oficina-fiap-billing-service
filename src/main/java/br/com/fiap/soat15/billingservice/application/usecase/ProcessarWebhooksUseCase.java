package br.com.fiap.soat15.billingservice.application.usecase;

import br.com.fiap.soat15.billingservice.application.exception.OrcamentoNaoEncontradoException;
import br.com.fiap.soat15.billingservice.application.port.out.WebhookInbox;
import br.com.fiap.soat15.billingservice.domain.exception.PagamentoIncompativelException;
import java.time.Clock;

public class ProcessarWebhooksUseCase {
    private final WebhookInbox inbox;
    private final VerificarPagamentoUseCase verificar;
    private final Clock clock;

    public ProcessarWebhooksUseCase(WebhookInbox inbox, VerificarPagamentoUseCase verificar, Clock clock) {
        this.inbox = inbox; this.verificar = verificar; this.clock = clock;
    }

    public void executar() {
        for (String id : inbox.pendentes(clock.instant())) {
            var reserva = inbox.reservar(id, clock.instant());
            if (reserva.isEmpty()) continue;
            var trabalho = reserva.get();
            try {
                verificar.executarNotificacao(trabalho.pagamentoId());
            } catch (PagamentoIncompativelException | OrcamentoNaoEncontradoException ex) {
                inbox.falhar(trabalho, clock.instant(), true, "VINCULO_FINANCEIRO_INVALIDO");
                continue;
            } catch (RuntimeException ex) {
                // Não salvar mensagem/cause: podem conter URL, token ou dados do provedor.
                inbox.falhar(trabalho, clock.instant(), false, "FALHA_CONSULTA_OU_PERSISTENCIA");
                continue;
            }
            inbox.concluir(trabalho, clock.instant());
        }
    }
}
