package br.com.fiap.soat15.billingservice.adapter.in.web;

import br.com.fiap.soat15.billingservice.application.exception.*;
import br.com.fiap.soat15.billingservice.application.usecase.*;
import br.com.fiap.soat15.billingservice.domain.model.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Profile("local")
@RestController
@RequestMapping("/internal/orcamentos/cotados")
public class OrcamentoCotadoLocalController {
    private final GerarOrcamentoCotadoUseCase gerar;
    public OrcamentoCotadoLocalController(GerarOrcamentoCotadoUseCase gerar) { this.gerar = gerar; }
    @PostMapping
    public Response gerar(@Valid @RequestBody Request request) {
        var cotado = gerar.executar(new GerarOrcamentoCotadoCommand(request.comandoId(), request.ordemId(), request.clienteId(),
                request.versaoDiagnostico(), request.itens().stream().map(i -> new ItemCotacao(i.tipo(), i.itemId(), i.quantidade().intValueExact())).toList()));
        var o = cotado.orcamento();
        return new Response(o.id(), o.comandoId(), o.ordemId(), o.clienteId(), cotado.versaoDiagnostico(),
                cotado.cotacaoGeradaEm(), o.status(), "BRL", o.itens(), o.total(), o.criadoEm());
    }
    @ExceptionHandler(ComandoConflitanteException.class)
    ProblemDetail conflito(ComandoConflitanteException ex) { return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage()); }
    @ExceptionHandler(CotacaoNaoConfiguradaException.class)
    ProblemDetail configuracao(CotacaoNaoConfiguradaException ex) { return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage()); }
    @ExceptionHandler(CotacaoRecusadaException.class)
    ProblemDetail recusada(CotacaoRecusadaException ex) { return ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(422), ex.getMessage()); }
    @ExceptionHandler(IntegracaoCotacaoException.class)
    ProblemDetail integracao(IntegracaoCotacaoException ex) {
        return ProblemDetail.forStatusAndDetail(ex.timeout() ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY, ex.getMessage());
    }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ProblemDetail entrada(Exception ex) { return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Confira comando, OS, cliente, versão e itens da cotação. Não envie preços nesta rota."); }
    public record Request(@NotNull UUID comandoId, @NotNull @Positive Long ordemId, @NotNull @Positive Long clienteId,
            @NotNull @Positive Integer versaoDiagnostico, @NotEmpty @Size(max = 20) List<@NotNull @Valid ItemRequest> itens) { }
    public record ItemRequest(@NotNull TipoItem tipo, @NotBlank @Size(max = 80) String itemId,
            @NotNull @Positive @Digits(integer = 10, fraction = 0) @DecimalMax("2147483647") BigDecimal quantidade) { }
    public record Response(UUID id, UUID comandoId, long ordemId, long clienteId, int versaoDiagnostico,
            Instant cotacaoGeradaEm, String status, String moeda, List<ItemOrcamento> itens, BigDecimal total, Instant criadoEm) { }
}
