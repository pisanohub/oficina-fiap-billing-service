package br.com.fiap.soat15.billingservice.adapter.out.persistence;

import br.com.fiap.soat15.billingservice.application.usecase.ComandoPagamento;
import br.com.fiap.soat15.billingservice.application.port.out.RegistroComandosFinanceiros;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Transações curtas: nunca mantém transação de banco durante HTTP ou SQS. */
@Component
public class RegistroFinanceiro implements RegistroComandosFinanceiros {
    private final ComandosFinanceirosJpa comandos;
    private final EventosFinanceirosJpa eventos;
    public RegistroFinanceiro(ComandosFinanceirosJpa comandos, EventosFinanceirosJpa eventos) {
        this.comandos = comandos; this.eventos = eventos;
    }
    @Transactional
    public void reservar(ComandoPagamento c, String corpo) {
        comandos.saveAndFlush(new ComandoFinanceiroEntity(c.comandoId(), c.chaveOperacao(), c.identidade(), corpo));
    }
    @Transactional(readOnly = true)
    public Optional<String> estado(ComandoPagamento c) {
        return comandos.findById(c.comandoId()).map(e -> {
            if (!e.identidade.equals(c.identidade())) throw new IllegalArgumentException("Comando conflitante.");
            return e.estado;
        });
    }
    @Transactional(readOnly = true)
    public Optional<ComandoPagamento> checkout(UUID orcamentoId, tools.jackson.databind.json.JsonMapper json) {
        return comandos.findByChaveOperacao("checkout:" + orcamentoId)
                .map(e -> json.readValue(e.corpo, ComandoPagamento.class));
    }
    @Transactional
    public void concluir(ComandoPagamento c, UUID eventoId, String corpo, String estado) {
        var e = comandos.findById(c.comandoId()).orElseThrow();
        eventos.saveAndFlush(new EventoFinanceiroEntity(eventoId, corpo));
        e.estado = estado;
    }
    @Transactional
    public void incerto(UUID id) { comandos.findById(id).orElseThrow().estado = "INCERTO"; }
    @Transactional(readOnly = true)
    public List<String> aguardando() {
        return comandos.findTop20ByEstadoOrderByComandoIdAsc("CHECKOUT_CRIADO").stream().map(e -> e.corpo).toList();
    }
    @Transactional(readOnly = true)
    public List<EventoFinanceiroEntity> pendentes() { return eventos.findTop20ByEnviadoFalseOrderByMessageIdAsc(); }
    @Transactional
    public void enviado(UUID id) { eventos.findById(id).orElseThrow().enviado = true; }
}
