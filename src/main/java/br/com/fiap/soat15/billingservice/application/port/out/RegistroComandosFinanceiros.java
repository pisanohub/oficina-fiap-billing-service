package br.com.fiap.soat15.billingservice.application.port.out;

import br.com.fiap.soat15.billingservice.application.usecase.ComandoPagamento;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

public interface RegistroComandosFinanceiros {
    void reservar(ComandoPagamento comando, String corpo);
    Optional<String> estado(ComandoPagamento comando);
    Optional<ComandoPagamento> checkout(UUID orcamentoId, JsonMapper json);
    void concluir(ComandoPagamento comando, UUID eventoId, String corpo, String estado);
    void incerto(UUID comandoId);
    List<String> aguardando();
}
