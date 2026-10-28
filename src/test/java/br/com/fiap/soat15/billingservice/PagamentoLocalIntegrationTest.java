package br.com.fiap.soat15.billingservice;

import br.com.fiap.soat15.billingservice.application.exception.*;
import br.com.fiap.soat15.billingservice.application.port.out.*;
import br.com.fiap.soat15.billingservice.application.usecase.*;
import br.com.fiap.soat15.billingservice.domain.exception.PagamentoIncompativelException;
import br.com.fiap.soat15.billingservice.domain.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("local")
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:pagamentos-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.open-in-view=false"})
@AutoConfigureMockMvc
class PagamentoLocalIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired GerarOrcamentoUseCase gerar;
    @Autowired ConsultarOrcamentoUseCase consultar;
    @Autowired VerificarPagamentoUseCase verificar;
    @Autowired PreferenciaCheckoutRepository preferencias;
    @Autowired PagamentoRepository repository;
    @MockitoBean PagamentoGateway gateway;
    private static final AtomicLong SEQUENCIA = new AtomicLong(900000);
    private final String pagamentoId = String.valueOf(SEQUENCIA.incrementAndGet());
    private final Instant base = Instant.parse("2026-10-28T12:00:00Z");

    @Test
    void confirmaConsultaERepeteSemDuplicarRegistro() throws Exception {
        var orcamento = orcamento(true);
        when(gateway.consultar(pagamentoId)).thenReturn(dados(orcamento,"approved","0",0));
        for (int i=0;i<2;i++) {
            mvc.perform(post(rota(orcamento)).contentType("application/json").content("{\"status\":\"rejected\",\"valor\":0.01}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("APROVADO"))
                    .andExpect(jsonPath("$.confirmado").value(true)).andExpect(jsonPath("$.valor").value(199.80));
        }
        mvc.perform(get(lista(orcamento))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].pagamentoId").value(pagamentoId));
        assertThat(repository.listarPorOrcamento(orcamento.id())).hasSize(1);
        assertThat(consultar.executar(orcamento.id()).status()).isEqualTo("AGUARDANDO_APROVACAO");
        verify(gateway,times(2)).consultar(pagamentoId); // Reconsulta para obter o estado atual.
    }

    @Test
    void atualizaPendenteParaAprovadoEReconheceEstornosSemRealizarReembolso() throws Exception {
        var orcamento = orcamento(true);
        when(gateway.consultar(pagamentoId)).thenReturn(dados(orcamento,"pending","0",0),
                dados(orcamento,"approved","0",1),dados(orcamento,"approved","10",2),dados(orcamento,"refunded","199.80",3));
        var estados = List.of("PENDENTE","APROVADO","PARCIALMENTE_ESTORNADO","ESTORNADO");
        for (var estado : estados) {
            mvc.perform(post(rota(orcamento))).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value(estado))
                    .andExpect(jsonPath("$.confirmado").value(estado.equals("APROVADO")));
        }
        assertThat(repository.listarPorOrcamento(orcamento.id())).hasSize(1);
    }

    @Test
    void respostaAntigaNaoRegrideESnapshotConflitanteNaMesmaDataDa409() throws Exception {
        var orcamento = orcamento(true);
        when(gateway.consultar(pagamentoId)).thenReturn(dados(orcamento,"approved","0",1),
                dados(orcamento,"pending","0",0), dados(orcamento,"pending","0",1));
        mvc.perform(post(rota(orcamento))).andExpect(status().isOk());
        mvc.perform(post(rota(orcamento))).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("APROVADO"));
        mvc.perform(post(rota(orcamento))).andExpect(status().isConflict());
        assertThat(repository.listarPorOrcamento(orcamento.id()).getFirst().confirmado()).isTrue();
    }

    @Test
    void rejeitaReferenciaMoedaOuValorDeOutroOrcamentoSemSalvar() throws Exception {
        var orcamento = orcamento(true);
        for (var dados : List.of(
                new PagamentoConsultado(pagamentoId,UUID.randomUUID().toString(),"approved",null,new BigDecimal("199.80"),"BRL",BigDecimal.ZERO,base),
                new PagamentoConsultado(pagamentoId,orcamento.id().toString(),"approved",null,new BigDecimal("1"),"BRL",BigDecimal.ZERO,base),
                new PagamentoConsultado(pagamentoId,orcamento.id().toString(),"approved",null,new BigDecimal("199.80"),"USD",BigDecimal.ZERO,base))) {
            when(gateway.consultar(pagamentoId)).thenReturn(dados);
            mvc.perform(post(rota(orcamento))).andExpect(status().isConflict());
            assertThat(repository.listarPorOrcamento(orcamento.id())).isEmpty();
        }
    }

    @Test
    void respostaComIdDiferenteEErroDoProvedor() throws Exception {
        var orcamento = orcamento(true);
        when(gateway.consultar(pagamentoId)).thenReturn(new PagamentoConsultado("9999999",orcamento.id().toString(),
                "approved",null,new BigDecimal("199.80"),"BRL",BigDecimal.ZERO,base));
        mvc.perform(post(rota(orcamento))).andExpect(status().isBadGateway());
        assertThat(repository.listarPorOrcamento(orcamento.id())).isEmpty();
    }

    @Test
    void naoHaConfirmacaoSemPreferenciaValidaOuIdentificadoresValidos() throws Exception {
        var semPreferencia = orcamento(false);
        mvc.perform(post(rota(semPreferencia))).andExpect(status().isConflict());
        mvc.perform(post("/internal/orcamentos/"+UUID.randomUUID()+"/pagamentos/123/verificar")).andExpect(status().isNotFound());
        mvc.perform(post("/internal/orcamentos/"+semPreferencia.id()+"/pagamentos/0/verificar")).andExpect(status().isBadRequest());
        mvc.perform(get("/internal/orcamentos/invalido/pagamentos")).andExpect(status().isBadRequest());
        mvc.perform(get("/internal/orcamentos/"+UUID.randomUUID()+"/pagamentos")).andExpect(status().isNotFound());
        mvc.perform(get(lista(semPreferencia))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        verifyNoInteractions(gateway);
    }

    @Test
    void errosDeConfiguracaoProvedorOuTimeoutNaoInventamEstadoFinanceiro() throws Exception {
        var orcamento = orcamento(true);
        var erros = List.of(new PagamentoNaoConfiguradoException(),new PagamentoNaoEncontradoException(),
                new IntegracaoPagamentoException("Falha de consulta.",false,false),new IntegracaoPagamentoException("Timeout.",false,true));
        var codigos = new int[]{503,404,502,504};
        for (int i=0;i<erros.size();i++) {
            doThrow(erros.get(i)).when(gateway).consultar(pagamentoId);
            mvc.perform(post(rota(orcamento))).andExpect(status().is(codigos[i]));
            assertThat(repository.listarPorOrcamento(orcamento.id())).isEmpty();
        }
    }

    @Test
    void consultasConcorrentesGravamUmaOperacao() throws Exception {
        var orcamento = orcamento(true);
        when(gateway.consultar(pagamentoId)).thenReturn(dados(orcamento,"approved","0",0));
        var iniciar = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<Future<Pagamento>> futuros = new ArrayList<>();
            for (int i=0;i<4;i++) futuros.add(executor.submit(() -> { iniciar.await(); return verificar.executar(orcamento.id(),pagamentoId); }));
            iniciar.countDown();
            for (var futuro : futuros) assertThat(futuro.get(30,TimeUnit.SECONDS).confirmado()).isTrue();
        }
        assertThat(repository.listarPorOrcamento(orcamento.id())).hasSize(1);
    }

    @Test
    void paymentIdNaoPodeTrocarDeOrcamentoMesmoSeProvedorRetornarReferenciaDiferente() {
        var primeiro = orcamento(true);
        var segundo = orcamento(true);
        when(gateway.consultar(pagamentoId)).thenReturn(dados(primeiro,"approved","0",0),dados(segundo,"approved","0",1));
        verificar.executar(primeiro.id(),pagamentoId);
        assertThatThrownBy(() -> verificar.executar(segundo.id(),pagamentoId)).isInstanceOf(PagamentoIncompativelException.class);
        assertThat(repository.listarPorOrcamento(segundo.id())).isEmpty();
        var inexistente = UUID.randomUUID();
        assertThatThrownBy(() -> repository.registrar(new Pagamento(inexistente,
                new PagamentoConsultado("888888",inexistente.toString(),"approved",null,BigDecimal.TEN,"BRL",BigDecimal.ZERO,base),base)))
                .isInstanceOf(OrcamentoNaoEncontradoException.class);
    }

    private Orcamento orcamento(boolean comPreferencia) {
        var orcamento = gerar.executar(new GerarOrcamentoCommand(UUID.randomUUID(),1,1,
                List.of(new ItemOrcamento("s1",TipoItem.SERVICO,"Revisão",1,new BigDecimal("199.80")))));
        if (comPreferencia) {
            preferencias.tentarIniciar(orcamento.id(),base);
            preferencias.concluir(orcamento.id(),new PreferenciaGerada("pref-"+orcamento.id(),"https://www.mercadopago.com.br/checkout",null),base);
        }
        return orcamento;
    }
    private PagamentoConsultado dados(Orcamento orcamento,String status,String estorno,long segundos) {
        return new PagamentoConsultado(pagamentoId,orcamento.id().toString(),status,"accredited",orcamento.total(),"BRL",new BigDecimal(estorno),base.plusSeconds(segundos));
    }
    private String rota(Orcamento o) { return lista(o)+"/"+pagamentoId+"/verificar"; }
    private String lista(Orcamento o) { return "/internal/orcamentos/"+o.id()+"/pagamentos"; }
}
