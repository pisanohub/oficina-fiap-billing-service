package br.com.fiap.soat15.billingservice.adapter.in.sqs;

import br.com.fiap.soat15.billingservice.adapter.out.persistence.*;
import br.com.fiap.soat15.billingservice.application.usecase.ProcessarComandoPagamentoUseCase;
import java.util.*;
import org.junit.jupiter.api.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import static org.mockito.Mockito.*;

class ComandosPagamentoWorkerTest {
    final SqsClient sqs = mock(SqsClient.class);
    final RegistroFinanceiro registro = mock(RegistroFinanceiro.class);
    final ProcessarComandoPagamentoUseCase processar = mock(ProcessarComandoPagamentoUseCase.class);
    final ComandosPagamentoWorker worker = new ComandosPagamentoWorker(sqs, "entrada", "saida", processar, registro);
    @BeforeEach void preparar() {
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(ReceiveMessageResponse.builder()
                .messages(Message.builder().body("{}").receiptHandle("recibo").build()).build());
    }
    @Test void confirmaRecebimentoSoDepoisDoProcessamentoDuravel() {
        worker.consumir();
        var ordem = inOrder(processar, sqs);
        ordem.verify(processar).executar("{}");
        ordem.verify(sqs).deleteMessage(any(DeleteMessageRequest.class));
    }
    @Test void erroMantemMensagemParaRetryOuDlq() {
        doThrow(new IllegalStateException()).when(processar).executar(any());
        worker.consumir();
        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
    }
    @Test void falhaDoSendNaoMarcaOutboxComoEnviada() {
        var evento = new EventoFinanceiroEntity(UUID.randomUUID(), "{\"status\":\"ESTORNADO\"}");
        when(registro.pendentes()).thenReturn(List.of(evento));
        when(sqs.sendMessage(any(SendMessageRequest.class))).thenThrow(new IllegalStateException());
        worker.publicar();
        verify(registro, never()).enviado(any());
    }
    @Test void envioBemSucedidoMarcaMesmoEvento() {
        var evento = new EventoFinanceiroEntity(UUID.randomUUID(), "{}");
        when(registro.pendentes()).thenReturn(List.of(evento));
        worker.publicar();
        verify(sqs).sendMessage(argThat((SendMessageRequest r) -> r.queueUrl().equals("saida") && r.messageBody().equals("{}")));
        verify(registro).enviado(evento.id());
    }
}
