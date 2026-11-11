package br.com.fiap.soat15.billingservice.application.usecase;

import br.com.fiap.soat15.billingservice.application.event.PagamentoConfirmado;
import br.com.fiap.soat15.billingservice.application.exception.OrcamentoNaoEncontradoException;
import br.com.fiap.soat15.billingservice.application.port.out.*;
import br.com.fiap.soat15.billingservice.domain.model.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;

/** Despacha os dois tipos da mesma fila. Somente o estorno chama a API de refunds. */
public class ProcessarComandoPagamentoUseCase {
    private final RegistroComandosFinanceiros registro;
    private final ConsultarOrcamentoUseCase consultar;
    private final CriarPreferenciaUseCase checkout;
    private final PagamentoGateway pagamentos;
    private final PagamentoRepository pagamentosSalvos;
    private final EstornoGateway estornos;
    private final JsonMapper json;
    private final Clock clock;

    public ProcessarComandoPagamentoUseCase(RegistroComandosFinanceiros registro, ConsultarOrcamentoUseCase consultar,
            CriarPreferenciaUseCase checkout, PagamentoGateway pagamentos, PagamentoRepository pagamentosSalvos,
            EstornoGateway estornos, JsonMapper json, Clock clock) {
        this.registro = registro; this.consultar = consultar; this.checkout = checkout;
        this.pagamentos = pagamentos; this.pagamentosSalvos = pagamentosSalvos;
        this.estornos = estornos; this.json = json; this.clock = clock;
    }

    public void executar(String corpo) {
        ComandoPagamento c = json.readValue(corpo, ComandoPagamento.class);
        var estado = registro.estado(c);
        if (estado.isPresent()) {
            if (Set.of("CONCLUIDO", "FALHA", "CHECKOUT_CRIADO").contains(estado.get())) return;
            if (c.type() == ComandoPagamento.Tipo.EstornarPagamento && estado.get().equals("INCERTO")) {
                // Reconciliação apenas por GET: não repete o POST após resultado incerto.
                reconciliar(c);
                return;
            }
            throw new IllegalStateException("Comando em processamento ou requer reconciliação.");
        }
        try {
            registro.reservar(c, json.writeValueAsString(c));
        } catch (DataIntegrityViolationException e) {
            // Outra instância/comando reservou a operação; nunca chama o provedor aqui.
            throw new IllegalStateException("Operação financeira já reservada.");
        }
        try {
            Orcamento o = consultar.executar(c.orcamentoId());
            if (o.ordemId() != c.ordemId() || o.clienteId() != c.clienteId()) {
                falha(c, "DADOS_INCOMPATIVEIS", "Orçamento não corresponde à OS e ao cliente.");
                return;
            }
            if (c.type() == ComandoPagamento.Tipo.ProcessarPagamento) {
                var p = checkout.executar(o.id());
                Map<String, Object> resposta = base(c, "CheckoutCriado", "CHECKOUT_CRIADO");
                resposta.put("checkoutUrl", p.checkoutUrl());
                if (p.checkoutTesteUrl() != null) resposta.put("checkoutTesteUrl", p.checkoutTesteUrl());
                gravar(c, resposta, "CHECKOUT_CRIADO");
            } else {
                estornar(c, o);
            }
        } catch (OrcamentoNaoEncontradoException e) {
            falha(c, "ORCAMENTO_NAO_ENCONTRADO", "Orçamento não encontrado.");
        } catch (RuntimeException e) {
            registro.incerto(c.comandoId());
            throw new IllegalStateException("Resultado financeiro incerto; requer reconciliação.", e);
        }
    }

    private void estornar(ComandoPagamento c, Orcamento o) {
        var origem = registro.checkout(o.id(), json);
        if (origem.isEmpty() || origem.get().versao() != c.versao()
                || !origem.get().sagaId().equals(c.sagaId())) {
            falha(c, "VERSAO_INCOMPATIVEL", "Não há checkout correspondente à Saga e versão.");
            return;
        }
        // Exige operação já verificada localmente; não aceita um paymentId arbitrário.
        var conhecido = pagamentosSalvos.listarPorOrcamento(o.id()).stream()
                .filter(p -> p.dados().id().equals(c.pagamentoId())).findFirst();
        if (conhecido.isEmpty()) {
            falha(c, "PAGAMENTO_NAO_REGISTRADO", "Pagamento ainda não foi registrado pelo Billing.");
            return;
        }
        var atual = pagamentos.consultar(c.pagamentoId());
        if (!atual.id().equals(c.pagamentoId())) throw new IllegalArgumentException("Operação financeira divergente.");
        validarVinculo(o, atual);
        if (atual.estado() == EstadoPagamento.ESTORNADO) {
            concluirEstorno(c, atual);
            return;
        }
        if (atual.estado() == EstadoPagamento.PARCIALMENTE_ESTORNADO) {
            falha(c, "ESTORNO_PARCIAL_NAO_SUPORTADO", "Pagamento possui estorno parcial; requer análise.");
            return;
        }
        if (atual.estado() != EstadoPagamento.APROVADO) {
            throw new IllegalStateException("Pagamento não está disponível para estorno integral.");
        }
        estornos.estornarIntegral(c.pagamentoId(), c.comandoId());
        reconciliar(c); // Só anuncia sucesso após GET confirmar o estado financeiro.
    }

