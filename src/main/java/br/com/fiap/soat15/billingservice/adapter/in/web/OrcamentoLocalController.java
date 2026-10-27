package br.com.fiap.soat15.billingservice.adapter.in.web;

import br.com.fiap.soat15.billingservice.application.exception.ComandoConflitanteException;
import br.com.fiap.soat15.billingservice.application.exception.OrcamentoNaoEncontradoException;
import br.com.fiap.soat15.billingservice.application.usecase.*;
import br.com.fiap.soat15.billingservice.domain.model.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Entrada temporária de desenvolvimento. Não é a API pública definitiva do Billing. */
@Profile("local")
@RestController
@RequestMapping("/internal/orcamentos")
public class OrcamentoLocalController {
    private final GerarOrcamentoUseCase gerar;
    private final ConsultarOrcamentoUseCase consultar;

    public OrcamentoLocalController(GerarOrcamentoUseCase gerar, ConsultarOrcamentoUseCase consultar) {
        this.gerar = gerar;
        this.consultar = consultar;
    }

    @PostMapping
    public OrcamentoResponse gerar(@Valid @RequestBody GerarRequest request) {
        var comando = new GerarOrcamentoCommand(request.comandoId(), request.ordemId(), request.clienteId(),
                request.itens().stream().map(item -> new ItemOrcamento(item.referencia(), item.tipo(),
                        item.descricao(), item.quantidade(), item.valorUnitario())).toList());
        return OrcamentoResponse.de(gerar.executar(comando));
    }

    @GetMapping("/{id}")
    public OrcamentoResponse consultar(@PathVariable UUID id) {
        return OrcamentoResponse.de(consultar.executar(id));
    }

    @ExceptionHandler(ComandoConflitanteException.class)
    ProblemDetail conflito(ComandoConflitanteException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(OrcamentoNaoEncontradoException.class)
    ProblemDetail naoEncontrado(OrcamentoNaoEncontradoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail regraInvalida(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    ProblemDetail entradaInvalida(Exception ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Entrada inválida. Confira identificadores, tipos, quantidades e valores.");
    }

    public record GerarRequest(@NotNull UUID comandoId, @NotNull @Positive Long ordemId,
            @NotNull @Positive Long clienteId, @NotEmpty @Size(max = 100) List<@NotNull @Valid ItemRequest> itens) { }

    public record ItemRequest(@NotBlank @Size(max = 80) String referencia, @NotNull TipoItem tipo,
            @NotBlank @Size(max = 255) String descricao, @NotNull @Positive Integer quantidade,
            @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal valorUnitario) { }

    public record OrcamentoResponse(UUID id, UUID comandoId, long ordemId, long clienteId,
            String status, String moeda, List<ItemOrcamento> itens, BigDecimal total, Instant criadoEm) {
        static OrcamentoResponse de(Orcamento orcamento) {
            return new OrcamentoResponse(orcamento.id(), orcamento.comandoId(), orcamento.ordemId(),
                    orcamento.clienteId(), orcamento.status(), "BRL", orcamento.itens(), orcamento.total(), orcamento.criadoEm());
        }
    }
}
