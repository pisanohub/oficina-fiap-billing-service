package br.com.fiap.soat15.billingservice;

import br.com.fiap.soat15.billingservice.application.port.out.OrcamentoRepository;
import br.com.fiap.soat15.billingservice.application.usecase.*;
import br.com.fiap.soat15.billingservice.adapter.out.persistence.SpringDataOrcamentoRepository;
import br.com.fiap.soat15.billingservice.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("local")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:billing-local-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class OrcamentoLocalIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired GerarOrcamentoUseCase gerar;
    @Autowired OrcamentoRepository repository;
    @Autowired SpringDataOrcamentoRepository jpa;

    @Test
    void criaConsultaERepeteComandoPelaEntradaLocal() throws Exception {
        UUID comandoId = UUID.randomUUID();
        var resposta = mvc.perform(post("/internal/orcamentos").contentType("application/json").content(json(comandoId, "49.90")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(199.80))
                .andExpect(jsonPath("$.moeda").value("BRL"))
                .andExpect(jsonPath("$.status").value("AGUARDANDO_APROVACAO")).andReturn();
        var salvo = repository.buscarPorComandoId(comandoId).orElseThrow();
        assertThat(salvo.itens()).hasSize(2);
        mvc.perform(get("/internal/orcamentos/{id}", salvo.id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(salvo.id().toString())).andExpect(jsonPath("$.total").value(199.80));
        mvc.perform(post("/internal/orcamentos").contentType("application/json").content(json(comandoId, "49.90")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(salvo.id().toString()));
        assertThat(resposta.getResponse().getContentAsString()).contains(comandoId.toString());
    }

    @Test
    void mesmaChaveComValorDiferenteRetorna409() throws Exception {
        var id = UUID.randomUUID();
        mvc.perform(post("/internal/orcamentos").contentType("application/json").content(json(id, "49.90"))).andExpect(status().isOk());
        mvc.perform(post("/internal/orcamentos").contentType("application/json").content(json(id, "59.90"))).andExpect(status().isConflict());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{", "{\"comandoId\":\"invalido\"}", "{\"comandoId\":null,\"ordemId\":1,\"clienteId\":1,\"itens\":[]}"})
    void rejeitaJsonInvalidoOuIncompleto(String corpo) throws Exception {
        mvc.perform(post("/internal/orcamentos").contentType("application/json").content(corpo)).andExpect(status().isBadRequest());
    }

    @Test
    void rejeitaPrecisaoNegativoQuantidadeETipoInvalidos() throws Exception {
        for (var preco : List.of("-1.00", "1.001")) {
            mvc.perform(post("/internal/orcamentos").contentType("application/json").content(json(UUID.randomUUID(), preco)))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/internal/orcamentos").contentType("application/json")
                .content(json(UUID.randomUUID(), "1.00").replace("\"quantidade\":2", "\"quantidade\":0"))).andExpect(status().isBadRequest());
        mvc.perform(post("/internal/orcamentos").contentType("application/json")
                .content(json(UUID.randomUUID(), "1.00").replace("PECA", "DESCONHECIDO"))).andExpect(status().isBadRequest());
    }

    @Test
    void erroDeDominioNaEntradaLocalRetorna400() throws Exception {
        var corpo = json(UUID.randomUUID(), "1.00").replace("\"referencia\":\"mao-obra\",\"tipo\":\"SERVICO\"", "\"referencia\":\"oleo\",\"tipo\":\"PECA\"");
        mvc.perform(post("/internal/orcamentos").contentType("application/json").content(corpo)).andExpect(status().isBadRequest());
    }

    @Test
    void consultaDistingueInexistenteDeIdMalformado() throws Exception {
        mvc.perform(get("/internal/orcamentos/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/internal/orcamentos/nao-e-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void concorrenciaDoMesmoComandoPersisteUmUnicoOrcamento() throws Exception {
        var id = UUID.randomUUID();
        var comando = new GerarOrcamentoCommand(id, 123, 456,
                List.of(new ItemOrcamento("s1", TipoItem.SERVICO, "Revisão", 1, BigDecimal.TEN)));
        var iniciar = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<Future<UUID>> resultados = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                resultados.add(executor.submit(() -> { iniciar.await(); return gerar.executar(comando).id(); }));
            }
            iniciar.countDown();
            Set<UUID> ids = new HashSet<>();
            for (var futuro : resultados) ids.add(futuro.get(30, TimeUnit.SECONDS));
            assertThat(ids).hasSize(1);
            assertThat(jpa.findAll().stream().map(entity -> entity.paraDominio()).filter(o -> o.comandoId().equals(id))).hasSize(1);
        }
    }

    @Test
    void swaggerEHealthFuncionamNoPerfilLocal() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.paths['/internal/orcamentos']").exists());
    }

    private String json(UUID comandoId, String preco) {
        return """
                {"comandoId":"%s","ordemId":1,"clienteId":1,"itens":[
                {"referencia":"oleo","tipo":"PECA","descricao":"Óleo","quantidade":2,"valorUnitario":%s},
                {"referencia":"mao-obra","tipo":"SERVICO","descricao":"Troca de óleo","quantidade":1,"valorUnitario":100.00}]}
                """.formatted(comandoId, preco);
    }
}