    private void reconciliar(ComandoPagamento c) {
        try {
            var atual = pagamentos.consultar(c.pagamentoId());
            if (!atual.id().equals(c.pagamentoId())) throw new IllegalArgumentException("Operação financeira divergente.");
            validarVinculo(consultar.executar(c.orcamentoId()), atual);
            if (atual.estado() != EstadoPagamento.ESTORNADO) {
                throw new IllegalStateException("Estorno ainda não confirmado.");
            }
            concluirEstorno(c, atual);
        } catch (RuntimeException e) {
            registro.incerto(c.comandoId());
            throw new IllegalStateException("Estorno incerto; não reenviar outro comando.", e);
        }
    }

    private void concluirEstorno(ComandoPagamento c, PagamentoConsultado atual) {
        pagamentosSalvos.registrar(new Pagamento(c.orcamentoId(), atual, clock.instant()));
        var resposta = base(c, "PagamentoEstornado", "ESTORNADO");
        resposta.put("pagamentoId", atual.id());
        resposta.put("valor", atual.valorEstornado());
        resposta.put("moeda", atual.moeda());
        gravar(c, resposta, "CONCLUIDO");
    }

    private void validarVinculo(Orcamento o, PagamentoConsultado p) {
        if (!o.id().toString().equals(p.referenciaExterna()) || !"BRL".equals(p.moeda())
                || o.total().compareTo(p.valor()) != 0) {
            throw new IllegalArgumentException("Resposta financeira incompatível.");
        }
    }

    /** Usa registros verificados pelo fluxo de consulta/webhook existente. */
    public void confirmarRegistrados() {
        for (String corpo : registro.aguardando()) {
            var c = json.readValue(corpo, ComandoPagamento.class);
            var o = consultar.executar(c.orcamentoId());
            var aprovados = pagamentosSalvos.listarPorOrcamento(o.id()).stream()
                    .filter(Pagamento::confirmado).toList();
            // Não escolhe silenciosamente entre múltiplos pagamentos.
            if (aprovados.size() != 1) continue;
            var e = PagamentoConfirmado.de(eventoId(c, "PagamentoConfirmado"), c.sagaId(),
                    c.messageId(), c.comandoId(), o, c.versao(), aprovados.getFirst());
            var resposta = base(c, "PagamentoConfirmado", "APROVADO");
            resposta.putAll(json.readValue(json.writeValueAsString(e), Map.class));
            gravar(c, resposta, "CONCLUIDO");
        }
    }

    private Map<String, Object> base(ComandoPagamento c, String tipo, String status) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("type", tipo); r.put("messageId", eventoId(c, tipo)); r.put("sagaId", c.sagaId());
        r.put("causationId", c.messageId()); r.put("comandoId", c.comandoId());
        r.put("ordemId", c.ordemId()); r.put("clienteId", c.clienteId());
        r.put("orcamentoId", c.orcamentoId()); r.put("versao", c.versao()); r.put("status", status);
        return r;
    }
    private void falha(ComandoPagamento c, String codigo, String mensagem) {
        var r = base(c, "FalhaPagamento", "FALHA");
        r.put("erro", Map.of("codigo", codigo, "mensagem", mensagem));
        gravar(c, r, "FALHA");
    }
    private void gravar(ComandoPagamento c, Map<String, Object> r, String estado) {
        registro.concluir(c, UUID.fromString(r.get("messageId").toString()), json.writeValueAsString(r), estado);
    }
    private UUID eventoId(ComandoPagamento c, String tipo) {
        return UUID.nameUUIDFromBytes(("billing:" + tipo + ":" + c.comandoId()).getBytes(StandardCharsets.UTF_8));
    }
}
