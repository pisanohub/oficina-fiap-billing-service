package br.com.fiap.soat15.billingservice.application.usecase;

import br.com.fiap.soat15.billingservice.application.exception.ComandoConflitanteException;
import br.com.fiap.soat15.billingservice.application.port.out.RegistroMensagensOrcamento;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;

/** Reserva a mensagem, usa geração idempotente existente e grava resposta durável. */
public class ProcessarMensagemOrcamentoUseCase {
    private final RegistroMensagensOrcamento registro;
    private final GerarOrcamentoUseCase gerar;
    private final JsonMapper json;
    public ProcessarMensagemOrcamentoUseCase(RegistroMensagensOrcamento registro,
            GerarOrcamentoUseCase gerar, JsonMapper json) {
        this.registro = registro; this.gerar = gerar; this.json = json;
    }
    public void executar(String corpo) {
        var recebido = json.readValue(corpo, SolicitarOrcamentoMensagem.class);
        String identidade = identidade(recebido);
        var entrada = registro.buscar(recebido.comandoId());
        if (entrada.isEmpty()) {
            try { registro.reservar(recebido.comandoId(), json.writeValueAsString(recebido), identidade); }
            catch (DataIntegrityViolationException e) {
                // O concorrente pode ter reservado; releitura fora da transação revertida.
            }
            entrada = registro.buscar(recebido.comandoId());
        }
        var original = entrada.orElseThrow(() -> new IllegalStateException("Reserva indisponível."));
        if (!original.identidade().equals(identidade)) throw new ComandoConflitanteException();
        if (original.resultado() != null) return;
        var c = json.readValue(original.corpo(), SolicitarOrcamentoMensagem.class);
        Map<String, Object> resposta = base(c);
        try {
            var o = gerar.executar(c.dominio());
            resposta.put("id", o.id());
            resposta.put("status", o.status());
            resposta.put("moeda", "BRL");
            resposta.put("total", o.total());
            resposta.put("criadoEm", o.criadoEm().toString());
            resposta.put("itens", o.itens());
        } catch (ComandoConflitanteException e) {
            resposta.put("status", "FALHA");
            resposta.put("erro", Map.of("codigo", "COMANDO_CONFLITANTE",
                    "mensagem", "Comando já utilizado com dados diferentes."));
        } catch (IllegalArgumentException e) {
            resposta.put("status", "FALHA");
            resposta.put("erro", Map.of("codigo", "DADOS_ORCAMENTO_INVALIDOS",
                    "mensagem", "Confira referências, tipos, quantidades e preços dos itens."));
        }
        registro.concluir(c.comandoId(), json.writeValueAsString(resposta));
    }
    private Map<String, Object> base(SolicitarOrcamentoMensagem c) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("messageId", UUID.nameUUIDFromBytes(("billing:orcamento:" + c.comandoId()).getBytes(StandardCharsets.UTF_8)));
        r.put("sagaId", c.sagaId()); r.put("causationId", c.messageId()); r.put("comandoId", c.comandoId());
        r.put("ordemId", c.ordemId()); r.put("clienteId", c.clienteId());
        if (c.versao() != null) r.put("versao", c.versao());
        return r;
    }
    private String identidade(SolicitarOrcamentoMensagem c) {
        // JSON normalizado pelo DTO; ordem dos itens faz parte do snapshot.
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sagaId", c.sagaId()); m.put("ordemId", c.ordemId()); m.put("clienteId", c.clienteId());
        m.put("versao", c.versao());
        try { m.put("itens", c.dominio().itens()); }
        catch (IllegalArgumentException e) { m.put("itens", c.itens()); }
        return json.writeValueAsString(m);
    }
}
