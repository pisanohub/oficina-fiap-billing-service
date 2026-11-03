package br.com.fiap.soat15.billingservice.config;

import br.com.fiap.soat15.billingservice.adapter.out.execucao.ExecucaoCotacaoAdapter;
import br.com.fiap.soat15.billingservice.application.port.out.*;
import br.com.fiap.soat15.billingservice.application.usecase.GerarOrcamentoCotadoUseCase;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.Clock;

@Configuration
@EnableConfigurationProperties(ExecucaoCotacaoProperties.class)
public class CotacaoConfiguration {
    @Bean CotacaoGateway cotacaoGateway(ExecucaoCotacaoProperties properties) {
        return new ExecucaoCotacaoAdapter(WebClient.builder().codecs(c -> c.defaultCodecs().maxInMemorySize(65536)).build(), properties);
    }
    @Bean GerarOrcamentoCotadoUseCase gerarOrcamentoCotadoUseCase(OrcamentoCotadoRepository repository, CotacaoGateway gateway, Clock clock) {
        return new GerarOrcamentoCotadoUseCase(repository, gateway, clock);
    }
}
