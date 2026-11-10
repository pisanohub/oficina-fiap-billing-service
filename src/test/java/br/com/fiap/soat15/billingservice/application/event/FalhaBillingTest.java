package br.com.fiap.soat15.billingservice.application.event;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;

class FalhaBillingTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final UUID evento = UUID.fromString("9d913b83-e2d4-4a81-9f8c-3cab8da26a43");
    private final UUID causa = UUID.fromString("71269b90-225d-4d42-8447-323f9c0328e1");
    private final UUID comando = UUID.fromString("c01d4a4a-66c1-4818-a7bb-c0d8fa42fd9e");

    @Test
    void falhaDePagamentoCorrespondeAoExemplo() throws Exception {
        var orcamento = UUID.fromString("544eb090-43ad-4b45-b9d6-d3e4ca8da341");
        var falha = FalhaBilling.definitiva(evento, "saga-os-123", causa, comando, 123, 1,
                orcamento, null, FalhaBilling.Codigo.ORCAMENTO_NAO_ENCONTRADO);
        assertThat(mapper.readTree(mapper.writeValueAsString(falha))).isEqualTo(
                mapper.readTree(Files.readString(Path.of("docs/billing-falha.exemplo.json"))));
    }

    @Test
    void falhaDeOrcamentoMantemCorrelacaoSemInventarIdentificadores() {
        var falha = FalhaBilling.definitiva(evento, "saga-os-123", causa, comando, 123, 1,
                null, null, FalhaBilling.Codigo.DADOS_ORCAMENTO_INVALIDOS);
        var json = mapper.readTree(mapper.writeValueAsString(falha));
        assertThat(json.path("status").asString()).isEqualTo("FALHA");
        assertThat(json.path("causationId").asString()).isEqualTo(causa.toString());
        assertThat(json.has("orcamentoId")).isFalse();
        assertThat(json.has("pagamentoId")).isFalse();
        assertThat(json.has("payload")).isFalse();
    }

    @Test
    void rejeitaIdentificadorDeEventoIgualAoDoComando() {
        assertThatIllegalArgumentException().isThrownBy(() -> FalhaBilling.definitiva(
                causa, "saga-os-123", causa, comando, 123, 1, null, null,
                FalhaBilling.Codigo.COMANDO_CONFLITANTE));
    }
}
