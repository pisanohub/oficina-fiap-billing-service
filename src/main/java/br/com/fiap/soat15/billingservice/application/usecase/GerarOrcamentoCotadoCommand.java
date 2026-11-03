package br.com.fiap.soat15.billingservice.application.usecase;
import br.com.fiap.soat15.billingservice.domain.model.ItemCotacao;
import java.util.*;

/** Entrada interna provisória; não define o envelope SQS compartilhado com o OS. */
public record GerarOrcamentoCotadoCommand(UUID comandoId, long ordemId, long clienteId,
        int versaoDiagnostico, List<ItemCotacao> itens) {
    public GerarOrcamentoCotadoCommand {
        if (comandoId == null || ordemId <= 0 || clienteId <= 0 || versaoDiagnostico <= 0) {
            throw new IllegalArgumentException("Comando, OS, cliente e versão do diagnóstico devem ser válidos.");
        }
        itens = ItemCotacao.validarLista(itens);
    }
}
