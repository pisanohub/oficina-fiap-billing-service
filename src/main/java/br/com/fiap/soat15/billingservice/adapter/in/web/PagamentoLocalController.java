package br.com.fiap.soat15.billingservice.adapter.in.web;
import br.com.fiap.soat15.billingservice.application.exception.*;
import br.com.fiap.soat15.billingservice.application.port.out.PagamentoRepository;
import br.com.fiap.soat15.billingservice.application.usecase.*;
import br.com.fiap.soat15.billingservice.domain.exception.PagamentoIncompativelException;
import br.com.fiap.soat15.billingservice.domain.model.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Profile("local")
@RestController
@RequestMapping("/internal/orcamentos/{id}/pagamentos")
public class PagamentoLocalController {
    private final VerificarPagamentoUseCase verificar;
    private final ConsultarOrcamentoUseCase consultar;
    private final PagamentoRepository repository;
    public PagamentoLocalController(VerificarPagamentoUseCase verificar, ConsultarOrcamentoUseCase consultar, PagamentoRepository repository) {
        this.verificar = verificar; this.consultar = consultar; this.repository = repository;
    }
    @PostMapping("/{pagamentoId}/verificar")
    public PagamentoResponse verificar(@PathVariable UUID id, @PathVariable String pagamentoId) {
        return PagamentoResponse.de(verificar.executar(id, pagamentoId));
    }
    @GetMapping
    public List<PagamentoResponse> listar(@PathVariable UUID id) {
        consultar.executar(id);
        return repository.listarPorOrcamento(id).stream().map(PagamentoResponse::de).toList();
    }
    @ExceptionHandler({OrcamentoNaoEncontradoException.class, PagamentoNaoEncontradoException.class})
    ProblemDetail inexistente(RuntimeException ex) { return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage()); }
    @ExceptionHandler(PagamentoIncompativelException.class)
    ProblemDetail incompatibilidade(PagamentoIncompativelException ex) { return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage()); }
    @ExceptionHandler(PagamentoNaoConfiguradoException.class)
    ProblemDetail configuracao(PagamentoNaoConfiguradoException ex) { return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage()); }
    @ExceptionHandler(IntegracaoPagamentoException.class)
    ProblemDetail integracao(IntegracaoPagamentoException ex) {
        return ProblemDetail.forStatusAndDetail(ex.timeout() ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY, ex.getMessage());
    }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail entrada(Exception ex) { return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Informe UUID do orçamento e ID numérico de pagamento válidos."); }
    public record PagamentoResponse(UUID orcamentoId, String pagamentoId, EstadoPagamento estado, boolean confirmado,
            String statusProvedor, String statusDetalhe, BigDecimal valor, String moeda, BigDecimal valorEstornado,
            Instant atualizadoNoProvedor, Instant consultadoEm) {
        static PagamentoResponse de(Pagamento pagamento) {
            var dados = pagamento.dados();
            return new PagamentoResponse(pagamento.orcamentoId(), dados.id(), dados.estado(), pagamento.confirmado(),
                    dados.status(), dados.statusDetalhe(), dados.valor(), dados.moeda(), dados.valorEstornado(),
                    dados.atualizadoNoProvedor(), pagamento.consultadoEm());
        }
    }
}
