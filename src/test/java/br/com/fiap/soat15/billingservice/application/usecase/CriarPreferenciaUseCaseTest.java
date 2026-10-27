package br.com.fiap.soat15.billingservice.application.usecase;

import br.com.fiap.soat15.billingservice.application.port.out.*;
import br.com.fiap.soat15.billingservice.application.exception.PreferenciaBloqueadaException;
import br.com.fiap.soat15.billingservice.domain.model.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class CriarPreferenciaUseCaseTest {
    @Test
    void reservaPerdidaReleResultadoConcorrenteOuBloqueiaSemChamadaExterna() {
        var consultar = mock(ConsultarOrcamentoUseCase.class);
        var repository = mock(PreferenciaCheckoutRepository.class);
        var gateway = mock(PreferenciaPagamentoGateway.class);
        var clock = Clock.fixed(Instant.parse("2026-10-27T12:00:00Z"), ZoneOffset.UTC);
        var id = UUID.randomUUID();
        when(consultar.executar(id)).thenReturn(new Orcamento(id, UUID.randomUUID(), 1, 1,
                List.of(new ItemOrcamento("s1", TipoItem.SERVICO, "Serviço", 1, BigDecimal.TEN)), clock.instant()));
        var criado = new PreferenciaCheckout(id, EstadoPreferencia.CRIADA, "p1", "https://www.mercadopago.com.br/checkout", null, clock.instant());
        var usecase = new CriarPreferenciaUseCase(consultar, repository, gateway, clock);
        when(repository.buscar(id)).thenReturn(Optional.empty(), Optional.of(criado));
        assertThat(usecase.executar(id)).isEqualTo(criado);
        when(repository.buscar(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> usecase.executar(id)).isInstanceOf(PreferenciaBloqueadaException.class);
        verify(gateway, never()).criar(any());
    }

    @Test
    void falhaAoSalvarRespostaNaoRepeteAChamadaDoProvedor() {
        var consultar = mock(ConsultarOrcamentoUseCase.class);
        var repository = mock(PreferenciaCheckoutRepository.class);
        var gateway = mock(PreferenciaPagamentoGateway.class);
        var id = UUID.randomUUID();
        var orcamento = new Orcamento(id, UUID.randomUUID(), 1, 1,
                List.of(new ItemOrcamento("s1", TipoItem.SERVICO, "Serviço", 1, BigDecimal.TEN)), Instant.now());
        when(consultar.executar(id)).thenReturn(orcamento);
        when(repository.tentarIniciar(eq(id), any())).thenReturn(true);
        when(gateway.criar(orcamento)).thenReturn(new PreferenciaGerada("p1", "https://www.mercadopago.com.br/checkout", null));
        doThrow(new IllegalStateException("Banco indisponível")).when(repository).concluir(eq(id), any(), any());
        var usecase = new CriarPreferenciaUseCase(consultar, repository, gateway, Clock.systemUTC());
        assertThatThrownBy(() -> usecase.executar(id)).isInstanceOf(IllegalStateException.class);
        verify(gateway, times(1)).criar(orcamento);
        verify(repository, never()).registrarFalha(any(), anyBoolean(), any());
    }
}
