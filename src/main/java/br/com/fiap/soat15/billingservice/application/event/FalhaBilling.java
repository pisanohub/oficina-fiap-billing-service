package br.com.fiap.soat15.billingservice.application.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;
import java.util.UUID;

/** Resposta terminal para orcamento-respondido ou pagamento-confirmado. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FalhaBilling(UUID messageId, String sagaId, UUID causationId, UUID comandoId,
        long ordemId, long clienteId, UUID orcamentoId, String pagamentoId, String status, Erro erro) {

    public FalhaBilling {
        Objects.requireNonNull(messageId, "messageId do evento");
        Objects.requireNonNull(causationId, "messageId do comando");
        Objects.requireNonNull(comandoId, "comandoId");
        Objects.requireNonNull(erro, "erro");
        if (!"FALHA".equals(status) || messageId.equals(causationId)
                || sagaId == null || sagaId.isBlank() || ordemId <= 0 || clienteId <= 0
                || pagamentoId != null && orcamentoId == null) {
            throw new IllegalArgumentException("Envelope de falha inválido.");
        }
    }

    /** Só use para erros definitivos. Em republicações, preserve o evento original. */
    public static FalhaBilling definitiva(UUID messageId, String sagaId, UUID causationId,
            UUID comandoId, long ordemId, long clienteId, UUID orcamentoId, String pagamentoId,
            Codigo codigo) {
        return new FalhaBilling(messageId, sagaId, causationId, comandoId, ordemId, clienteId,
                orcamentoId, pagamentoId, "FALHA", new Erro(codigo));
    }

    public enum Codigo {
        DADOS_ORCAMENTO_INVALIDOS("Dados do orçamento inválidos"),
        COMANDO_CONFLITANTE("Comando já utilizado com dados diferentes"),
        ORCAMENTO_NAO_ENCONTRADO("Orçamento não encontrado"),
        PAGAMENTO_INCOMPATIVEL("Pagamento não corresponde ao orçamento");

        private final String mensagem;

        Codigo(String mensagem) {
            this.mensagem = mensagem;
        }
    }

    public record Erro(Codigo codigo, String mensagem) {
        public Erro(Codigo codigo) {
            this(codigo, Objects.requireNonNull(codigo, "codigo").mensagem);
        }

        public Erro {
            // Use somente textos do contrato, nunca a exceção ou a resposta bruta do provedor.
            mensagem = Objects.requireNonNull(codigo, "codigo").mensagem;
        }
    }
}
