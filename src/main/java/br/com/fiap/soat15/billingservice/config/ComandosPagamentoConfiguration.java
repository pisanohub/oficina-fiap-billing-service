package br.com.fiap.soat15.billingservice.config;

import br.com.fiap.soat15.billingservice.adapter.in.sqs.ComandosPagamentoWorker;
import br.com.fiap.soat15.billingservice.adapter.out.mercadopago.MercadoPagoEstornoAdapter;
import br.com.fiap.soat15.billingservice.adapter.out.persistence.RegistroFinanceiro;
import br.com.fiap.soat15.billingservice.application.port.out.*;
import br.com.fiap.soat15.billingservice.application.usecase.*;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.WebClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.regions.Region;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@ConditionalOnProperty(name = "billing.sqs.enabled", havingValue = "true")
@EnableScheduling
public class ComandosPagamentoConfiguration {
    @Bean(destroyMethod = "close")
    SqsClient financeiroSqs(@Value("${billing.sqs.region:us-east-1}") String region) {
        return SqsClient.builder().region(Region.of(region))
                .overrideConfiguration(c -> c.apiCallTimeout(java.time.Duration.ofSeconds(20))).build();
    }
    @Bean
    ProcessarComandoPagamentoUseCase comandosPagamento(RegistroFinanceiro registro, ConsultarOrcamentoUseCase consultar,
            CriarPreferenciaUseCase checkout, PagamentoGateway pagamentos, PagamentoRepository salvos,
            MercadoPagoProperties properties, Clock clock) {
        properties.validar();
        return new ProcessarComandoPagamentoUseCase(registro, consultar, checkout, pagamentos, salvos,
                new MercadoPagoEstornoAdapter(WebClient.builder().baseUrl("https://api.mercadopago.com").build(), properties),
                JsonMapper.builder().disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).build(), clock);
    }
    @Bean
    ComandosPagamentoWorker comandosPagamentoWorker(SqsClient financeiroSqs,
            @Value("${billing.sqs.comandos-url}") String entrada,
            @Value("${billing.sqs.resultados-url}") String saida,
            ProcessarComandoPagamentoUseCase comandos, RegistroFinanceiro registro) {
        if (entrada.isBlank() || saida.isBlank() || entrada.equals(saida)) {
            throw new IllegalArgumentException("Configure filas de comandos e resultados diferentes.");
        }
        return new ComandosPagamentoWorker(financeiroSqs, entrada, saida, comandos, registro);
    }
}
