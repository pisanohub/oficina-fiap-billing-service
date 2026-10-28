package br.com.fiap.soat15.billingservice.config;
import br.com.fiap.soat15.billingservice.adapter.out.mercadopago.MercadoPagoPagamentoAdapter;
import br.com.fiap.soat15.billingservice.application.port.out.*;
import br.com.fiap.soat15.billingservice.application.usecase.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.Clock;
@Configuration
public class PagamentoConfiguration {
    @Bean
    PagamentoGateway pagamentoGateway(MercadoPagoProperties properties) {
        return new MercadoPagoPagamentoAdapter(WebClient.builder().baseUrl("https://api.mercadopago.com").build(), properties);
    }
    @Bean
    VerificarPagamentoUseCase verificarPagamentoUseCase(ConsultarOrcamentoUseCase consultar,
            PreferenciaCheckoutRepository preferencias, PagamentoGateway gateway, PagamentoRepository repository, Clock clock) {
        return new VerificarPagamentoUseCase(consultar, preferencias, gateway, repository, clock);
    }
}
