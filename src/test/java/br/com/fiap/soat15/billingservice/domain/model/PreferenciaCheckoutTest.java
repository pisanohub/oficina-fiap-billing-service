package br.com.fiap.soat15.billingservice.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class PreferenciaCheckoutTest {
    private final String url = "https://www.mercadopago.com.br/checkout/p1";

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "http://www.mercadopago.com.br/checkout", "https://outro.example/checkout", "https://usuario@www.mercadopago.com.br/checkout", "https://mercadopago.com.br.outro.example/checkout"})
    void rejeitaUrlInvalida(String invalida) {
        assertThatIllegalArgumentException().isThrownBy(() -> new PreferenciaGerada("p1", invalida, null));
    }

    @Test
    void validaIdentificadorLinkOpcionalETamanho() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PreferenciaGerada(null, url, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new PreferenciaGerada(" ", url, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new PreferenciaGerada("a".repeat(121), url, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new PreferenciaGerada("p1", url + "a".repeat(2001), null));
        assertThatIllegalArgumentException().isThrownBy(() -> new PreferenciaGerada("p1", url, "https://outro.example"));
        assertThat(new PreferenciaGerada("p1", "https://mercadopago.com/checkout", "https://mercadopago.com.br/checkout").id()).isEqualTo("p1");
    }

    @Test
    void estadoCriadoExigePreferenciaValidaEEstadosPendentesNaoInventamLink() {
        var id = UUID.randomUUID();
        var agora = Instant.now();
        assertThat(new PreferenciaCheckout(id, EstadoPreferencia.CRIADA, "p1", url, null, agora).preferenciaId()).isEqualTo("p1");
        assertThatIllegalArgumentException().isThrownBy(() -> new PreferenciaCheckout(null, EstadoPreferencia.CRIANDO, null, null, null, agora));
        assertThatIllegalArgumentException().isThrownBy(() -> new PreferenciaCheckout(id, null, null, null, null, agora));
        assertThatIllegalArgumentException().isThrownBy(() -> new PreferenciaCheckout(id, EstadoPreferencia.CRIANDO, null, null, null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new PreferenciaCheckout(id, EstadoPreferencia.CRIADA, null, null, null, agora));
        assertThatIllegalArgumentException().isThrownBy(() -> new PreferenciaCheckout(id, EstadoPreferencia.CRIANDO, "p1", null, null, agora));
        assertThatIllegalArgumentException().isThrownBy(() -> new PreferenciaCheckout(id, EstadoPreferencia.CRIANDO, null, url, null, agora));
        assertThatIllegalArgumentException().isThrownBy(() -> new PreferenciaCheckout(id, EstadoPreferencia.CRIANDO, null, null, url, agora));
    }
}
