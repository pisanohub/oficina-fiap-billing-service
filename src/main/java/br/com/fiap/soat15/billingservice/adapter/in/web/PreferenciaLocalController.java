package br.com.fiap.soat15.billingservice.adapter.in.web;

import br.com.fiap.soat15.billingservice.application.exception.*;
import br.com.fiap.soat15.billingservice.application.port.out.PreferenciaCheckoutRepository;
import br.com.fiap.soat15.billingservice.application.usecase.CriarPreferenciaUseCase;
import br.com.fiap.soat15.billingservice.domain.model.PreferenciaCheckout;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.UUID;

/** Demonstração local do Checkout Pro. Ainda não é ProcessarPagamento da Saga. */
@Profile("local")
@RestController
@RequestMapping("/internal/orcamentos/{id}/preferencia")
public class PreferenciaLocalController {
    private final CriarPreferenciaUseCase criar;
    private final PreferenciaCheckoutRepository repository;

    public PreferenciaLocalController(CriarPreferenciaUseCase criar, PreferenciaCheckoutRepository repository) {
        this.criar = criar;
        this.repository = repository;
    }

    @PostMapping
    public PreferenciaCheckout criar(@PathVariable UUID id) { return criar.executar(id); }

    @GetMapping
    public ResponseEntity<PreferenciaCheckout> consultar(@PathVariable UUID id) {
        return ResponseEntity.of(repository.buscar(id));
    }

    @ExceptionHandler(PagamentoNaoConfiguradoException.class)
    ProblemDetail configuracao(PagamentoNaoConfiguradoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(PreferenciaBloqueadaException.class)
    ProblemDetail bloqueada(PreferenciaBloqueadaException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(IntegracaoPagamentoException.class)
    ProblemDetail integracao(IntegracaoPagamentoException ex) {
        var erro = ProblemDetail.forStatusAndDetail(ex.timeout() ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY, ex.getMessage());
        erro.setProperty("resultadoIncerto", ex.resultadoIncerto());
        return erro;
    }

    @ExceptionHandler(OrcamentoNaoEncontradoException.class)
    ProblemDetail inexistente(OrcamentoNaoEncontradoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail entrada(Exception ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Identificador inválido ou orçamento sem valor positivo.");
    }
}
