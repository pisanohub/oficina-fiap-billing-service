package br.com.fiap.soat15.billingservice.adapter.out.persistence;
import br.com.fiap.soat15.billingservice.application.port.out.RegistroMensagensOrcamento;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RegistroMensagensOrcamentoAdapter implements RegistroMensagensOrcamento {
    private final MensagensOrcamentoJpa repository;
    public RegistroMensagensOrcamentoAdapter(MensagensOrcamentoJpa repository) { this.repository = repository; }
    @Transactional(readOnly = true)
    public Optional<Entrada> buscar(UUID id) {
        return repository.findById(id).map(e -> new Entrada(e.corpo, e.identidade, e.resultado));
    }
    @Transactional
    public void reservar(UUID id, String corpo, String identidade) {
        repository.saveAndFlush(new MensagemOrcamentoEntity(id, corpo, identidade));
    }
    @Transactional
    public void concluir(UUID id, String resultado) {
        Objects.requireNonNull(resultado, "resultado");
        var e = repository.findById(id).orElseThrow();
        // Conserva a resposta original mesmo em concorrência/republicação.
        if (e.resultado == null) e.resultado = resultado;
    }
    @Transactional(readOnly = true)
    public List<Pendente> pendentes() {
        return repository.findTop20ByEnviadoFalseAndResultadoIsNotNullOrderByComandoIdAsc().stream()
                .map(e -> new Pendente(e.comandoId, e.resultado)).toList();
    }
    @Transactional
    public void enviado(UUID id) { repository.findById(id).orElseThrow().enviado = true; }
}
