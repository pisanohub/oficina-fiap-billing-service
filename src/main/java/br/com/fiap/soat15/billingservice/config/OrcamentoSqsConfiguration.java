package br.com.fiap.soat15.billingservice.config;

import br.com.fiap.soat15.billingservice.adapter.in.sqs.OrcamentoSqsWorker;
import br.com.fiap.soat15.billingservice.application.port.out.RegistroMensagensOrcamento;
import br.com.fiap.soat15.billingservice.application.usecase.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.regions.Region;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@ConditionalOnProperty(name = "billing.orcamento-sqs.enabled", havingValue = "true")
@EnableScheduling
public class OrcamentoSqsConfiguration {
    @Bean(destroyMethod = "close")
    SqsClient orcamentoSqsClient(@Value("${billing.orcamento-sqs.region:us-east-1}") String region) {
        return SqsClient.builder().region(Region.of(region))
                .overrideConfiguration(c -> c.apiCallTimeout(java.time.Duration.ofSeconds(20))).build();
    }
    @Bean
    ProcessarMensagemOrcamentoUseCase processarMensagemOrcamento(RegistroMensagensOrcamento registro, GerarOrcamentoUseCase gerar) {
        return new ProcessarMensagemOrcamentoUseCase(registro, gerar,
                JsonMapper.builder().disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).build());
    }
    @Bean
    OrcamentoSqsWorker orcamentoSqsWorker(SqsClient orcamentoSqsClient,
            @Value("${billing.orcamento-sqs.comandos-url}") String entrada,
            @Value("${billing.orcamento-sqs.resultados-url}") String saida,
            ProcessarMensagemOrcamentoUseCase processar, RegistroMensagensOrcamento registro) {
        if (entrada.isBlank() || saida.isBlank() || entrada.equals(saida)) {
            throw new IllegalArgumentException("Configure filas de orçamento diferentes.");
        }
        return new OrcamentoSqsWorker(orcamentoSqsClient, entrada, saida, processar, registro);
    }
}
