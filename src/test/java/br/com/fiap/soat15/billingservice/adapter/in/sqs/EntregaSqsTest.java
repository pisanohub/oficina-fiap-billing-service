package br.com.fiap.soat15.billingservice.adapter.in.sqs;

import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

class EntregaSqsTest {
    private final SqsClient sqs = mock(SqsClient.class);
    @SuppressWarnings("unchecked")
    private final Consumer<String> processar = mock(Consumer.class);

    @Test void conservaVisibilidadeEspecificaDeCadaFluxo() {
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(ReceiveMessageResponse.builder().build());
        new EntregaSqs(sqs, "orcamentos", 60).consumir(processar);
        new EntregaSqs(sqs, "pagamentos", 120).consumir(processar);
        verify(sqs).receiveMessage(argThat((ReceiveMessageRequest r) ->
                r.queueUrl().equals("orcamentos") && r.visibilityTimeout() == 60
                        && r.maxNumberOfMessages() == 1 && r.waitTimeSeconds() == 5));
        verify(sqs).receiveMessage(argThat((ReceiveMessageRequest r) ->
                r.queueUrl().equals("pagamentos") && r.visibilityTimeout() == 120));
        verifyNoInteractions(processar);
    }
    @Test void receberComErroNaoExecutaNemConfirma() {
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenThrow(new IllegalStateException());
        assertThatCode(() -> new EntregaSqs(sqs, "entrada", 60).consumir(processar)).doesNotThrowAnyException();
        verifyNoInteractions(processar);
        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
    }
    @Test void falhaNoAckNaoRepeteProcessamentoDentroDaMesmaEntrega() {
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(ReceiveMessageResponse.builder()
                .messages(Message.builder().body("corpo").receiptHandle("recibo").build()).build());
        when(sqs.deleteMessage(any(DeleteMessageRequest.class))).thenThrow(new IllegalStateException());
        assertThatCode(() -> new EntregaSqs(sqs, "entrada", 60).consumir(processar)).doesNotThrowAnyException();
        var ordem = inOrder(processar, sqs);
        ordem.verify(processar).accept("corpo");
        ordem.verify(sqs).deleteMessage(argThat((DeleteMessageRequest r) ->
                r.queueUrl().equals("entrada") && r.receiptHandle().equals("recibo")));
        verify(processar, times(1)).accept("corpo");
    }
}
