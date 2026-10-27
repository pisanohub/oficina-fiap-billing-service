package br.com.fiap.soat15.billingservice.config;

import br.com.fiap.soat15.billingservice.adapter.out.mercadopago.MercadoPagoPreferenciaAdapter;
import br.com.fiap.soat15.billingservice.application.port.out.*;
import br.com.fiap.soat15.billingservice.application.usecase.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.Clock;

@Configuration
@EnableConfigurationProperties(MercadoPagoProperties.class)
public class PreferenciaConfiguration {
    @Bean
    PreferenciaPagamentoGateway preferenciaPagamentoGateway(MercadoPagoProperties properties) {
        return new MercadoPagoPreferenciaAdapter(WebClient.builder().baseUrl("https://api.mercadopago.com").build(), properties);
    }

    @Bean
    CriarPreferenciaUseCase criarPreferenciaUseCase(ConsultarOrcamentoUseCase consultar, PreferenciaCheckoutRepository repository,
            PreferenciaPagamentoGateway gateway, Clock clock) {
        return new CriarPreferenciaUseCase(consultar, repository, gateway, clock);
    }
}
