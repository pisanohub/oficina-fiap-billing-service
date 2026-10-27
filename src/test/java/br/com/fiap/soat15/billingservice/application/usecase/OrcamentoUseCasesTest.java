package br.com.fiap.soat15.billingservice.application.usecase;

import br.com.fiap.soat15.billingservice.application.exception.*;
import br.com.fiap.soat15.billingservice.application.port.out.OrcamentoRepository;
import br.com.fiap.soat15.billingservice.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrcamentoUseCasesTest {
    private final OrcamentoRepository repository = mock(OrcamentoRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-27T12:00:00Z"), ZoneOffset.UTC);
    private final GerarOrcamentoUseCase gerar = new GerarOrcamentoUseCase(repository, clock);
    private final GerarOrcamentoCommand comando = new GerarOrcamentoCommand(UUID.randomUUID(), 1, 2,
            List.of(new ItemOrcamento("s1", TipoItem.SERVICO, "Diagnóstico", 1, new BigDecimal("100.00"))));

    @BeforeEach
    void preparar() {
        when(repository.buscarPorComandoId(comando.comandoId())).thenReturn(Optional.empty());
        when(repository.salvar(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void geraComDataControladaEPersiste() {
        var resultado = gerar.executar(comando);
        assertThat(resultado.criadoEm()).isEqualTo(clock.instant());
        assertThat(resultado.total()).isEqualByComparingTo("100.00");
        verify(repository).salvar(resultado);
    }

    @Test
    void comandoRepetidoRetornaOriginalSemNovaEscrita() {
        var existente = existente(1);
        when(repository.buscarPorComandoId(comando.comandoId())).thenReturn(Optional.of(existente));
        assertThat(gerar.executar(comando)).isSameAs(existente);
        verify(repository, never()).salvar(any());
    }

    @Test
    void mesmaChaveComOutroConteudoGeraConflito() {
        when(repository.buscarPorComandoId(comando.comandoId())).thenReturn(Optional.of(existente(99)));
        assertThatThrownBy(() -> gerar.executar(comando)).isInstanceOf(ComandoConflitanteException.class);
    }

    @Test
    void conflitoConcorrenteReleOriginalAposRollback() {
        var existente = existente(1);
        when(repository.buscarPorComandoId(comando.comandoId())).thenReturn(Optional.empty(), Optional.of(existente));
        when(repository.salvar(any())).thenThrow(new ConflitoPersistenciaException(new RuntimeException("unique")));
        assertThat(gerar.executar(comando)).isSameAs(existente);
    }

    @Test
    void conflitoConcorrenteComOutroConteudoNaoEMascarado() {
        when(repository.buscarPorComandoId(comando.comandoId())).thenReturn(Optional.empty(), Optional.of(existente(99)));
        when(repository.salvar(any())).thenThrow(new ConflitoPersistenciaException(new RuntimeException("unique")));
        assertThatThrownBy(() -> gerar.executar(comando)).isInstanceOf(ComandoConflitanteException.class);
    }

    @Test
    void outraViolacaoDeIntegridadeNaoRetornaFalsoSucesso() {
        var erro = new ConflitoPersistenciaException(new RuntimeException("outro erro"));
        when(repository.salvar(any())).thenThrow(erro);
        assertThatThrownBy(() -> gerar.executar(comando)).isSameAs(erro);
    }

    @Test
    void validaComandoAntesDeConsultarBanco() {
        assertThatIllegalArgumentException().isThrownBy(() -> gerar.executar(null));
        assertThatIllegalArgumentException().isThrownBy(() -> gerar.executar(new GerarOrcamentoCommand(null, 1, 2, comando.itens())));
        verify(repository, never()).salvar(any());
    }

    @Test
    void consultaRetornaOuDistingueAusenciaEEntradaInvalida() {
        var consultar = new ConsultarOrcamentoUseCase(repository);
        var existente = existente(1);
        when(repository.buscarPorId(existente.id())).thenReturn(Optional.of(existente));
        assertThat(consultar.executar(existente.id())).isSameAs(existente);
        assertThatThrownBy(() -> consultar.executar(UUID.randomUUID())).isInstanceOf(OrcamentoNaoEncontradoException.class);
        assertThatIllegalArgumentException().isThrownBy(() -> consultar.executar(null));
    }

    private Orcamento existente(long ordem) {
        return new Orcamento(UUID.randomUUID(), comando.comandoId(), ordem, 2, comando.itens(), clock.instant());
    }
}
