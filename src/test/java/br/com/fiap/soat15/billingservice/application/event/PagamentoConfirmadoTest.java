package br.com.fiap.soat15.billingservice.application.event;

import br.com.fiap.soat15.billingservice.domain.model.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;

class PagamentoConfirmadoTest {
    private final UUID message = UUID.fromString("9d913b83-e2d4-4a81-9f8c-3cab8da26a43");
    private final UUID causa = UUID.fromString("71269b90-225d-4d42-8447-323f9c0328e1");
    private final UUID comando = UUID.fromString("c01d4a4a-66c1-4818-a7bb-c0d8fa42fd9e");
    private final UUID orcamentoId = UUID.fromString("544eb090-43ad-4b45-b9d6-d3e4ca8da341");

    @Test
    void correspondeAoJsonCompartilhado() throws Exception {
        var mapper = JsonMapper.builder().build();
        var evento = evento("approved", "199.80");
        var json = mapper.readTree(mapper.writeValueAsString(evento));
        assertThat(json).isEqualTo(mapper.readTree(Files.readString(
                Path.of("docs/pagamento-confirmado.exemplo.json"))));
        assertThat(evento("approved", "199.80")).isEqualTo(evento);
    }

    @Test
    void rejeitaPagamentoPendenteOuValorDivergente() {
        assertThatIllegalArgumentException().isThrownBy(() -> evento("pending", "199.80"));
        assertThatIllegalArgumentException().isThrownBy(() -> evento("approved", "200.00"));
    }

    private PagamentoConfirmado evento(String status, String valor) {
        return evento(status, valor, 2);
    }

    @Test
    void rejeitaVersaoAusenteOuInvalida() {
        assertThatIllegalArgumentException().isThrownBy(() -> evento("approved", "199.80", 0));
        assertThatIllegalArgumentException().isThrownBy(() -> evento("approved", "199.80", -1));
    }

    private PagamentoConfirmado evento(String status, String valor, int versao) {
        var orcamento = new Orcamento(orcamentoId, UUID.randomUUID(), 123, 1,
                List.of(new ItemOrcamento("servico", TipoItem.SERVICO, "Serviço", 1,
                        new BigDecimal("199.80"))), Instant.EPOCH);
        var dados = new PagamentoConsultado("186426882414", orcamentoId.toString(), status,
                null, new BigDecimal(valor), "BRL", BigDecimal.ZERO, Instant.EPOCH);
        return PagamentoConfirmado.de(message, "saga-os-123", causa, comando,
                orcamento, versao, new Pagamento(orcamentoId, dados, Instant.EPOCH));
    }
}
