package br.com.fiap.soat15.billingservice.adapter.in.sqs;

import br.com.fiap.soat15.billingservice.application.port.out.RegistroMensagensOrcamento;
import br.com.fiap.soat15.billingservice.application.usecase.ProcessarMensagemOrcamentoUseCase;
import java.util.*;
import org.junit.jupiter.api.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import static org.mockito.Mockito.*;

class OrcamentoSqsWorkerTest {
    final SqsClient sqs = mock(SqsClient.class);
    final RegistroMensagensOrcamento registro = mock(RegistroMensagensOrcamento.class);
    final ProcessarMensagemOrcamentoUseCase processar = mock(ProcessarMensagemOrcamentoUseCase.class);
    final OrcamentoSqsWorker worker = new OrcamentoSqsWorker(sqs, "entrada", "saida", processar, registro);
    @BeforeEach void preparar() {
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(ReceiveMessageResponse.builder()
                .messages(Message.builder().body("{}").receiptHandle("recibo").build()).build());
    }
    @Test void apagaApenasDepoisDeSalvarResposta() {
        worker.consumir();
        var ordem = inOrder(processar, sqs);
        ordem.verify(processar).executar("{}");
        ordem.verify(sqs).deleteMessage(any(DeleteMessageRequest.class));
    }
    @Test void erroNaoApagaMensagem() {
        doThrow(new IllegalStateException()).when(processar).executar(any());
        worker.consumir();
        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
    }
    @Test void falhaDeEnvioMantemRespostaDuravel() {
        when(registro.pendentes()).thenReturn(List.of(new RegistroMensagensOrcamento.Pendente(UUID.randomUUID(), "{}")));
        when(sqs.sendMessage(any(SendMessageRequest.class))).thenThrow(new IllegalStateException());
        worker.publicar();
        verify(registro, never()).enviado(any());
    }
    @Test void enviaRespostaPersistidaAntesDeMarcarComoEnviada() {
        var cmd = UUID.randomUUID();
        when(registro.pendentes()).thenReturn(List.of(new RegistroMensagensOrcamento.Pendente(cmd, "{\"status\":\"FALHA\"}")));
        worker.publicar();
        var ordem = inOrder(sqs, registro);
        ordem.verify(sqs).sendMessage(argThat((SendMessageRequest r) -> r.messageBody().contains("FALHA") && r.queueUrl().equals("saida")));
        ordem.verify(registro).enviado(cmd);
    }
}
