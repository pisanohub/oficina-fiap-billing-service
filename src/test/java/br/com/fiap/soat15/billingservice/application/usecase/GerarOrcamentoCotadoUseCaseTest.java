package br.com.fiap.soat15.billingservice.application.usecase;

import br.com.fiap.soat15.billingservice.application.exception.*;
import br.com.fiap.soat15.billingservice.application.port.out.*;
import br.com.fiap.soat15.billingservice.domain.model.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GerarOrcamentoCotadoUseCaseTest {
    private final OrcamentoCotadoRepository repository = mock(OrcamentoCotadoRepository.class);
    private final CotacaoGateway gateway = mock(CotacaoGateway.class);
    private final Instant data = Instant.parse("2026-11-03T12:00:00Z");
    private final GerarOrcamentoCotadoUseCase useCase = new GerarOrcamentoCotadoUseCase(repository, gateway, Clock.fixed(data, ZoneOffset.UTC));
    private final GerarOrcamentoCotadoCommand comando = new GerarOrcamentoCotadoCommand(UUID.randomUUID(), 1, 1, 2,
            List.of(new ItemCotacao(TipoItem.PECA, "filtro", 1)));
    private Cotacao cotacao(String referencia) {
        return new Cotacao("BRL", data, List.of(new ItemOrcamento(referencia, TipoItem.PECA, "Filtro", 1, BigDecimal.TEN)));
    }
    @Test void comandoNuloNaoConsultaNada() {
        assertThatIllegalArgumentException().isThrownBy(() -> useCase.executar(null));
        verifyNoInteractions(repository, gateway);
    }
    @Test void gatewayNuloOuComItensDiferentesNaoPersiste() {
        when(gateway.consultar(any())).thenReturn(null, cotacao("outro"));
        for (int i = 0; i < 2; i++) assertThatThrownBy(() -> useCase.executar(comando)).isInstanceOf(IntegracaoCotacaoException.class);
        verify(repository, never()).salvar(any());
    }
    @Test void conflitoSemGravacaoConcorrenteNaoEEscondido() {
        when(gateway.consultar(any())).thenReturn(cotacao("filtro"));
        when(repository.salvar(any())).thenThrow(new ConflitoPersistenciaException(new RuntimeException("falha simulada")));
        assertThatThrownBy(() -> useCase.executar(comando)).isInstanceOf(ConflitoPersistenciaException.class);
    }
    @Test void repeticaoConcorrenteComOutroConteudoDaConflito() {
        var outro = new OrcamentoCotado(new Orcamento(UUID.randomUUID(), comando.comandoId(), 2, 1,
                cotacao("filtro").itens(), data), 2, data);
        when(repository.buscarPorComandoId(comando.comandoId())).thenReturn(Optional.empty(), Optional.of(outro));
        when(gateway.consultar(any())).thenReturn(cotacao("filtro"));
        when(repository.salvar(any())).thenThrow(new ConflitoPersistenciaException(new RuntimeException("concorrência")));
        assertThatThrownBy(() -> useCase.executar(comando)).isInstanceOf(ComandoConflitanteException.class);
    }
}
