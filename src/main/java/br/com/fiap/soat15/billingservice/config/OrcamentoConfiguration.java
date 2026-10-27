package br.com.fiap.soat15.billingservice.config;

import br.com.fiap.soat15.billingservice.application.port.out.OrcamentoRepository;
import br.com.fiap.soat15.billingservice.application.usecase.ConsultarOrcamentoUseCase;
import br.com.fiap.soat15.billingservice.application.usecase.GerarOrcamentoUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration
public class OrcamentoConfiguration {
    @Bean
    Clock clock() { return Clock.systemUTC(); }

    @Bean
    GerarOrcamentoUseCase gerarOrcamentoUseCase(OrcamentoRepository repository, Clock clock) {
        return new GerarOrcamentoUseCase(repository, clock);
    }

    @Bean
    ConsultarOrcamentoUseCase consultarOrcamentoUseCase(OrcamentoRepository repository) {
        return new ConsultarOrcamentoUseCase(repository);
    }
}
